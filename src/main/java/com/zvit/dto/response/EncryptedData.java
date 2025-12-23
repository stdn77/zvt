package com.zvit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Обгортка для зашифрованих даних у відповідях API.
 * Поле 'payload' містить AES-зашифрований JSON об'єкт.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedData {

    /** AES-зашифрований JSON у Base64 форматі */
    private String payload;

    /** Прапорець що дані зашифровані */
    private boolean encrypted;

    public static EncryptedData of(String encryptedPayload) {
        return EncryptedData.builder()
                .payload(encryptedPayload)
                .encrypted(true)
                .build();
    }
}
