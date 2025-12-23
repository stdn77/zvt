package com.zvit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфігурація Rate Limiting.
 * Захист від brute-force атак.
 */
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitConfig {
    /** Максимальна кількість спроб логіну за вікно часу */
    private int maxAttemptsLogin = 5;

    /** Максимальна кількість спроб реєстрації за вікно часу */
    private int maxAttemptsRegister = 3;

    /** Розмір вікна часу в мілісекундах (1 хвилина) */
    private long windowSizeMs = 60_000;

    /** Тривалість блокування в мілісекундах (5 хвилин) */
    private long blockDurationMs = 300_000;

    /** Інтервал очищення застарілих записів в хвилинах */
    private int cleanupIntervalMinutes = 5;
}
