package com.zvit.controller;

import com.zvit.dto.response.QrSessionResponse;
import com.zvit.entity.QrSession;
import com.zvit.service.QrSessionService;
import com.zvit.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebViewController {

    private final QrSessionService qrSessionService;
    private final ReportService reportService;

    // Rate limiting: IP -> timestamp останнього доступу
    private final Map<String, Long> rateLimitCache = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_MS = 5 * 60 * 1000; // 5 хвилин

    /**
     * GET / - головна сторінка (landing page)
     */
    @GetMapping("/")
    public String landingPage() {
        return "landing";
    }

    /**
     * GET /privacy-policy - Політика конфіденційності
     */
    @GetMapping("/privacy-policy")
    public String privacyPolicy() {
        return "privacy-policy";
    }

    /**
     * GET /terms - Умови використання
     */
    @GetMapping("/terms")
    public String termsOfService() {
        return "terms";
    }

    /**
     * GET /delete-account - Інструкції для видалення облікового запису
     */
    @GetMapping("/delete-account")
    public String deleteAccount() {
        return "delete-account";
    }

    /**
     * GET /guide - Інструкція користувача
     */
    @GetMapping("/guide")
    public String userGuide() {
        return "guide";
    }

    /**
     * GET /app - PWA додаток
     */
    @GetMapping("/app")
    public String pwaApp() {
        return "app";
    }

    /**
     * GET /feature-graphic - Банер для Google Play (1024x500)
     */
    @GetMapping("/feature-graphic")
    public String featureGraphic() {
        return "feature-graphic";
    }

    /**
     * GET /app-icon - Іконка для Google Play (512x512)
     */
    @GetMapping("/app-icon")
    public String appIcon() {
        return "app-icon";
    }

    /**
     * GET /admin/qr-access
     * Сторінка для адміністраторів з rate limiting (1 раз на 5 хв)
     */
    @GetMapping("/admin/qr-access")
    public String adminQrAccess(HttpServletRequest request, Model model) {
        String clientIp = getClientIp(request);

        // Перевірка rate limit
        Long lastAccess = rateLimitCache.get(clientIp);
        long now = System.currentTimeMillis();

        if (lastAccess != null) {
            long timeSinceLastAccess = now - lastAccess;
            if (timeSinceLastAccess < RATE_LIMIT_MS) {
                long remainingSeconds = (RATE_LIMIT_MS - timeSinceLastAccess) / 1000;
                model.addAttribute("rateLimited", true);
                model.addAttribute("remainingSeconds", remainingSeconds);
                return "rate-limited";
            }
        }

        // Оновлюємо час останнього доступу
        rateLimitCache.put(clientIp, now);

        // Очищаємо старі записи (старші за 10 хвилин)
        cleanupOldEntries();

        // Створюємо QR сесію
        QrSessionResponse session = qrSessionService.createSession();
        model.addAttribute("sessionToken", session.getSessionToken());
        model.addAttribute("qrUrl", session.getQrUrl());
        model.addAttribute("expiresIn", session.getExpiresIn());
        return "qr-auth";
    }

    /**
     * GET /web/start
     * Початкова сторінка - створює QR сесію та показує QR код
     */
    @GetMapping("/web/start")
    public String startWebSession(Model model) {
        QrSessionResponse session = qrSessionService.createSession();
        model.addAttribute("sessionToken", session.getSessionToken());
        model.addAttribute("qrUrl", session.getQrUrl());
        model.addAttribute("expiresIn", session.getExpiresIn());
        return "qr-auth"; // qr-auth.html template
    }

    /**
     * GET /web/auth?token=xxx
     * Сторінка для редиректу з QR коду (може бути відкрита на мобільному)
     * Якщо відкрито на мобільному з додатком - додаток перехопить URL
     * Якщо відкрито в браузері - покаже інструкцію
     */
    @GetMapping("/web/auth")
    public String authRedirect(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "qr-redirect"; // qr-redirect.html template
    }

    /**
     * GET /web/reports?token=xxx
     * Сторінка з відображенням звітів (після авторизації)
     */
    @GetMapping("/web/reports")
    public String showReports(@RequestParam String token, Model model) {
        QrSession session = qrSessionService.getAuthorizedSession(token);

        // Передаємо дані в модель для відображення
        model.addAttribute("sessionToken", token);
        model.addAttribute("groupId", session.getGroupId());

        return "web-reports"; // web-reports.html template
    }

    /**
     * Отримує IP клієнта з урахуванням проксі
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Очищає записи старші за 10 хвилин
     */
    private void cleanupOldEntries() {
        long now = System.currentTimeMillis();
        long maxAge = 10 * 60 * 1000; // 10 хвилин
        rateLimitCache.entrySet().removeIf(entry -> (now - entry.getValue()) > maxAge);
    }
}
