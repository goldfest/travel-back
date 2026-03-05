package com.travelapp.poi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth-service")
public class AuthServiceProperties {

    /** Например: http://localhost:8084 или http://auth-service:8084 */
    private String baseUrl;

    /** В auth-service точно есть /users/me */
    private String mePath = "/users/me";

    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration responseTimeout = Duration.ofSeconds(3);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getMePath() { return mePath; }
    public void setMePath(String mePath) { this.mePath = mePath; }

    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration responseTimeout) { this.responseTimeout = responseTimeout; }
}