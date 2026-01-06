package com.zvit.service;

import com.zvit.config.CryptoConfig;
import com.zvit.entity.RsaKeyPair;
import com.zvit.repository.RsaKeyPairRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

/**
 * Сервіс для RSA шифрування.
 * Ключі зберігаються в БД для збереження між перезапусками сервера.
 * Клієнт шифрує дані публічним ключем, сервер дешифрує приватним.
 * Налаштування зчитуються з application.yml (crypto.rsa.*)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RSAKeyService {

    private final CryptoConfig cryptoConfig;
    private final RsaKeyPairRepository rsaKeyPairRepository;

    private KeyPair keyPair;
    private String publicKeyBase64;

    @PostConstruct
    @Transactional
    public void init() {
        loadOrGenerateKeyPair();
    }

    /**
     * Завантажує існуючу пару ключів з БД або генерує нову
     */
    private void loadOrGenerateKeyPair() {
        Optional<RsaKeyPair> existingKeyPair = rsaKeyPairRepository.findByActiveTrue();

        if (existingKeyPair.isPresent()) {
            RsaKeyPair savedKeys = existingKeyPair.get();
            try {
                loadKeyPairFromDatabase(savedKeys);
                log.info("RSA key pair loaded from database: algorithm={}, keySize={}",
                        savedKeys.getAlgorithm(), savedKeys.getKeySize());
                return;
            } catch (Exception e) {
                log.error("Failed to load RSA keys from database, generating new pair", e);
                // Деактивуємо старі ключі
                savedKeys.setActive(false);
                rsaKeyPairRepository.save(savedKeys);
            }
        }

        // Генеруємо нову пару ключів
        generateAndSaveKeyPair();
    }

    /**
     * Завантажує пару ключів з БД
     */
    private void loadKeyPairFromDatabase(RsaKeyPair savedKeys) throws Exception {
        String algorithm = savedKeys.getAlgorithm();
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm);

        // Відновлюємо публічний ключ
        byte[] publicKeyBytes = Base64.getDecoder().decode(savedKeys.getPublicKey());
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        // Відновлюємо приватний ключ
        byte[] privateKeyBytes = Base64.getDecoder().decode(savedKeys.getPrivateKey());
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        this.keyPair = new KeyPair(publicKey, privateKey);
        this.publicKeyBase64 = savedKeys.getPublicKey();
    }

    /**
     * Генерує нову пару ключів та зберігає в БД
     */
    private void generateAndSaveKeyPair() {
        try {
            String algorithm = cryptoConfig.getRsa().getAlgorithm();
            int keySize = cryptoConfig.getRsa().getKeySize();

            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
            keyGen.initialize(keySize, new SecureRandom());
            this.keyPair = keyGen.generateKeyPair();

            // Зберігаємо ключі в Base64
            String publicKeyB64 = Base64.getEncoder().encodeToString(
                    keyPair.getPublic().getEncoded()
            );
            String privateKeyB64 = Base64.getEncoder().encodeToString(
                    keyPair.getPrivate().getEncoded()
            );

            this.publicKeyBase64 = publicKeyB64;

            // Зберігаємо в БД
            RsaKeyPair rsaKeyPair = RsaKeyPair.builder()
                    .publicKey(publicKeyB64)
                    .privateKey(privateKeyB64)
                    .algorithm(algorithm)
                    .keySize(keySize)
                    .active(true)
                    .build();

            rsaKeyPairRepository.save(rsaKeyPair);

            log.info("New RSA key pair generated and saved to database: algorithm={}, keySize={}",
                    algorithm, keySize);

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
