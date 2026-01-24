package com.zvit.service;

import com.zvit.dto.request.UpdateProfileRequest;
import com.zvit.entity.User;
import com.zvit.exception.BusinessException;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RSAKeyService rsaKeyService;
    private final EncryptionService encryptionService;

    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Користувача не знайдено"));
    }

    @Transactional
    public void updateFcmToken(String userId, String fcmToken) {
        User user = getUserById(userId);
        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }

    @Transactional
    public void updateFcmToken(String userId, String fcmToken, String deviceType) {
        User user = getUserById(userId);
        String tokenPreview = fcmToken != null && fcmToken.length() > 20 ? fcmToken.substring(0, 20) + "..." : fcmToken;
        if ("WEB".equalsIgnoreCase(deviceType)) {
            String oldToken = user.getFcmTokenWeb();
            boolean isNew = oldToken == null || !oldToken.equals(fcmToken);
            user.setFcmTokenWeb(fcmToken);
            log.info("[FCM] Updated WEB FCM token for user: {}, token: {}, isNew: {}", userId, tokenPreview, isNew);
        } else {
            String oldToken = user.getFcmToken();
            boolean isNew = oldToken == null || !oldToken.equals(fcmToken);
            user.setFcmToken(fcmToken);
            log.info("[FCM] Updated ANDROID FCM token for user: {}, token: {}, isNew: {}", userId, tokenPreview, isNew);
        }
        userRepository.save(user);
    }

    @Transactional
    public void clearFcmToken(String userId) {
        User user = getUserById(userId);
        user.setFcmToken(null);
        userRepository.save(user);
    }

    @Transactional
    public void clearFcmTokenWeb(String userId) {
        User user = getUserById(userId);
        user.setFcmTokenWeb(null);
        userRepository.save(user);
    }

    /**
     * Check if user has Android FCM token (for deciding whether to send server reminders)
     */
    public boolean hasAndroidToken(String userId) {
        User user = getUserById(userId);
        return user.getFcmToken() != null && !user.getFcmToken().isEmpty();
    }

    /**
     * Оновлення профілю користувача (ім'я та/або email)
     */
    @Transactional
    public void updateProfile(String userId, UpdateProfileRequest request) {
        log.info("📝 Updating profile for user: {}", userId);

        User user = getUserById(userId);

        // Оновлення імені
        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            String newName = request.getName().trim();
            if (newName.length() < 2 || newName.length() > 100) {
                throw new BusinessException("Ім'я повинно бути від 2 до 100 символів");
            }
            log.info("   Updating name: {} -> {}", user.getName(), newName);
            user.setName(newName);
        }

        // Оновлення email
        if (request.getEmail() != null) {
            String email = rsaKeyService.decryptIfEncrypted(request.getEmail());

            if (email.trim().isEmpty()) {
                // Видалення email
                log.info("   Removing email");
                user.setEmailHash(null);
                user.setEmailEncrypted(null);
                user.setEmailVerified(false);
            } else {
                // Валідація email
                if (!isValidEmail(email)) {
                    throw new BusinessException("Невірний формат email");
                }

                String emailHash = hashValue(email.toLowerCase());

                // Перевірка унікальності (якщо email змінився)
                if (user.getEmailHash() == null || !user.getEmailHash().equals(emailHash)) {
                    if (userRepository.existsByEmailHash(emailHash)) {
                        throw new BusinessException("Цей email вже використовується");
                    }

                    log.info("   Updating email");
                    user.setEmailHash(emailHash);
                    user.setEmailEncrypted(encryptionService.encrypt(email));
                    user.setEmailVerified(false); // Потребує повторної верифікації
                }
            }
        }

        user.setUpdatedAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")));
        userRepository.save(user);
        log.info("   ✅ Profile updated successfully");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

    private String hashValue(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Помилка хешування", e);
        }
    }

    /**
     * Оновлення налаштування сповіщень
     */
    @Transactional
    public void updateNotificationsEnabled(String userId, boolean enabled) {
        log.info("🔔 Updating notifications setting for user {}: {}", userId, enabled);
        User user = getUserById(userId);
        user.setNotificationsEnabled(enabled);
        user.setUpdatedAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")));
        userRepository.save(user);
        log.info("   ✅ Notifications setting updated");
    }

    /**
     * Отримати налаштування сповіщень
     */
    public boolean areNotificationsEnabled(String userId) {
        User user = getUserById(userId);
        return user.isNotificationsEnabled();
    }
}
