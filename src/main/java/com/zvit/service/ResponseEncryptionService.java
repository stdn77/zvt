package com.zvit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zvit.config.CryptoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Сервіс для шифрування відповідей API.
 * Використовує AES для шифрування чутливих даних перед відправкою клієнту.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseEncryptionService {

    private final CryptoConfig cryptoConfig;
    private final ObjectMapper objectMapper;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * Шифрує об'єкт у JSON та повертає зашифрований Base64 рядок
     */
    public String encryptObject(Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return encrypt(json);
        } catch (Exception e) {
            log.error("Failed to encrypt object", e);
            throw new RuntimeException("Помилка шифрування даних", e);
        }
    }

    /**
     * Шифрує рядок за допомогою AES
     */
    public String encrypt(String data) {
        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("AES encryption failed", e);
            throw new RuntimeException("Помилка шифрування", e);
        }
    }

    /**
     * Дешифрує Base64 рядок
     */
    public String decrypt(String encryptedData) {
        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES decryption failed", e);
            throw new RuntimeException("Помилка розшифрування", e);
        }
    }

    /**
     * Дешифрує та конвертує у вказаний тип
     */
    public <T> T decryptObject(String encryptedData, Class<T> valueType) {
        try {
            String json = decrypt(encryptedData);
            return objectMapper.readValue(json, valueType);
        } catch (Exception e) {
            log.error("Failed to decrypt object", e);
            throw new RuntimeException("Помилка розшифрування даних", e);
        }
    }

    private SecretKeySpec getSecretKey() {
        String key = cryptoConfig.getAes().getKey();
        // Ensure key is exactly 32 bytes for AES-256
        byte[] keyBytes = new byte[32];
        byte[] originalBytes = key.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(originalBytes, 0, keyBytes, 0, Math.min(originalBytes.length, 32));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * Повертає ключ шифрування для передачі клієнту (під час логіну)
     * Клієнт використовуватиме цей ключ для дешифрування відповідей
     */
    public String getEncryptionKeyBase64() {
        String key = cryptoConfig.getAes().getKey();
        byte[] keyBytes = new byte[32];
        byte[] originalBytes = key.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(originalBytes, 0, keyBytes, 0, Math.min(originalBytes.length, 32));
        return Base64.getEncoder().encodeToString(keyBytes);
    }
}
