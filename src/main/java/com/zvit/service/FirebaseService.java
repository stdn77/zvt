package com.zvit.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FirebaseService {

    /**
     * Перевіряє чи Firebase ініціалізовано
     */
    public boolean isFirebaseInitialized() {
        return !FirebaseApp.getApps().isEmpty();
    }

    /**
     * Відправляє Push-сповіщення на один пристрій
     */
    public boolean sendPushNotification(String fcmToken, String title, String body) {
        return sendPushNotification(fcmToken, title, body, null);
    }

    /**
     * Відправляє Push-сповіщення на один пристрій з додатковими даними
     */
    public boolean sendPushNotification(String fcmToken, String title, String body, Map<String, String> data) {
        if (!isFirebaseInitialized()) {
            log.warn("Firebase not initialized - push notification skipped");
            return false;
        }

        if (fcmToken == null || fcmToken.isEmpty()) {
            log.debug("FCM token is empty - skipping notification");
            return false;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    // Android specific settings
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .setClickAction("OPEN_REPORTS")
                                    .build())
                            .build());

            // Додаємо data payload якщо є
            if (data != null && !data.isEmpty()) {
                messageBuilder.putAllData(data);
            }

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.debug("Push notification sent successfully: {}", response);
            return true;

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send push notification: {}", e.getMessage());
            // Якщо токен невалідний, потрібно видалити його з бази
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("Invalid FCM token detected, should be removed from database");
            }
            return false;
        }
    }

    /**
     * Відправляє Push-сповіщення на декілька пристроїв
     */
    public int sendPushNotificationToMultiple(List<String> fcmTokens, String title, String body, Map<String, String> data) {
        if (!isFirebaseInitialized()) {
            log.warn("Firebase not initialized - push notifications skipped");
            return 0;
        }

        // Фільтруємо пусті токени
        List<String> validTokens = fcmTokens.stream()
                .filter(token -> token != null && !token.isEmpty())
                .toList();

        if (validTokens.isEmpty()) {
            log.debug("No valid FCM tokens - skipping notifications");
            return 0;
        }

        try {
            // Створюємо список повідомлень
            List<Message> messages = new ArrayList<>();
            for (String token : validTokens) {
                Message.Builder messageBuilder = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        .setSound("default")
                                        .setClickAction("OPEN_REPORTS")
                                        .build())
                                .build());

                if (data != null && !data.isEmpty()) {
                    messageBuilder.putAllData(data);
                }

                messages.add(messageBuilder.build());
            }

            // Відправляємо пакетом (до 500 за раз)
            BatchResponse response = FirebaseMessaging.getInstance().sendEach(messages);

            int successCount = response.getSuccessCount();
            int failureCount = response.getFailureCount();

            log.info("Push notifications sent: {} success, {} failed", successCount, failureCount);

            // Логуємо помилки
            if (failureCount > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    SendResponse sendResponse = responses.get(i);
                    if (!sendResponse.isSuccessful()) {
                        FirebaseMessagingException exception = sendResponse.getException();
                        if (exception != null) {
                            log.debug("Failed to send to token {}: {}", i, exception.getMessage());
                        }
                    }
                }
            }

            return successCount;

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send batch push notifications: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Верифікує Firebase ID токен та повертає номер телефону
     * @param idToken Firebase ID токен від клієнта
     * @return номер телефону або null якщо токен невалідний
     */
    public String verifyIdTokenAndGetPhone(String idToken) {
        if (!isFirebaseInitialized()) {
            log.warn("Firebase not initialized - cannot verify ID token");
            return null;
        }

        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            String phoneNumber = decodedToken.getClaims().get("phone_number") != null
                    ? decodedToken.getClaims().get("phone_number").toString()
                    : null;

            if (phoneNumber != null) {
                log.info("Firebase ID token verified for phone: {}", phoneNumber);
            } else {
                log.warn("Firebase ID token valid but no phone_number claim found");
            }

            return phoneNumber;
        } catch (FirebaseAuthException e) {
            log.error("Failed to verify Firebase ID token: {}", e.getMessage());
            return null;
        }
    }
}
