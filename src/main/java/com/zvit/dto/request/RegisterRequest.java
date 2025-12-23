package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Запит на реєстрацію.
 * Поля phone, email та password можуть бути RSA-зашифрованими.
 * Валідація формату відбувається після дешифрування в AuthService.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Номер телефону не може бути порожнім")
    private String phone;

    private String email;

    @NotBlank(message = "Пароль не може бути порожнім")
    private String password;

    @NotBlank(message = "Ім'я не може бути порожнім")
    @Size(min = 2, max = 100, message = "Ім'я має бути від 2 до 100 символів")
    private String name;
}
