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
        log.info("📝 UPDATE PROFILE request for user: {}", userId);

        userService.updateProfile(userId, request);

        log.info("✅ Profile updated successfully");
        return ResponseEntity.ok(ApiResponse.success("Профіль оновлено", null));
    }
}
