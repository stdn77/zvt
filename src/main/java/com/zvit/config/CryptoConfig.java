package com.zvit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Конфігурація криптографії.
 * RSA, AES та хешування.
 */
@Configuration
@ConfigurationProperties(prefix = "crypto")
@Getter
@Setter
public class CryptoConfig {
    private Hash hash = new Hash();
    private Rsa rsa = new Rsa();
    private Aes aes = new Aes();

    @Getter
    @Setter
    public static class Hash {
        /** Алгоритм хешування */
        private String algorithm = "SHA-256";
    }

    @Getter
    @Setter
    public static class Rsa {
        /** Алгоритм RSA */
        private String algorithm = "RSA";

        /** Трансформація для Cipher */
        private String transformation = "RSA/ECB/PKCS1Padding";

        /** Розмір ключа в бітах */
        private int keySize = 2048;

        /** Розмір зашифрованих даних в байтах */
        private int encryptedDataSize = 256;
    }

    @Getter
    @Setter
    public static class Aes {
        /** Алгоритм AES */
        private String algorithm = "AES";

        /** Ключ шифрування (32 символи для AES-256) */
        private String key = "MySecretKey12345MySecretKey12345";
    }
}
