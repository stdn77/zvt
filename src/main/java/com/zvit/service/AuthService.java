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
        // Дешифруємо RSA-зашифровані дані (якщо вони зашифровані)
        String phone = rsaKeyService.decryptIfEncrypted(request.getPhone());
        String password = rsaKeyService.decryptIfEncrypted(request.getPassword());
        String name = rsaKeyService.decryptIfEncrypted(request.getName());
        String email = request.getEmail() != null
                ? rsaKeyService.decryptIfEncrypted(request.getEmail())
                : null;

        if (!isValidPhone(phone)) {
            throw new BusinessException("Невірний формат телефону");
        }

        if (!isValidName(name)) {
            throw new BusinessException("Ім'я має бути від 2 до 100 символів");
        }

        if (email != null && !isValidEmail(email)) {
            throw new BusinessException("Невірний формат email");
        }

        String phoneHash = hashPhone(phone);
        String phoneEncrypted = encryptionService.encrypt(phone);
        String emailHash = email != null ? hashEmail(email) : null;
        String emailEncrypted = email != null ? encryptionService.encrypt(email) : null;

        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new BusinessException("Користувач з таким телефоном вже існує");
        }

        if (emailHash != null && userRepository.existsByEmailHash(emailHash)) {
            throw new BusinessException("Користувач з таким email вже існує");
        }

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

        return RegisterResponse.builder()
                .userId(user.getId())
                .phoneVerificationRequired(true)
                .emailVerificationRequired(emailHash != null)
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // Дешифруємо RSA-зашифровані дані (якщо вони зашифровані)
        String phone = rsaKeyService.decryptIfEncrypted(request.getPhone());
        String password = rsaKeyService.decryptIfEncrypted(request.getPassword());

        String phoneHash = hashPhone(phone);

        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new BusinessException("Невірний телефон або пароль"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("Невірний телефон або пароль");
        }

        if (!user.isActive()) {
            throw new BusinessException("Обліковий запис деактивовано");
        }

        user.setLastLoginAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")));
        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user.getId(), phone);

        // Дешифрувати телефон для відповіді
        String decryptedPhone = encryptionService.decrypt(user.getPhoneEncrypted());

        // Дешифрувати email, якщо він є
        String decryptedEmail = user.getEmailEncrypted() != null
                ? encryptionService.decrypt(user.getEmailEncrypted())
                : null;

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