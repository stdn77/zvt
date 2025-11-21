package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AddMemberRequest {

    @NotBlank(message = "Номер телефону обов'язковий")
    @Pattern(regexp = "^\\+380\\d{9}$", message = "Формат: +380XXXXXXXXX")
    private String phone;
}
