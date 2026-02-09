package com.zvit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "admin_logs", indexes = {
    @Index(name = "idx_admin_log_date", columnList = "log_date"),
    @Index(name = "idx_admin_log_user_name", columnList = "user_name"),
    @Index(name = "idx_admin_log_ip", columnList = "ip_address"),
    @Index(name = "idx_admin_log_level", columnList = "log_level"),
    @Index(name = "idx_admin_log_method", columnList = "request_method")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "log_time", nullable = false)
    private LocalTime logTime;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "log_level", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private LogLevel logLevel;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_uri", length = 500)
    private String requestUri;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "message", length = 1000)
    private String message;

    public enum LogLevel {
        INFO,
        WARN,
        ERROR
    }
}
