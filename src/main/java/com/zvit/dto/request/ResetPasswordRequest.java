package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Запит на скидання паролю.
 * Поля phone та newPassword можуть бути RSA-зашифрованими.
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Номер телефону не може бути порожнім")
    private String phone;

    @NotBlank(message = "Новий пароль не може бути порожнім")
    private String newPassword;

    @NotBlank(message = "Firebase токен не може бути порожнім")
    private String firebaseIdToken;
}
