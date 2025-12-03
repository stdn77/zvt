package com.zvit.controller;

import com.zvit.dto.response.QrSessionResponse;
import com.zvit.entity.QrSession;
import com.zvit.service.QrSessionService;
import com.zvit.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/web")
@RequiredArgsConstructor
@Slf4j
public class WebViewController {

    private final QrSessionService qrSessionService;
    private final ReportService reportService;

    /**
     * GET /web/start
     * Початкова сторінка - створює QR сесію та показує QR код
     */
    @GetMapping("/start")
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
    @GetMapping("/auth")
    public String authRedirect(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "qr-redirect"; // qr-redirect.html template
    }

    /**
     * GET /web/reports?token=xxx
     * Сторінка з відображенням звітів (після авторизації)
     */
    @GetMapping("/reports")
    public String showReports(@RequestParam String token, Model model) {
        QrSession session = qrSessionService.getAuthorizedSession(token);

        // Передаємо дані в модель для відображення
        model.addAttribute("sessionToken", token);
        model.addAttribute("groupId", session.getGroupId());

        return "web-reports"; // web-reports.html template
    }
}
