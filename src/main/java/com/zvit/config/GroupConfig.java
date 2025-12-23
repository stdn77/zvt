package com.zvit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфігурація груп.
 */
@Configuration
@ConfigurationProperties(prefix = "group")
@Getter
@Setter
public class GroupConfig {
    /** Інтервал очищення в мілісекундах */
    private long cleanupIntervalMs = 3_600_000;

    /** Час життя pending запитів в годинах */
    private int pendingExpiryHours = 24;

    /** Мінімальна кількість адміністраторів */
    private int minAdminCount = 2;
}
