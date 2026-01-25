package com.zvit.dto.request;

import com.zvit.entity.Group;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequest {

    @NotBlank(message = "Назва групи обов'язкова")
    private String externalName;

    // TODO: Повернути валідацію після тестування
    // @NotNull(message = "Максимальна кількість учасників обов'язкова")
    // @Min(value = 2, message = "Мінімум 2 учасники")
    // @Max(value = 300, message = "Максимум 300 учасників")
    private Integer maxMembers = 300;

    @NotNull(message = "Тип звіту обов'язковий")
    private Group.ReportType reportType;
}
