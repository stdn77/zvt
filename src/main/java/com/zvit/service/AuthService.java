package com.zvit.service;

import com.zvit.dto.request.LoginRequest;
import com.zvit.dto.request.RegisterRequest;
import com.zvit.dto.response.LoginResponse;
import com.zvit.dto.response.RegisterResponse;
import com.zvit.entity.User;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EncryptionService encryptionService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!isValidPhone(request.getPhone())) {
            throw new RuntimeException("Невірний формат телефону");
        }

        if (request.getEmail() != null && !isValidEmail(request.getEmail())) {
            throw new RuntimeException("Невірний формат email");
        }

        String phoneHash = hashPhone(request.getPhone());
        String phoneEncrypted = encryptionService.encrypt(request.getPhone());
        String emailHash = request.getEmail() != null ? hashEmail(request.getEmail()) : null;
        String emailEncrypted = request.getEmail() != null ? encryptionService.encrypt(request.getEmail()) : null;

        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new RuntimeException("Користувач з таким телефоном вже існує");
        }

        if (emailHash != null && userRepository.existsByEmailHash(emailHash)) {
            throw new RuntimeException("Користувач з таким email вже існує");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .phoneHash(phoneHash)
                .phoneEncrypted(phoneEncrypted)
                .emailHash(emailHash)
                .emailEncrypted(emailEncrypted)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phoneVerified(false)
                .emailVerified(false)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
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
        String phoneHash = hashPhone(request.getPhone());
        System.out.println("DEBUG: Login attempt for phone: " + request.getPhone());
        System.out.println("DEBUG: Phone hash: " + phoneHash);

        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> {
                    System.out.println("DEBUG: User not found for phone hash: " + phoneHash);
                    return new RuntimeException("Невірний телефон або пароль");
                });

        System.out.println("DEBUG: User found: " + user.getId() + ", name: " + user.getName());
        System.out.println("DEBUG: User password hash: " + user.getPasswordHash());
        System.out.println("DEBUG: Password matches: " + passwordEncoder.matches(request.getPassword(), user.getPasswordHash()));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            System.out.println("DEBUG: Password mismatch for user: " + user.getId());
            throw new RuntimeException("Невірний телефон або пароль");
        }

        if (!user.isActive()) {
            throw new RuntimeException("Обліковий запис деактивовано");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user.getId(), request.getPhone());

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
                .build();
    }

    public String test() {
        return "Auth API працює! Версія: 1.3 (JWT)";
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
}