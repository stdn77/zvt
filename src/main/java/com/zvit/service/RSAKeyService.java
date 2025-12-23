package com.zvit.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * Сервіс для RSA шифрування.
 * Генерує пару ключів при старті сервера.
 * Клієнт шифрує дані публічним ключем, сервер дешифрує приватним.
 */
@Slf4j
@Service
public class RSAKeyService {

    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final int KEY_SIZE = 2048;

    private KeyPair keyPair;
    private String publicKeyBase64;

    @PostConstruct
    public void init() {
        generateKeyPair();
        log.info("RSA key pair generated successfully");
    }

    /**
     * Генерує нову пару ключів
     */
    private void generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
            keyGen.initialize(KEY_SIZE, new SecureRandom());
            this.keyPair = keyGen.generateKeyPair();

            // Зберігаємо публічний ключ у Base64 форматі
            this.publicKeyBase64 = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded()
            );
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Помилка генерації RSA ключів", e);
        }
    }

    /**
     * Повертає публічний ключ у Base64 форматі
     */
    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    /**
     * Дешифрує дані, зашифровані публічним ключем
     */
    public String decrypt(String encryptedBase64) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());

            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedBase64);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("RSA decryption failed", e);
            throw new RuntimeException("Помилка дешифрування даних", e);
        }
    }

    /**
     * Перевіряє чи рядок є зашифрованим RSA
     * (перевірка Base64 формату та довжини)
     */
    public boolean isEncrypted(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            // RSA 2048 біт = 256 байт зашифрованих даних
            return decoded.length == 256;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Дешифрує значення якщо воно зашифроване, інакше повертає як є.
     * Це дозволяє підтримувати як зашифровані, так і незашифровані запити
     * (для зворотної сумісності).
     */
    public String decryptIfEncrypted(String value) {
        if (isEncrypted(value)) {
            return decrypt(value);
        }
        return value;
    }
}
