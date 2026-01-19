package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class JoinGroupRequest {

    @NotBlank(message = "Код доступу обов'язковий")
    @Pattern(regexp = "^[A-Z]{3}-\\d{4}-[A-Z]{3}$", message = "Невірний формат коду (очікується: ABC-1234-XYZ)")
    private String accessCode;
}
