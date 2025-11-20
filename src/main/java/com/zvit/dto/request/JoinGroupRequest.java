package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class JoinGroupRequest {
    
    @NotBlank(message = "Код доступу обов'язковий")
    @Pattern(regexp = "^GROUP-\\d{5}$", message = "Невірний формат коду")
    private String accessCode;
}
