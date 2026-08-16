package com.tutorbase.system;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
final class SystemStatusController {

    private static final String SERVICE_NAME = "tutor-base-backend";

    private final Clock clock;
    private final String version;

    SystemStatusController(Clock clock, Optional<BuildProperties> buildProperties) {
        this.clock = clock;
        this.version = buildProperties.map(BuildProperties::getVersion).orElse("dev");
    }

    @GetMapping("/status")
    SystemStatusResponse status() {
        return new SystemStatusResponse("ok", SERVICE_NAME, version, Instant.now(clock));
    }

    record SystemStatusResponse(String status, String service, String version, Instant time) {
    }
}
