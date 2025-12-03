package com.zvit.service;

import com.zvit.dto.request.AuthorizeQrRequest;
import com.zvit.dto.response.QrSessionResponse;
import com.zvit.entity.Group;
import com.zvit.entity.QrSession;
import com.zvit.entity.User;
import com.zvit.repository.GroupRepository;
import com.zvit.repository.QrSessionRepository;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrSessionService {

    private final QrSessionRepository qrSessionRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final int QR_EXPIRY_MINUTES = 5; // QR код дійсний 5 хвилин
    private static final int SESSION_EXPIRY_HOURS = 24; // Авторизована сесія дійсна 24 години

    /**
     * Створити нову QR сесію
     */
    @Transactional
    public QrSessionResponse createSession() {
        // Видалити прострочені сесії перед створенням нової
        qrSessionRepository.deleteExpiredSessions(LocalDateTime.now());

        // Згенерувати унікальний токен
        String sessionToken = generateSecureToken();

        // Створити нову сесію
        QrSession session = QrSession.builder()
                .sessionToken(sessionToken)
                .isAuthorized(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(QR_EXPIRY_MINUTES))
                .build();

        qrSessionRepository.save(session);

        log.info("Created new QR session: {}", sessionToken);

        // Створити URL для QR коду
        String qrUrl = baseUrl + "/web/auth?token=" + sessionToken;

        long expiresIn = Duration.between(LocalDateTime.now(), session.getExpiresAt()).getSeconds();

        return QrSessionResponse.builder()
                .sessionToken(sessionToken)
                .qrUrl(qrUrl)
                .isAuthorized(false)
                .expiresIn(expiresIn)
                .build();
    }

    /**
     * Авторизувати QR сесію (викликається з мобільного додатку)
     */
    @Transactional
    public void authorizeSession(AuthorizeQrRequest request, String userEmail) {
        // Знайти користувача
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Користувача не знайдено"));

        // Знайти сесію
        QrSession session = qrSessionRepository.findBySessionToken(request.getSessionToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR сесію не знайдено"));

        // Перевірити чи сесія не прострочена
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "QR код прострочений");
        }

        // Перевірити чи сесія вже авторизована
        if (session.getIsAuthorized()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сесія вже авторизована");
        }

        // Перевірити чи користувач є адміном групи
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Групу не знайдено"));

        if (!group.getAdminUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Тільки адміністратор групи може авторизувати веб сесію");
        }

        // Авторизувати сесію
        session.setUserId(user.getId());
        session.setGroupId(request.getGroupId());
        session.setIsAuthorized(true);
        session.setAuthorizedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(SESSION_EXPIRY_HOURS)); // Продовжити на 24 години
        session.setLastActivityAt(LocalDateTime.now());

        qrSessionRepository.save(session);

        log.info("QR session {} authorized by user {} for group {}",
                 session.getSessionToken(), user.getEmail(), group.getExternalName());
    }

    /**
     * Перевірити статус сесії (для polling з веб сторінки)
     */
    public QrSessionResponse getSessionStatus(String sessionToken) {
        QrSession session = qrSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR сесію не знайдено"));

        // Перевірити чи сесія не прострочена
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Сесія прострочена");
        }

        // Оновити останню активність
        if (session.getIsAuthorized()) {
            session.setLastActivityAt(LocalDateTime.now());
            qrSessionRepository.save(session);
        }

        long expiresIn = Duration.between(LocalDateTime.now(), session.getExpiresAt()).getSeconds();

        return QrSessionResponse.builder()
                .sessionToken(session.getSessionToken())
                .isAuthorized(session.getIsAuthorized())
                .expiresIn(expiresIn)
                .build();
    }

    /**
     * Отримати сесію за токеном (для доступу до звітів)
     */
    public QrSession getAuthorizedSession(String sessionToken) {
        QrSession session = qrSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сесію не знайдено"));

        if (!session.getIsAuthorized()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Сесія не авторизована");
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Сесія прострочена");
        }

        // Оновити останню активність
        session.setLastActivityAt(LocalDateTime.now());
        qrSessionRepository.save(session);

        return session;
    }

    /**
     * Згенерувати безпечний токен
     */
    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Scheduled task для очищення прострочених сесій (раз на годину)
     */
    @Scheduled(fixedRate = 3600000) // 1 година
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        qrSessionRepository.deleteExpiredSessions(now);
        log.info("Cleaned up expired QR sessions");
    }
}
