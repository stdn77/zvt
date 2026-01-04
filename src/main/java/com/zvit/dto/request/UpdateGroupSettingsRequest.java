package com.zvit.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class UpdateGroupSettingsRequest {

    // Назва групи (зовнішня)
    private String externalName;

    @Pattern(regexp = "^(SIMPLE|EXTENDED|URGENT)$", message = "Тип звіту має бути SIMPLE, EXTENDED або URGENT")
    private String reportType;

    @Pattern(regexp = "^(FIXED_TIMES|INTERVAL)$", message = "Тип розкладу має бути FIXED_TIMES або INTERVAL")
    private String scheduleType;

    // Для FIXED_TIMES: список часів (формат HH:mm)
    private List<@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Формат часу: HH:mm") String> fixedTimes;

    // Для INTERVAL: інтервал в хвилинах (5-1440)
    @Min(value = 5, message = "Мінімальний інтервал 5 хвилин")
    @Max(value = 1440, message = "Максимальний інтервал 24 години (1440 хвилин)")
    private Integer intervalMinutes;

    // Для INTERVAL: час початку (формат HH:mm)
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Формат часу: HH:mm")
    private String intervalStartTime;

    // Налаштування слів для простих звітів
    @Pattern(regexp = "^.{1,20}$", message = "Довжина слова має бути від 1 до 20 символів")
    private String positiveWord;  // Наприклад: "ОК", "ДОБРЕ"

    @Pattern(regexp = "^.{1,20}$", message = "Довжина слова має бути від 1 до 20 символів")
    private String negativeWord;  // Наприклад: "НЕ ОК", "ПОГАНО"
}
