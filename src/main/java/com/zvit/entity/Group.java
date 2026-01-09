package com.zvit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Random;

@Entity
@Table(name = "`groups`")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "external_name", nullable = false, length = 100)
    private String externalName; // Назва яку бачать всі

    @Column(name = "access_code", unique = true, nullable = false, length = 12)
    private String accessCode; // ABC-1234-XYZ

    @Column(name = "internal_code", unique = true, nullable = false, length = 17)
    private String internalCode; // @st64Q52z11HGtrps (тільки для адмінів)

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 20)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", length = 20)
    private ScheduleType scheduleType; // FIXED_TIMES або INTERVAL

    // Для FIXED_TIMES: до 5 часів звітування (формат HH:mm)
    @Column(name = "fixed_time_1", length = 5)
    private String fixedTime1;

    @Column(name = "fixed_time_2", length = 5)
    private String fixedTime2;

    @Column(name = "fixed_time_3", length = 5)
    private String fixedTime3;

    @Column(name = "fixed_time_4", length = 5)
    private String fixedTime4;

    @Column(name = "fixed_time_5", length = 5)
    private String fixedTime5;

    // Для INTERVAL: інтервал в хвилинах (5-1440)
    @Column(name = "interval_minutes")
    private Integer intervalMinutes;

    // Для INTERVAL: час початку відліку (формат HH:mm)
    @Column(name = "interval_start_time", length = 5)
    private String intervalStartTime;

    // Налаштування слів для простих звітів
    @Column(name = "positive_word", length = 20)
    @Builder.Default
    private String positiveWord = "ОК"; // За замовчуванням "ОК"

    @Column(name = "negative_word", length = 20)
    @Builder.Default
    private String negativeWord = "НЕ ОК"; // За замовчуванням "НЕ ОК"

    @Column(name = "created_by", nullable = false)
    private String createdBy; // UUID користувача-створювача

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Терміновий збір - активна сесія
    @Column(name = "urgent_session_id", length = 36)
    private String urgentSessionId;  // UUID активної термінової сесії (null = немає активної)

    @Column(name = "urgent_requested_at")
    private LocalDateTime urgentRequestedAt;  // Коли було надіслано запит

    @Column(name = "urgent_expires_at")
    private LocalDateTime urgentExpiresAt;  // Коли закінчується збір

    @Column(name = "urgent_requested_by", length = 36)
    private String urgentRequestedBy;  // ID адміна який запросив

    @Column(name = "urgent_message", length = 500)
    private String urgentMessage;  // Повідомлення термінового запиту

    public enum ReportType {
        SIMPLE,    // Простий: ОК/Проблема + коментар
        EXTENDED,  // Розгорнутий: 5 полів
        URGENT     // Терміновий запит
    }

    public enum ScheduleType {
        FIXED_TIMES,  // На конкретний час (до 5 разів на добу)
        INTERVAL      // Через певний інтервал
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
        Random random = new Random();
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

        // Формат: ABC-1234-XYZ (3 букви - 4 цифри - 3 букви)
        StringBuilder code = new StringBuilder();

        // Перші 3 букви
        for (int i = 0; i < 3; i++) {
            code.append(letters.charAt(random.nextInt(letters.length())));
        }
        code.append("-");

        // 4 цифри
        code.append(String.format("%04d", random.nextInt(10000)));
        code.append("-");

        // Останні 3 букви
        for (int i = 0; i < 3; i++) {
            code.append(letters.charAt(random.nextInt(letters.length())));
        }

        return code.toString();
    }

    private String generateInternalCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder code = new StringBuilder("@");
        for (int i = 0; i < 16; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    /**
     * Regenerate access code for the group
     */
    public void regenerateAccessCode() {
        this.accessCode = generateAccessCode();
        this.updatedAt = LocalDateTime.now();
    }
}
