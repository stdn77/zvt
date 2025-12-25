package com.zvit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Інформація про активну термінову сесію
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrgentSessionInfo {

    private boolean active;                    // Чи є активна термінова сесія
    private String sessionId;                  // ID сесії
    private LocalDateTime requestedAt;         // Коли було надіслано запит
    private LocalDateTime expiresAt;           // Коли закінчується
    private String message;                    // Повідомлення адміна
    private String requestedByUserId;          // ID адміна
    private String requestedByUserName;        // Ім'я адміна
    private int totalMembers;                  // Всього учасників (без адмінів)
    private int respondedCount;                // Скільки відповіли
    private long remainingSeconds;             // Скільки секунд залишилось
}
