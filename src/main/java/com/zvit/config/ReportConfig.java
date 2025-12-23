package com.zvit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфігурація звітів.
 * Кольори статусів та часові вікна.
 */
@Configuration
@ConfigurationProperties(prefix = "report")
@Getter
@Setter
public class ReportConfig {
    /** Вікно часу для звітів в годинах */
    private int windowHours = 24;

    private Color color = new Color();
    private Percentage percentage = new Percentage();

    @Getter
    @Setter
    public static class Color {
        /** Колір за замовчуванням (немає звітів) */
        private String defaultColor = "#E0E0E0";

        /** Колір для адміністратора */
        private String admin = "#006400";

        /** Колір для раннього звіту */
        private String early = "#C8E6C9";

        /** Колір для простроченого звіту */
        private String overdue = "#FFCDD2";

        /** Колір для критичного стану */
        private String critical = "#FFCDD2";

        /** Колір для попередження */
        private String warning = "#FFF59D";

        /** Колір для нормального стану */
        private String normal = "#C8E6C9";

        // Getter для defaultColor з правильним іменем
        public String getDefaultColor() {
            return defaultColor;
        }
    }

    @Getter
    @Setter
    public static class Percentage {
        /** Поріг для раннього звіту */
        private double early = 25.0;

        /** Поріг для попередження */
        private double warning = 60.0;

        /** Поріг для критичного стану */
        private double critical = 80.0;
    }
}
