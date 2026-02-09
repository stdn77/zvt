package com.zvit.service;

import com.zvit.dto.request.AuthorizeQrRequest;
import com.zvit.dto.response.QrSessionResponse;
import com.zvit.entity.Group;
import com.zvit.entity.QrSession;
import com.zvit.entity.User;
import com.zvit.repository.GroupMemberRepository;
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
import java.time.ZoneId;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrSessionService {

    private final QrSessionRepository qrSessionRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final int QR_EXPIRY_MINUTES = 10; // QR код дійсний 10 хвилин
    private static final int SESSION_EXPIRY_HOURS = 24; // Авторизована сесія дійсна 24 години
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");

    /**
     * Створити нову QR сесію
     */
    @Transactional
    public QrSessionResponse createSession() {
        // Видалити прострочені сесії перед створенням нової
        qrSessionRepository.deleteExpiredSessions(LocalDateTime.now(KYIV_ZONE));

        // Згенерувати унікальний токен
        String sessionToken = generateSecureToken();

        // Створити нову сесію
        LocalDateTime now = LocalDateTime.now(KYIV_ZONE);
        QrSession session = QrSession.builder()
                .sessionToken(sessionToken)
                .isAuthorized(false)
                .createdAt(now)
                .expiresAt(now.plusMinutes(QR_EXPIRY_MINUTES))
                .build();

        qrSessionRepository.save(session);

        // Створити URL для QR коду
        String qrUrl = baseUrl + "/web/auth?token=" + sessionToken;

        long expiresIn = Duration.between(now, session.getExpiresAt()).getSeconds();

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
    public void authorizeSession(AuthorizeQrRequest request, String userId) {
        // Знайти користувача
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Користувача не знайдено"));

        // Знайти сесію
        QrSession session = qrSessionRepository.findBySessionToken(request.getSessionToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR сесію не знайдено"));

        LocalDateTime now = LocalDateTime.now(KYIV_ZONE);

        // Перевірити чи сесія не прострочена
        if (session.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "QR код прострочений");
        }

        // Перевірити чи сесія вже авторизована
        if (session.getIsAuthorized()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Сесія вже авторизована");
        }

        // Перевірити чи користувач є адміном групи
        String groupId = request.getGroupId();
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Групу не знайдено"));

        // Перевірка через GroupMemberRepository (ADMIN або MODER)
        if (!groupMemberRepository.isUserAdminOrModerOfGroup(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Тільки адміністратор або модератор групи може авторизувати веб сесію");
        }

        // Авторизувати сесію
        session.setUserId(userId);
        session.setGroupId(request.getGroupId());
        session.setIsAuthorized(true);
        session.setAuthorizedAt(now);
        session.setExpiresAt(now.plusHours(SESSION_EXPIRY_HOURS)); // Продовжити на 24 години
        session.setLastActivityAt(now);

        qrSessionRepository.save(session);
    }

    /**
     * Перевірити статус сесії (для polling з веб сторінки)
     */
    public QrSessionResponse getSessionStatus(String sessionToken) {
        QrSession session = qrSessionRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR сесію не знайдено"));

        LocalDateTime now = LocalDateTime.now(KYIV_ZONE);

        // Перевірити чи сесія не прострочена
        if (session.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Сесія прострочена");
        }

        // Оновити останню активність
        if (session.getIsAuthorized()) {
            session.setLastActivityAt(now);
            qrSessionRepository.save(session);
        }

        long expiresIn = Duration.between(now, session.getExpiresAt()).getSeconds();

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

        LocalDateTime now = LocalDateTime.now(KYIV_ZONE);

        if (session.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Сесія прострочена");
        }

        // Оновити останню активність
        session.setLastActivityAt(now);
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
        LocalDateTime now = LocalDateTime.now(KYIV_ZONE);
        qrSessionRepository.deleteExpiredSessions(now);
    }
}
