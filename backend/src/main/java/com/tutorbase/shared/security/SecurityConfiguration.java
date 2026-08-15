package com.tutorbase.shared.security;

import java.io.IOException;
import java.util.List;

import com.tutorbase.shared.http.ApiProblem;
import com.tutorbase.identity.IdentityProperties;
import com.tutorbase.identity.SessionAuthenticationFilter;
import com.tutorbase.identity.SessionCsrfFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({WebClientProperties.class, IdentityProperties.class})
public class SecurityConfiguration {

    private static final long CORS_MAX_AGE_SECONDS = 3_600;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            SessionAuthenticationFilter sessionAuthenticationFilter,
            SessionCsrfFilter sessionCsrfFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(sessionCsrfFilter, SessionAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/system/status",
                                "/api/v1/csrf",
                                "/api/v1/sessions",
                                "/api/v1/account-activations/complete",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMINISTRATOR")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "unauthenticated",
                                "Authentication required",
                                "A valid session is required."))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                objectMapper,
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "forbidden",
                                "Access denied",
                                "The current account cannot access this resource.")))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65_536, 3);
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(WebClientProperties webClientProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(webClientProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Content-Type",
                "X-CSRF-TOKEN",
                "Idempotency-Key",
                "If-Match"));
        configuration.setExposedHeaders(List.of("ETag", "Location", "Retry-After", "X-Trace-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(CORS_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void writeProblem(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String title,
            String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiProblem.create(status, code, title, detail, request));
    }
}
