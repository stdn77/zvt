package com.zvit.controller;

import com.zvit.dto.request.FcmTokenRequest;
import com.zvit.dto.request.LoginRequest;
import com.zvit.dto.request.RegisterRequest;
import com.zvit.dto.response.LoginResponse;
import com.zvit.dto.response.RegisterResponse;
import com.zvit.service.AuthService;
import com.zvit.service.UserService;
import com.zvit.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Користувача зареєстровано", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Вхід успішний", response));
    }

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<String>> test() {
        String message = authService.test();
        return ResponseEntity.ok(ApiResponse.success("Test OK", message));
    }

    /**
     * Зберігає FCM токен для Push-сповіщень
     */
    @PostMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @Valid @RequestBody FcmTokenRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        userService.updateFcmToken(userId, request.getFcmToken());
        return ResponseEntity.ok(ApiResponse.success("FCM токен збережено", null));
    }

    /**
     * Видаляє FCM токен (при виході з акаунту)
     */
    @DeleteMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> clearFcmToken(Authentication authentication) {
        String userId = authentication.getName();
        userService.clearFcmToken(userId);
        return ResponseEntity.ok(ApiResponse.success("FCM токен видалено", null));
    }
}