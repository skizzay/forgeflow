package io.forgeflow.controlplane;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flowable.engine.ProcessEngine;
import org.flywaydb.core.Flyway;
import io.forgeflow.controlplane.webhook.application.port.in.ProcessWebhook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL is required: no H2 fallback and no skipped container-dependent assertions. */
class BootstrapIntegrationTest {
    private String adminUrl;
    private String url;
    private String database;
    private String runtimeRole;
    private String login;
    private String loginPassword;
    private final String user = System.getenv("FORGEFLOW_DB_USER");
    private final String password = System.getenv("FORGEFLOW_DB_PASSWORD");

    @BeforeEach
    void createDatabase() throws Exception {
        adminUrl = System.getenv("FORGEFLOW_DB_URL");
        login = user;
        loginPassword = password;
        assertThat(adminUrl).as("Run scripts/verify-bootstrap.sh or supply a disposable PostgreSQL admin database").isNotBlank();
        database = "bootstrap_" + UUID.randomUUID().toString().replace("-", "");
        url = adminUrl.substring(0, adminUrl.lastIndexOf('/') + 1) + database + "?currentSchema=forgeflow";
        try (var connection = DriverManager.getConnection(adminUrl, user, password);
             var statement = connection.createStatement()) {
            statement.execute("create database " + database);
        }
    }

    @AfterEach
    void dropDatabase() throws Exception {
        if (database == null) return;
        try (var connection = DriverManager.getConnection(adminUrl, user, password);
             var statement = connection.createStatement()) {
            statement.execute("drop database if exists " + database + " with (force)");
            if (runtimeRole != null) statement.execute("drop role if exists " + runtimeRole);
        }
    }

    private ConfigurableApplicationContext start(boolean migrate, String... extra) {
        if (migrate) {
            DatabaseMigrator.migrate(new DriverManagerDataSource(url, login, loginPassword),
                    new DatabaseSchemas("forgeflow", "flowable"));
        }
        return new SpringApplicationBuilder(ControlPlaneApplication.class).web(WebApplicationType.NONE)
                .run(arguments(extra));
    }

    private String[] arguments(String... extra) {
        var arguments = new ArrayList<>(List.of(
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + login,
                "--spring.datasource.password=" + loginPassword,
                "--logging.level.root=ERROR"));
        arguments.addAll(List.of(extra));
        return arguments.toArray(String[]::new);
    }

