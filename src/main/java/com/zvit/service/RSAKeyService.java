package com.zvit.service;

import com.zvit.config.CryptoConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
 * Налаштування зчитуються з application.yml (crypto.rsa.*)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RSAKeyService {

    private final CryptoConfig cryptoConfig;

    private KeyPair keyPair;
    private String publicKeyBase64;

    @PostConstruct
    public void init() {
        generateKeyPair();
        log.info("RSA key pair generated: algorithm={}, keySize={}",
                cryptoConfig.getRsa().getAlgorithm(),
                cryptoConfig.getRsa().getKeySize());
    }

    /**
     * Генерує нову пару ключів
     */
    private void generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(cryptoConfig.getRsa().getAlgorithm());
            keyGen.initialize(cryptoConfig.getRsa().getKeySize(), new SecureRandom());
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
     * Повертає розмір ключа в бітах
     */
    public int getKeySize() {
        return cryptoConfig.getRsa().getKeySize();
    }

    /**
     * Дешифрує дані, зашифровані публічним ключем
     */
    public String decrypt(String encryptedBase64) {
        try {
            Cipher cipher = Cipher.getInstance(cryptoConfig.getRsa().getTransformation());
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
            return decoded.length == cryptoConfig.getRsa().getEncryptedDataSize();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Дешифрує значення якщо воно зашифроване, інакше повертає як є.
     * Це дозволяє підтримувати як зашифровані, так і незашифровані запити
     * (для зворотної сумісності).
     * Якщо дешифрування не вдається (наприклад, ключі не співпадають) - повертає оригінальне значення.
     */
    public String decryptIfEncrypted(String value) {
        if (isEncrypted(value)) {
            try {
                return decrypt(value);
            } catch (Exception e) {
                log.warn("Failed to decrypt value, returning as-is. Client may need to refresh public key.");
                return value;
            }
        }
        return value;
    }
}
