package com.tutorbase.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin")
final class BootstrapAdministratorProvisioner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdministratorProvisioner.class);
    private final IdentityService identity;
    private final IdentityProperties properties;
    private final ConfigurableApplicationContext context;

    BootstrapAdministratorProvisioner(
            IdentityService identity,
            IdentityProperties properties,
            ConfigurableApplicationContext context) {
        this.identity = identity;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        IdentityProperties.Bootstrap bootstrap = properties.bootstrap();
        if (bootstrap.password() == null || bootstrap.password().length() < 12) {
            throw new IllegalStateException("Bootstrap password must contain at least 12 characters");
        }
        identity.bootstrapAdministrator(bootstrap.username(), bootstrap.password());
        LOGGER.info("One-time administrator provisioning completed; disable and clear bootstrap configuration now");
        context.close();
    }
}
