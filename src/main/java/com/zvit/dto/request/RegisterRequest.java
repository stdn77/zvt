package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Запит на реєстрацію.
 * Поля phone, email, password та name можуть бути RSA-зашифрованими.
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
    private String name; // Валідація розміру після дешифрування в AuthService
}
