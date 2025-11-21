package com.zvit.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Номер телефону не може бути порожнім")
    @Pattern(regexp = "^\\+380\\d{9}$", message = "Номер телефону має бути у форматі +380XXXXXXXXX")
    private String phone;

    @Email(message = "Невірний формат email")
    private String email;

    @NotBlank(message = "Пароль не може бути порожнім")
    @Size(min = 8, max = 100, message = "Пароль має бути від 8 до 100 символів")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Пароль має містити принаймні одну велику літеру, одну малу літеру, одну цифру та один спеціальний символ")
    private String password;

    @NotBlank(message = "Ім'я не може бути порожнім")
    @Size(min = 2, max = 100, message = "Ім'я має бути від 2 до 100 символів")
    private String name;
}