    @Test
    void emptyDatabaseMigratesAndRestartsWithoutRedeployingOrLosingData() {
        String definition;
        try (var context = start(true)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success and version is not null", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='forgeflow' and table_name <> 'flyway_schema_history'", Integer.class)).isEqualTo(11);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='flowable'", Integer.class)).isPositive();
            jdbc.update("insert into repository(id,github_id,full_name) values (?,1,'public/fixture')", UUID.randomUUID());
            definition = jdbc.queryForObject("select definition_id from workflow_definition", String.class);
        }
        try (var ignored = start(true)) { /* Repeated migrations are a no-op. */ }
        try (var context = start(false)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select full_name from repository", String.class)).isEqualTo("public/fixture");
            assertThat(jdbc.queryForObject("select definition_id from workflow_definition", String.class)).isEqualTo(definition);
            assertThat(context.getBean(ProcessEngine.class).getRepositoryService().createDeploymentQuery().count()).isEqualTo(1);
        }
    }

    @Test
    void waitingRunKeepsDefinitionAndApprovalIdentityAcrossRestartAndNewDeployment() {
        String instance;
        String definition;
        UUID run = UUID.randomUUID();
        try (var context = start(true)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var engine = context.getBean(ProcessEngine.class);
            UUID repository = UUID.randomUUID(), epic = UUID.randomUUID(), story = UUID.randomUUID();
            jdbc.update("insert into repository(id,github_id,full_name) values (?,1,'public/fixture')", repository);
            jdbc.update("insert into epic(id,repository_id,issue_number) values (?,?,1)", epic, repository);
            jdbc.update("insert into story(id,epic_id,repository_id,issue_number) values (?,?,?,7)", story, epic, repository);
            definition = jdbc.queryForObject("select definition_id from workflow_definition", String.class);
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            String pinnedDefinition = definition;
            instance = transaction.execute(status -> {
                var process = engine.getRuntimeService().startProcessInstanceById(pinnedDefinition,
                        Map.of("planRevision", "fixture-plan-v1", "configurationDigest", "a".repeat(64)));
                jdbc.update("insert into workflow_run(id,story_id,workflow_key,workflow_version,process_definition_id,process_instance_id,plan_revision,configuration_sha256,approval_id,approved_by,approved_at) "
                        + "values (?,?,'bootstrap',1,?,?,?, ?,?,'test-fixture-not-human-approval',now())",
                        run, story, pinnedDefinition, process.getId(), "fixture-plan-v1", "a".repeat(64), UUID.randomUUID());
                return process.getId();
            });
            // Simulate deployment v2. It must not migrate an already-running v1 instance.
            String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="test">
                  <process id="bootstrap" isExecutable="true"><startEvent id="start"/>
                    <sequenceFlow id="f" sourceRef="start" targetRef="waitV2"/>
                    <receiveTask id="waitV2"/>
                  </process>
                </definitions>
                """;
            engine.getRepositoryService().createDeployment().addString("fixture-v2.bpmn20.xml", xml).deploy();
        }
        try (var context = start(false)) {
            var engine = context.getBean(ProcessEngine.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            var process = engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(instance).singleResult();
            assertThat(process.getProcessDefinitionId()).isEqualTo(definition);
            assertThat(engine.getRuntimeService().getActiveActivityIds(instance)).containsExactly("awaitImplementation");
            assertThat(engine.getRuntimeService().getVariable(instance, "planRevision")).isEqualTo("fixture-plan-v1");
            var row = jdbc.queryForMap("select * from workflow_run where id=?", run);
            assertThat(row).containsEntry("process_definition_id", definition)
                    .containsEntry("plan_revision", "fixture-plan-v1")
                    .containsEntry("configuration_sha256", "a".repeat(64))
                    .containsEntry("approved_by", "test-fixture-not-human-approval");
            assertThat(jdbc.queryForObject("select state from story", String.class)).isEqualTo("DRAFT");
        }
    }

    @Test
    void engineAndDomainWritesRollbackTogether() {
        try (var context = start(true)) {
            var engine = context.getBean(ProcessEngine.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
            assertThat(context.getBean(PlatformTransactionManager.class)).isInstanceOf(JdbcTransactionManager.class);
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            String definition = jdbc.queryForObject("select definition_id from workflow_definition", String.class);
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                engine.getRuntimeService().startProcessInstanceById(definition);
                jdbc.update("insert into repository(id,github_id,full_name) values (?,1,'public/rollback')", UUID.randomUUID());
                throw new IllegalStateException("simulated domain rejection");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(engine.getRuntimeService().createProcessInstanceQuery().count()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from repository", Integer.class)).isZero();
        }
    }

    @Test
    void runtimeRefusesUninitializedDatabaseInsteadOfMigratingIt() {
        assertThatThrownBy(() -> start(false)).hasStackTraceContaining("flyway");
    }

    @Test
    void runtimeRefusesChangedMigrationChecksum() {
        try (var context = start(true)) {
            context.getBean(JdbcTemplate.class).update("update flyway_schema_history set checksum=0 where version='1'");
        }
        assertThatThrownBy(() -> start(false)).hasStackTraceContaining("checksum mismatch");
    }

    @Test
    void changedRegisteredContentFailsStartupRatherThanMutatingDefinition() {
        try (var context = start(true)) {
            context.getBean(JdbcTemplate.class).update("update workflow_definition set content_sha256=?", "0".repeat(64));
        }
        assertThatThrownBy(() -> start(false)).hasStackTraceContaining("Bootstrap registry incompatible");
    }

    @Test
    void runtimeStartsWithDmlOnlyRoleAndCannotPerformDdl() throws Exception {
        runtimeRole = "runtime_" + UUID.randomUUID().toString().replace("-", "");
        String temporaryPassword = UUID.randomUUID().toString();
        try (var context = start(true)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("create role " + runtimeRole + " login password '" + temporaryPassword + "'");
            jdbc.execute("grant connect on database " + database + " to " + runtimeRole);
            jdbc.execute("grant usage on schema forgeflow, flowable to " + runtimeRole);
            for (String schema : List.of("forgeflow", "flowable")) {
                jdbc.execute("grant select, insert, update, delete on all tables in schema " + schema + " to " + runtimeRole);
                jdbc.execute("grant usage, select on all sequences in schema " + schema + " to " + runtimeRole);
            }
        }
        login = runtimeRole;
        loginPassword = temporaryPassword;
        try (var context = start(false)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("insert into repository(id,github_id,full_name) values (?,1,'public/dml')", UUID.randomUUID());
            assertThatThrownBy(() -> jdbc.execute("create table forgeflow.unwanted(id int)"))
                    .hasStackTraceContaining("permission denied");
            assertThatThrownBy(() -> jdbc.execute("create table flowable.unwanted(id int)"))
                    .hasStackTraceContaining("permission denied");
            assertThat(context.getBean(ProcessEngine.class).getRepositoryService().createDeploymentQuery().count()).isEqualTo(1);
        }
    }

    @Test
    void configuredSchemasMigrateAndRestartWithoutCreatingDefaultSchemas() throws Exception {
        url = url.replace("currentSchema=forgeflow", "currentSchema=ff_domain");
        var schemas = DatabaseSchemas.fromJdbcUrl(url, "ff_engine");
        DatabaseMigrator.migrate(new DriverManagerDataSource(url, login, loginPassword), schemas);
        for (int restart = 0; restart < 2; restart++) {
            try (var context = start(false, "--forgeflow.database.engine-schema=ff_engine")) {
                var jdbc = context.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("select count(*) from workflow_definition", Integer.class)).isEqualTo(1);
                try (var first = context.getBean(DataSource.class).getConnection();
                     var second = context.getBean(DataSource.class).getConnection()) {
                    assertThat(first.getSchema()).isEqualTo("ff_domain");
                    assertThat(second.getSchema()).isEqualTo("ff_domain");
                }
                assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='ff_engine'", Integer.class)).isPositive();
                assertThat(jdbc.queryForObject("select count(*) from information_schema.schemata where schema_name in ('forgeflow','flowable')", Integer.class)).isZero();
            }
        }
    }

    @Test
    void versionOneInboxRowsSurviveUpgradeWithoutBecomingVerifiedEvents() {
        var source = new DriverManagerDataSource(url, login, loginPassword);
        var schemas = DatabaseSchemas.fromJdbcUrl(url, "flowable");
        Flyway.configure().configuration(schemas.flyway(source).getConfiguration()).target("1").load().migrate();
        var legacyJdbc = new JdbcTemplate(source);
        UUID delivery = UUID.randomUUID();
        legacyJdbc.update("insert into webhook_inbox(delivery_id,event_type) values (?,'ping')", delivery);
        DatabaseMigrator.migrate(source, schemas);
        try (var runtime = start(false, "--forgeflow.github.webhook.enabled=true",
                "--forgeflow.github.webhook.secret=" + UUID.randomUUID(),
                "--forgeflow.github.webhook.processing-enabled=false")) {
            assertThat(runtime.getBean(ProcessWebhook.class).processOne()).isFalse();
            var row = runtime.getBean(JdbcTemplate.class).queryForMap("select * from webhook_inbox where delivery_id=?", delivery);
            assertThat(row).containsEntry("state", "RECEIVED").containsEntry("payload_sha256", null);
            assertThat(runtime.getBean(JdbcTemplate.class).queryForObject("select count(*) from webhook_event", Integer.class)).isZero();
        }
    }

    @Test
    void httpExposesOnlyMinimalHealth() throws Exception {
        try (var ignored = start(true)) { }
        try (var context = new SpringApplicationBuilder(ControlPlaneApplication.class)
                .web(WebApplicationType.REACTIVE).run(arguments("--server.port=0"))) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var client = HttpClient.newHttpClient();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
            for (String path : List.of("/actuator/env", "/jobs", "/process-api/repository/deployments")) {
                response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isIn(401, 403);
            }
        }
    }
}
