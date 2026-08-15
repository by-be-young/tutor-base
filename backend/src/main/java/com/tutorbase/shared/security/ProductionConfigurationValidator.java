package com.tutorbase.shared.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import com.tutorbase.identity.IdentityProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
final class ProductionConfigurationValidator implements SmartInitializingSingleton {
    private final WebClientProperties web;
    private final IdentityProperties identity;

    ProductionConfigurationValidator(WebClientProperties web, IdentityProperties identity) {
        this.web = web;
        this.identity = identity;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate(web, identity);
    }

    static void validate(WebClientProperties web, IdentityProperties identity) {
        if (web.allowedOrigins().isEmpty() || web.allowedOrigins().stream().anyMatch(origin -> !isHttpsOrigin(origin))) {
            throw new IllegalStateException(
                    "Production requires at least one exact HTTPS origin without a path, query, or fragment");
        }
        if (identity.csrfSecret() == null
                || identity.csrfSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Production CSRF secret must contain at least 32 bytes");
        }
        if (!identity.cookieSecure()) {
            throw new IllegalStateException("Production session cookies must be Secure");
        }
        if (identity.bootstrap() != null && identity.bootstrap().enabled()) {
            throw new IllegalStateException("Administrator bootstrap must be disabled in production");
        }
    }

    private static boolean isHttpsOrigin(String value) {
        try {
            URI origin = URI.create(value);
            return "https".equalsIgnoreCase(origin.getScheme())
                    && origin.getHost() != null
                    && origin.getUserInfo() == null
                    && (origin.getPath() == null || origin.getPath().isEmpty())
                    && origin.getQuery() == null
                    && origin.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
