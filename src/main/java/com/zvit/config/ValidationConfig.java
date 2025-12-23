package com.zvit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфігурація валідації.
 * Патерни для перевірки телефону та email.
 */
@Configuration
@ConfigurationProperties(prefix = "validation")
@Getter
@Setter
public class ValidationConfig {
    private Phone phone = new Phone();
    private Email email = new Email();

    @Getter
    @Setter
    public static class Phone {
        /** Регулярний вираз для валідації телефону */
        private String pattern = "^\\+\\d{10,14}$";
    }

    @Getter
    @Setter
    public static class Email {
        /** Регулярний вираз для валідації email */
        private String pattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    }
}
