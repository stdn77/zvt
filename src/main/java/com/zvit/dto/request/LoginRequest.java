package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Запит на логін.
 * Поля phone та password можуть бути RSA-зашифрованими.
 * Валідація формату відбувається після дешифрування в AuthService.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Номер телефону не може бути порожнім")
    private String phone;

    @NotBlank(message = "Пароль не може бути порожнім")
    private String password;

    /**
     * Публічний ключ клієнта для E2E шифрування AES ключа (Base64).
     * Якщо присутній - AES ключ буде зашифрований цим ключем.
     */
    private String clientPublicKey;
}
