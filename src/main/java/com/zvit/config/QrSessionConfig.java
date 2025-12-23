package com.zvit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфігурація QR сесій для веб-автентифікації.
 */
@Configuration
@ConfigurationProperties(prefix = "qr-session")
@Getter
@Setter
public class QrSessionConfig {
    /** Час життя QR коду в хвилинах */
    private int expiryMinutes = 5;

    /** Час життя авторизованої сесії в годинах */
    private int authorizedExpiryHours = 24;

    /** Розмір токена в байтах */
    private int tokenSizeBytes = 32;

    /** Інтервал очищення в мілісекундах */
    private long cleanupIntervalMs = 3_600_000;
}
