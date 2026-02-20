package com.zvit.service;

import com.zvit.dto.request.LoginRequest;
import com.zvit.dto.request.RegisterRequest;
import com.zvit.dto.request.ResetPasswordRequest;
import com.zvit.dto.response.LoginResponse;
import com.zvit.dto.response.RegisterResponse;
import com.zvit.entity.User;
import com.zvit.exception.BusinessException;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EncryptionService encryptionService;
    private final RSAKeyService rsaKeyService;
    private final ResponseEncryptionService responseEncryptionService;
    private final FirebaseService firebaseService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("[REGISTER] === Початок реєстрації нового користувача ===");
        log.info("[REGISTER] Отримані дані - phone: {}, name: {}, email: {}",
                maskPhone(request.getPhone()),
                request.getName() != null ? request.getName().substring(0, Math.min(3, request.getName().length())) + "***" : "null",
                request.getEmail() != null ? "present" : "null");

        // Дешифруємо RSA-зашифровані дані (якщо вони зашифровані)
        log.info("[REGISTER] Спроба дешифрування RSA даних...");
        String phone = rsaKeyService.decryptIfEncrypted(request.getPhone());
        String password = rsaKeyService.decryptIfEncrypted(request.getPassword());
        String name = rsaKeyService.decryptIfEncrypted(request.getName());
        String email = request.getEmail() != null
                ? rsaKeyService.decryptIfEncrypted(request.getEmail())
                : null;

        log.info("[REGISTER] Після дешифрування - phone: {}, name length: {}",
                maskPhone(phone), name != null ? name.length() : 0);

        if (!isValidPhone(phone)) {
            log.warn("[REGISTER] ПОМИЛКА: Невірний формат телефону: {}", maskPhone(phone));
            throw new BusinessException("Невірний формат телефону");
        }
        log.info("[REGISTER] Телефон валідний");

        if (!isValidName(name)) {
            log.warn("[REGISTER] ПОМИЛКА: Невірне ім'я (length={})", name != null ? name.length() : 0);
            throw new BusinessException("Ім'я має бути від 2 до 100 символів");
        }
        log.info("[REGISTER] Ім'я валідне");

        if (email != null && !isValidEmail(email)) {
            log.warn("[REGISTER] ПОМИЛКА: Невірний формат email");
            throw new BusinessException("Невірний формат email");
        }
        if (email != null) {
            log.info("[REGISTER] Email валідний");
        }

        log.info("[REGISTER] Хешування та шифрування даних...");
        String phoneHash = hashPhone(phone);
        String phoneEncrypted = encryptionService.encrypt(phone);
        String emailHash = email != null ? hashEmail(email) : null;
        String emailEncrypted = email != null ? encryptionService.encrypt(email) : null;
        log.info("[REGISTER] Дані захешовано та зашифровано");

        log.info("[REGISTER] Перевірка унікальності телефону...");
        if (userRepository.existsByPhoneHash(phoneHash)) {
            log.warn("[REGISTER] ПОМИЛКА: Користувач з таким телефоном вже існує: {}", maskPhone(phone));
            throw new BusinessException("Користувач з таким телефоном вже існує");
        }
        log.info("[REGISTER] Телефон унікальний");

        if (emailHash != null) {
            log.info("[REGISTER] Перевірка унікальності email...");
            if (userRepository.existsByEmailHash(emailHash)) {
                log.warn("[REGISTER] ПОМИЛКА: Користувач з таким email вже існує");
                throw new BusinessException("Користувач з таким email вже існує");
            }
            log.info("[REGISTER] Email унікальний");
        }

        log.info("[REGISTER] Створення нового користувача в БД...");
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .phoneHash(phoneHash)
                .phoneEncrypted(phoneEncrypted)
                .emailHash(emailHash)
                .emailEncrypted(emailEncrypted)
                .passwordHash(passwordEncoder.encode(password))
                .name(name)
                .phoneVerified(false)
                .emailVerified(false)
                .active(true)
                .createdAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")))
                .updatedAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")))
                .build();

        userRepository.save(user);
        log.info("[REGISTER] УСПІХ: Користувача створено з ID: {}", user.getId());

        return RegisterResponse.builder()
                .userId(user.getId())
                .phoneVerificationRequired(true)
                .emailVerificationRequired(emailHash != null)
                .build();
    }

    /**
     * Маскує номер телефону для логування (показує перші 4 та останні 2 символи)
     */
    private String maskPhone(String phone) {
        if (phone == null) return "null";
        if (phone.length() <= 6) return "***";
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 2);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("[LOGIN] === Початок входу користувача ===");
        log.info("[LOGIN] Отримані дані - phone: {}, hasPassword: {}, hasClientPublicKey: {}",
                maskPhone(request.getPhone()),
                request.getPassword() != null && !request.getPassword().isEmpty(),
                request.getClientPublicKey() != null && !request.getClientPublicKey().isEmpty());

        // Дешифруємо RSA-зашифровані дані (якщо вони зашифровані)
        log.info("[LOGIN] Спроба дешифрування RSA даних...");
        String phone = rsaKeyService.decryptIfEncrypted(request.getPhone());
        String password = rsaKeyService.decryptIfEncrypted(request.getPassword());
        log.info("[LOGIN] Після дешифрування - phone: {}", maskPhone(phone));

        String phoneHash = hashPhone(phone);
        log.info("[LOGIN] Пошук користувача за хешем телефону...");

        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> {
                    log.warn("[LOGIN] ПОМИЛКА: Користувача не знайдено за телефоном: {}", maskPhone(phone));
                    return new BusinessException("Невірний телефон або пароль");
                });
        log.info("[LOGIN] Користувача знайдено: ID={}, name={}", user.getId(), user.getName());

        log.info("[LOGIN] Перевірка пароля...");
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("[LOGIN] ПОМИЛКА: Невірний пароль для користувача: {}", user.getId());
            throw new BusinessException("Невірний телефон або пароль");
        }
        log.info("[LOGIN] Пароль вірний");

        if (!user.isActive()) {
            log.warn("[LOGIN] ПОМИЛКА: Обліковий запис деактивовано: {}", user.getId());
            throw new BusinessException("Обліковий запис деактивовано");
        }
        log.info("[LOGIN] Обліковий запис активний");

        user.setLastLoginAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")));
        userRepository.save(user);
        log.info("[LOGIN] Оновлено час останнього входу");

        String jwtToken = jwtService.generateToken(user.getId(), phone);
        log.info("[LOGIN] JWT токен згенеровано");

        // Дешифрувати телефон для відповіді
        String decryptedPhone = encryptionService.decrypt(user.getPhoneEncrypted());

        // Дешифрувати email, якщо він є
        String decryptedEmail = user.getEmailEncrypted() != null
                ? encryptionService.decrypt(user.getEmailEncrypted())
                : null;

        log.info("[LOGIN] УСПІХ: Вхід виконано для користувача ID={}, phone={}", user.getId(), maskPhone(decryptedPhone));

        return LoginResponse.builder()
                .userId(user.getId())
                .token(jwtToken)
                .name(user.getName())
                .phone(decryptedPhone)
                .email(decryptedEmail)
                .serverTime(LocalDateTime.now(java.time.ZoneId.of("Europe/Kiev")))  // Серверний час (Київ)
                .timezone("Europe/Kiev")          // Часова зона сервера
                .encryptionKey(responseEncryptionService.getEncryptionKeyBase64()) // AES ключ для дешифрування
                .build();
    }

    public String test() {
        return "Auth API працює! Версія: 1.3 (JWT)";
    }

    /**
     * Скидання паролю через верифікацію Firebase
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Дешифруємо RSA-зашифровані дані
        String phone = rsaKeyService.decryptIfEncrypted(request.getPhone());
        String newPassword = rsaKeyService.decryptIfEncrypted(request.getNewPassword());

        // Перевіряємо формат телефону
        if (!isValidPhone(phone)) {
            throw new BusinessException("Невірний формат телефону");
        }

        // Перевіряємо пароль
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException("Пароль повинен містити мінімум 6 символів");
        }

        // Верифікуємо Firebase токен
        String verifiedPhone = firebaseService.verifyIdTokenAndGetPhone(request.getFirebaseIdToken());
        if (verifiedPhone == null) {
            throw new BusinessException("Не вдалося підтвердити номер телефону. Спробуйте ще раз.");
        }

        // Перевіряємо що телефон з токена співпадає з наданим
        if (!verifiedPhone.equals(phone)) {
            throw new BusinessException("Номер телефону не співпадає з верифікованим");
        }

        // Знаходимо користувача
        String phoneHash = hashPhone(phone);
        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new BusinessException("Користувача з таким номером не знайдено"));

        // Оновлюємо пароль
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")));
        userRepository.save(user);
    }

    private String hashPhone(String phone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phone.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Помилка хешування телефону", e);
        }
    }

    private String hashEmail(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Помилка хешування email", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^\\+\\d{10,14}$");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private boolean isValidName(String name) {
        return name != null && name.length() >= 2 && name.length() <= 100;
    }
}