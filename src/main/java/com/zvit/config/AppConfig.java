package com.zvit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Централізована конфігурація додатку.
 * Всі константи зчитуються з application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppConfig {
    private String timezone = "Europe/Kiev";
    private String version = "1.0.0";
    private String baseUrl = "http://localhost:8080";
}
