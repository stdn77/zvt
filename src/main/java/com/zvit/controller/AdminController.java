package com.zvit.controller;

import com.zvit.dto.response.GroupMemberResponse;
import com.zvit.dto.response.GroupStatusesResponse;
import com.zvit.dto.response.UserStatusResponse;
import com.zvit.service.AdminService;
import com.zvit.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * Згенерувати QR-код для групи
     * Повертає PNG image з QR-кодом
     */
    @GetMapping(value = "/qr/{groupId}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQRCode(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        byte[] qrCode = adminService.generateQRCode(groupId, userId);
        return ResponseEntity.ok(qrCode);
    }

    /**
     * Отримати токен для доступу до дашборду
     * Цей токен буде в QR-коді
     */
    @GetMapping("/dashboard-token/{groupId}")
    public ResponseEntity<ApiResponse<String>> getDashboardToken(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        String token = adminService.generateDashboardToken(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Токен створено", token));
    }

    /**
     * Відкрити дашборд по токену з QR
     * БЕЗ аутентифікації (публічний endpoint)
     */
    @GetMapping("/dashboard/{token}")
    public ResponseEntity<ApiResponse<GroupStatusesResponse>> getDashboard(
            @PathVariable String token
    ) {
        GroupStatusesResponse statuses = adminService.getDashboardByToken(token);
        return ResponseEntity.ok(ApiResponse.success("Дашборд отримано", statuses));
    }

    /**
     * Деталі користувача
     */
    @GetMapping("/member/{userId}/details")
    public ResponseEntity<ApiResponse<GroupMemberResponse>> getMemberDetails(
            @PathVariable String userId,
            @RequestParam String token
    ) {
        GroupMemberResponse member = adminService.getMemberDetails(userId, token);
        return ResponseEntity.ok(ApiResponse.success("Деталі отримано", member));
    }
}