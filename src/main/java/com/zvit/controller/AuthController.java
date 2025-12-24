package com.zvit.controller;

import com.zvit.dto.request.FcmTokenRequest;
import com.zvit.dto.request.LoginRequest;
import com.zvit.dto.request.RegisterRequest;
import com.zvit.dto.response.EncryptedData;
import com.zvit.dto.response.LoginResponse;
import com.zvit.dto.response.PublicKeyResponse;
import com.zvit.dto.response.RegisterResponse;
import com.zvit.service.AuthService;
import com.zvit.service.RSAKeyService;
import com.zvit.service.ResponseEncryptionService;
import com.zvit.service.UserService;
import com.zvit.dto.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RSAKeyService rsaKeyService;
    private final ResponseEncryptionService encryptionService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("📝 REGISTER request received");
        log.info("   Phone (encrypted?): {} (length: {})",
            request.getPhone().length() > 50 ? request.getPhone().substring(0, 50) + "..." : request.getPhone(),
            request.getPhone().length());
        log.info("   Name: {}", request.getName());
        log.info("   Email: {}", request.getEmail() != null ? "provided" : "null");

        RegisterResponse response = authService.register(request);
        log.info("✅ REGISTER successful, userId: {}", response.getUserId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Користувача зареєстровано", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<EncryptedData>> login(@Valid @RequestBody LoginRequest request) {
        log.info("🔐 LOGIN request received");
        log.info("   Phone (encrypted?): {} (length: {})",
            request.getPhone().length() > 50 ? request.getPhone().substring(0, 50) + "..." : request.getPhone(),
            request.getPhone().length());
        log.info("   Password length: {}", request.getPassword().length());

        LoginResponse loginData = authService.login(request);
        log.info("✅ LOGIN successful, userId: {}", loginData.getUserId());

        // Шифруємо відповідь AES
        String encryptedPayload = encryptionService.encryptObject(loginData);
        String encryptionKey = encryptionService.getEncryptionKeyBase64();

        EncryptedData response = EncryptedData.ofWithKey(encryptedPayload, encryptionKey);
        return ResponseEntity.ok(ApiResponse.success("Вхід успішний", response));
    }

    @GetMapping("/test")
    public ResponseEntity<ApiResponse<String>> test() {
        String message = authService.test();
        return ResponseEntity.ok(ApiResponse.success("Test OK", message));
    }

    /**
     * Повертає публічний ключ RSA для шифрування чутливих даних
     */
    @GetMapping("/public-key")
    public ResponseEntity<ApiResponse<PublicKeyResponse>> getPublicKey() {
        PublicKeyResponse response = PublicKeyResponse.builder()
                .publicKey(rsaKeyService.getPublicKeyBase64())
                .algorithm("RSA")
                .keySize(2048)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Публічний ключ", response));
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