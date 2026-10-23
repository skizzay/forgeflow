package io.forgeflow.controlplane;

import java.io.IOException;
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

/** Only the packaged, side-effect-free bootstrap workflow is registered in this story. */
@Configuration(proxyBeanMethods = false)
class WorkflowRegistry {
    @Bean
    ApplicationRunner registerBootstrap(ProcessEngine engine, JdbcTemplate jdbc,
                                        PlatformTransactionManager transactions, DatabaseSchemas schemas) {
        return args -> register(engine, jdbc, transactions, schemas);
    }

    static void register(ProcessEngine engine, JdbcTemplate jdbc, PlatformTransactionManager transactions, DatabaseSchemas schemas)
            throws IOException, NoSuchAlgorithmException {
        byte[] resource = new ClassPathResource("workflows/bootstrap-v1.bpmn20.xml").getContentAsByteArray();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            // Serialize startup registrations across control-plane replicas in the same database.
            jdbc.execute("select pg_advisory_xact_lock(70607001)");
            var rows = jdbc.queryForList("select content_sha256, definition_id from " + schemas.domainSchema() + ".workflow_definition "
                    + "where workflow_key='bootstrap' and registry_version=1");
            if (!rows.isEmpty()) {
                var existing = rows.getFirst();
                if (!digest.equals(existing.get("content_sha256"))
                        || engine.getRepositoryService().createProcessDefinitionQuery()
                        .processDefinitionId((String) existing.get("definition_id")).count() != 1) {
                    throw new IllegalStateException("Bootstrap registry incompatible with deployed definition; publish a new version");
                }
                return;
            }
            var deployment = engine.getRepositoryService().createDeployment().name("forgeflow-bootstrap-v1")
                    .addBytes("bootstrap-v1.bpmn20.xml", resource).deploy();
            var definition = engine.getRepositoryService().createProcessDefinitionQuery()
                    .deploymentId(deployment.getId()).singleResult();
            jdbc.update("insert into " + schemas.domainSchema() + ".workflow_definition "
                    + "(workflow_key, registry_version, content_sha256, definition_id) values ('bootstrap',1,?,?)",
                    digest, definition.getId());
        });
    }
}
