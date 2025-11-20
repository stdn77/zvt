package com.zvit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Entity
@Table(name = "`groups`", indexes = {
    @Index(name = "idx_group_created_by", columnList = "created_by"),
    @Index(name = "idx_group_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "external_name", nullable = false, length = 100)
    private String externalName; // Назва яку бачать всі

    @Column(name = "access_code", unique = true, nullable = false, length = 11)
    private String accessCode; // GROUP-12345

    @Column(name = "internal_code", unique = true, nullable = false, length = 17)
    private String internalCode; // @st64Q52z11HGtrps (тільки для адмінів)

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    private ReportType reportType;

    @Column(name = "created_by", nullable = false)
    private String createdBy; // UUID користувача-створювача

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ReportType {
        SIMPLE,    // Простий: ОК/Проблема + коментар
        EXTENDED,  // Розгорнутий: 5 полів
        URGENT     // Терміновий запит
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Генерувати коди якщо не встановлені
        if (accessCode == null) {
            accessCode = generateAccessCode();
        }
        if (internalCode == null) {
            internalCode = generateInternalCode();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generateAccessCode() {
        int number = 10000 + SECURE_RANDOM.nextInt(90000); // 10000-99999
        return "GROUP-" + number;
    }

    private String generateInternalCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder code = new StringBuilder("@");
        for (int i = 0; i < 16; i++) {
            code.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return code.toString();
    }
}
