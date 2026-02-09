package com.zvit.controller;

import com.zvit.dto.request.UpdateProfileRequest;
import com.zvit.dto.response.ApiResponse;
import com.zvit.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Оновлення профілю користувача (ім'я та/або email)
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Профіль оновлено", null));
    }

    /**
     * Оновлення налаштування сповіщень
     */
    @PutMapping("/notifications")
    public ResponseEntity<ApiResponse<Void>> updateNotifications(
            @RequestBody java.util.Map<String, Boolean> request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        Boolean enabled = request.get("enabled");

        if (enabled == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Параметр 'enabled' є обов'язковим"));
        }

        userService.updateNotificationsEnabled(userId, enabled);

        String message = enabled ? "Сповіщення увімкнено" : "Сповіщення вимкнено";
        return ResponseEntity.ok(ApiResponse.success(message, null));
    }

    /**
     * Отримання налаштування сповіщень
     */
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<java.util.Map<String, Boolean>>> getNotifications(
            Authentication authentication
    ) {
        String userId = authentication.getName();
        boolean enabled = userService.areNotificationsEnabled(userId);

        java.util.Map<String, Boolean> result = java.util.Map.of("enabled", enabled);
        return ResponseEntity.ok(ApiResponse.success(null, result));
    }
}
