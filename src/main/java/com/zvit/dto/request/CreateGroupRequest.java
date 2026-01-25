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

    @NotNull(message = "Максимальна кількість учасників обов'язкова")
    @Min(value = 2, message = "Мінімум 2 учасники")
    @Max(value = 1000, message = "Максимум 1000 учасників")
    private Integer maxMembers;

    @NotNull(message = "Тип звіту обов'язковий")
    private Group.ReportType reportType;
}
