package com.zvit.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UrgentReportRequest {

    @NotBlank(message = "ID групи обов'язковий")
    private String groupId;

    @Min(value = 5, message = "Мінімум 5 хвилин")
    @Max(value = 120, message = "Максимум 120 хвилин")
    private int deadlineMinutes;

    // Валідація довжини відбувається після дешифрування в сервісі
    private String message;
}
