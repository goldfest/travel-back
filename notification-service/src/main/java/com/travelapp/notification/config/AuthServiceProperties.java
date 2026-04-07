package com.travelapp.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "services.auth")
public class AuthServiceProperties {
    private String url;
    private String mePath = "/internal/users/me";
    private String infoPath = "/internal/users/{id}/info";
    private String existsPath = "/internal/users/{id}/exists";
}