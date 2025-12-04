package com.zvit.controller;

import com.zvit.dto.request.AuthorizeQrRequest;
import com.zvit.dto.response.ApiResponse;
import com.zvit.dto.response.QrSessionResponse;
import com.zvit.service.QrSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class QrSessionController {

    private final QrSessionService qrSessionService;

    /**
     * POST /api/web-auth/authorize
     * Авторизувати QR сесію (викликається з мобільного додатку після сканування QR)
     */
    @PostMapping("/web-auth/authorize")
    public ResponseEntity<ApiResponse<Void>> authorizeSession(
            @Valid @RequestBody AuthorizeQrRequest request,
            Authentication authentication) {
        String userId = authentication.getName();
        qrSessionService.authorizeSession(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Веб сесію авторизовано", null));
    }

    /**
     * GET /api/web-session/status/{sessionToken}
     * Перевірити статус сесії (для polling з веб сторінки)
     */
    @GetMapping("/web-session/status/{sessionToken}")
    public ResponseEntity<QrSessionResponse> getSessionStatus(@PathVariable String sessionToken) {
        QrSessionResponse response = qrSessionService.getSessionStatus(sessionToken);
        return ResponseEntity.ok(response);
    }
}
