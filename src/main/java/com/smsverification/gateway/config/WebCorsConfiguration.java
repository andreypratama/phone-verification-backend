package com.smsverification.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebCorsConfiguration implements WebMvcConfigurer {

    private final AppProperties properties;

    public WebCorsConfiguration(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("OPTIONS", "GET", "POST")
                .allowedHeaders("Content-Type", "X-Timestamp", "X-Nonce", "X-Signature")
                .maxAge(3600);
    }
}
