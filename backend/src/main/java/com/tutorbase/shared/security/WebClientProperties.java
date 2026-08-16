package com.tutorbase.shared.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tutor.web")
public record WebClientProperties(List<String> allowedOrigins) {

    public WebClientProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .distinct()
                        .toList();
    }
}
