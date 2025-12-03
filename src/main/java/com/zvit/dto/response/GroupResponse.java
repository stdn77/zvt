package com.zvit.dto.response;

import com.zvit.entity.Group;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    private String groupId;
    private String externalName;
    private String accessCode;
    private Integer maxMembers;
    private Integer currentMembers;
    private Group.ReportType reportType;
    private String userRole;
    private LocalDateTime createdAt;

    // Налаштування розкладу звітів
    private Group.ScheduleType scheduleType;
    private List<String> fixedTimes;  // Для FIXED_TIMES
    private Integer intervalMinutes;  // Для INTERVAL
    private String intervalStartTime; // Для INTERVAL

    // Налаштування слів для простих звітів
    private String positiveWord;  // Наприклад: "ОК", "ДОБРЕ"
    private String negativeWord;  // Наприклад: "НЕ ОК", "ПОГАНО"

    // Для учасників групи - час останнього звіту
    private LocalDateTime lastReportAt;

    // Серверний час для синхронізації
    private LocalDateTime serverTime;
    private String timezone;
}