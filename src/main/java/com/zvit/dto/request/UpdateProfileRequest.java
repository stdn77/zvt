package com.zvit.dto.request;

import lombok.Data;

/**
 * Запит на оновлення профілю користувача.
 * Поле email може бути RSA-зашифрованим.
 */
@Data
public class UpdateProfileRequest {

    private String name;

    private String email;
}
