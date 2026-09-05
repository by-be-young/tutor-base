package com.tutorbase.shared.migration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Closes the non-web application after Flyway completes in the release-job container. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "tutor.migration-only", havingValue = "true")
class MigrationOnlyConfiguration {

    @Bean
    ApplicationRunner closeAfterMigration(ConfigurableApplicationContext context) {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments arguments) {
                context.close();
            }
        };
    }
}
