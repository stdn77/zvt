package com.zvit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zvit.config.CryptoConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
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

    /**
     * Повертає ключ шифрування, зашифрований публічним ключем клієнта (E2E)
     * @param clientPublicKeyBase64 Публічний ключ клієнта у Base64
     * @return AES ключ, зашифрований RSA у Base64 форматі
     */
    public String getEncryptionKeyEncrypted(String clientPublicKeyBase64) {
        if (clientPublicKeyBase64 == null || clientPublicKeyBase64.isEmpty()) {
            log.warn("Client public key not provided, returning plain AES key");
            return getEncryptionKeyBase64();
        }

        try {
            // Декодуємо публічний ключ клієнта
            byte[] publicKeyBytes = Base64.getDecoder().decode(clientPublicKeyBase64);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey clientPublicKey = keyFactory.generatePublic(keySpec);

            // Шифруємо AES ключ публічним ключем клієнта
            Cipher cipher = Cipher.getInstance(cryptoConfig.getRsa().getTransformation());
            cipher.init(Cipher.ENCRYPT_MODE, clientPublicKey);

            // Отримуємо байти AES ключа
            String key = cryptoConfig.getAes().getKey();
            byte[] keyBytes = new byte[32];
            byte[] originalBytes = key.getBytes(StandardCharsets.UTF_8);
            System.arraycopy(originalBytes, 0, keyBytes, 0, Math.min(originalBytes.length, 32));

            byte[] encryptedKey = cipher.doFinal(keyBytes);
            String result = Base64.getEncoder().encodeToString(encryptedKey);

            log.debug("AES key encrypted with client RSA public key (E2E)");
            return result;
        } catch (Exception e) {
            log.error("Failed to encrypt AES key with client public key", e);
            throw new RuntimeException("Помилка E2E шифрування ключа", e);
        }
    }
}
