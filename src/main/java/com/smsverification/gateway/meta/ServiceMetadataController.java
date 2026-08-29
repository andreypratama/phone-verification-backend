package com.smsverification.gateway.meta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

@RestController
@PropertySource("classpath:service.properties")
public class ServiceMetadataController {

    private final String serviceName;
    private final String serviceVersion;
    private final Clock clock;

    public ServiceMetadataController(
            @Value("${spring.application.name}") String serviceName,
            @Value("${service.version}") String serviceVersion,
            Clock clock
    ) {
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.clock = clock;
    }

    @GetMapping("/health")
    public ServiceStatusResponse health() {
        return new ServiceStatusResponse("UP", serviceName, Instant.now(clock));
    }

    @GetMapping("/version")
    public ServiceVersionResponse version() {
        return new ServiceVersionResponse(serviceName, serviceVersion, Instant.now(clock));
    }
}
