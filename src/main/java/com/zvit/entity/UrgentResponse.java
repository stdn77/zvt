package com.zvit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Відповідь користувача на терміновий запит
 */
@Entity
@Table(name = "urgent_responses", indexes = {
    @Index(name = "idx_urgent_session_user", columnList = "urgent_session_id, user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrgentResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "urgent_session_id", nullable = false, length = 36)
    private String urgentSessionId;  // ID термінової сесії

    @Column(name = "group_id", nullable = false, length = 36)
    private String groupId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "responded_at", nullable = false)
    private LocalDateTime respondedAt;

    @Column(name = "report_id", length = 36)
    private String reportId;  // Посилання на звіт

    @PrePersist
    protected void onCreate() {
        if (respondedAt == null) {
            respondedAt = LocalDateTime.now();
        }
    }
}
