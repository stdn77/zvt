package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Номер телефону не може бути порожнім")
    @Pattern(regexp = "^\\+380\\d{9}$", message = "Номер телефону має бути у форматі +380XXXXXXXXX")
    private String phone;

    @NotBlank(message = "Пароль не може бути порожнім")
    private String password;
}
