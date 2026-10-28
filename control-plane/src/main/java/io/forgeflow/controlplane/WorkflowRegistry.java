package io.forgeflow.controlplane;

import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.flowable.engine.ProcessEngine;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Registers only packaged, content-verified workflows; approved execution is opt-in. */
@Configuration(proxyBeanMethods = false)
class WorkflowRegistry {
    @Bean
    ApplicationRunner registerBootstrap(ProcessEngine engine, JdbcTemplate jdbc,
                                        PlatformTransactionManager transactions) {
        return args -> register(engine, jdbc, transactions);
    }

    static void register(ProcessEngine engine, JdbcTemplate jdbc, PlatformTransactionManager transactions)
            throws IOException, NoSuchAlgorithmException {
        register(engine, jdbc, transactions, "bootstrap", "bootstrap-v1.bpmn20.xml");
    }
    @Bean
    @ConditionalOnProperty(prefix = "forgeflow.execution", name = "enabled", havingValue = "true")
    ApplicationRunner registerApprovedStory(ProcessEngine engine, JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return args -> register(engine, jdbc, transactions, "approved-story", "approved-story-v1.bpmn20.xml");
    }
    private static void register(ProcessEngine engine, JdbcTemplate jdbc, PlatformTransactionManager transactions,
                                 String key, String file) throws IOException, NoSuchAlgorithmException {
        byte[] resource = new ClassPathResource("workflows/" + file).getContentAsByteArray();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            // Serialize startup registrations across control-plane replicas in the same database.
            jdbc.execute("select pg_advisory_xact_lock(70607001)");
            var rows = jdbc.queryForList("select content_sha256, definition_id from workflow_definition "
                    + "where workflow_key=? and registry_version=1", key);
            if ("approved-story".equals(key)) {
                jdbc.update("insert into activity_definition(activity_key,registry_version,input_contract_version,output_contract_version) "
                        + "values (?,1,1,1) on conflict do nothing", "develop-story");
                if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from activity_definition where activity_key=? "
                        + "and registry_version=1 and input_contract_version=1 and output_contract_version=1)", Boolean.class, "develop-story"))) {
                    throw new IllegalStateException("Approved activity registry incompatible");
                }
            }
            if (!rows.isEmpty()) {
                var existing = rows.getFirst();
                if (!digest.equals(existing.get("content_sha256"))
                        || engine.getRepositoryService().createProcessDefinitionQuery()
                        .processDefinitionId((String) existing.get("definition_id")).count() != 1) {
                    throw new IllegalStateException("Workflow registry incompatible with deployed definition; publish a new version");
                }
                return;
            }
            var deployment = engine.getRepositoryService().createDeployment().name("forgeflow-" + key + "-v1")
                    .addBytes(file, resource).deploy();
            var definition = engine.getRepositoryService().createProcessDefinitionQuery()
                    .deploymentId(deployment.getId()).singleResult();
            jdbc.update("insert into workflow_definition "
                    + "(workflow_key, registry_version, content_sha256, definition_id) values (?,1,?,?)",
                    key, digest, definition.getId());
        });
    }
}
