package io.forgeflow.controlplane;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ConnectionSchemaConfiguration {
    @Bean
    static BeanPostProcessor connectionSchemaDefaults() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (bean instanceof HikariDataSource pool) {
                    // Flowable temporarily switches schema. Hikari needs a known default to
                    // reset it on return; the URI remains the sole domain-schema setting.
                    pool.setSchema(DatabaseSchemas.domainSchemaFromJdbcUrl(pool.getJdbcUrl()));
                }
                return bean;
            }
        };
    }
}
