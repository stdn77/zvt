package com.zvit.dto.response;

import com.zvit.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusResponse {
    private String userId;
    private String userName;
    private Role role;
    private boolean hasReported;
    private LocalDateTime lastReportAt;  // MVZ - час останнього звіту (може бути null)
    private String lastReportResponse;

    // Нові поля для обчислення кольорів на фронтенді
    private LocalDateTime previousScheduledTime;  // MZZ - минулий запланований час
    private LocalDateTime nextScheduledTime;      // NZ - наступний запланований час
    private LocalDateTime serverTime;             // Серверний час для синхронізації
    private String timezone;                      // Часова зона сервера

    private String phoneNumber; // Тільки для адміністраторів
}