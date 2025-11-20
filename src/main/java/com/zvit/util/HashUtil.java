package com.zvit.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility клас для хешування даних
 */
public class HashUtil {

    private HashUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Хешування телефону за допомогою SHA-256
     */
    public static String hashPhone(String phone) {
        return hash(phone);
    }

    /**
     * Хешування email за допомогою SHA-256 (email переводиться в toLowerCase)
     */
    public static String hashEmail(String email) {
        return hash(email.toLowerCase());
    }

    /**
     * Загальний метод хешування за допомогою SHA-256
     */
    private static String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Помилка хешування: SHA-256 не знайдено", e);
        }
    }

    /**
     * Конвертація byte[] в hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
