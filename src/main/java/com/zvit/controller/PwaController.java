package com.zvit.controller;

import com.zvit.dto.request.CreateGroupRequest;
import com.zvit.dto.request.JoinGroupRequest;
import com.zvit.dto.request.LoginRequest;
import com.zvit.dto.request.RegisterRequest;
import com.zvit.dto.request.SimpleReportRequest;
import com.zvit.dto.request.UpdateProfileRequest;
import com.zvit.dto.response.ApiResponse;
import com.zvit.dto.response.GroupResponse;
import com.zvit.dto.response.LoginResponse;
import com.zvit.dto.response.RegisterResponse;
import com.zvit.dto.response.ReportResponse;
import com.zvit.service.AuthService;
import com.zvit.service.GroupService;
import com.zvit.service.ReportService;
import com.zvit.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PWA Controller - API без шифрування для Progressive Web App
 * HTTPS забезпечує безпеку передачі даних
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pwa")
@RequiredArgsConstructor
public class PwaController {

    private final GroupService groupService;
    private final ReportService reportService;
    private final AuthService authService;
    private final UserService userService;

    /**
     * PWA Login - без шифрування
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("PWA LOGIN: {}", request.getPhone().length() > 6 ?
            request.getPhone().substring(0, 6) + "***" : "***");
        LoginResponse loginData = authService.login(request);
        log.info("PWA LOGIN successful, userId: {}", loginData.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Вхід успішний", loginData));
    }

    /**
     * PWA Register - без шифрування
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("PWA REGISTER: {}", request.getName());
        RegisterResponse response = authService.register(request);
        log.info("PWA REGISTER successful, userId: {}", response.getUserId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Користувача зареєстровано", response));
    }

    /**
     * Отримати групи користувача (без шифрування)
     */
    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getUserGroups(
            Authentication authentication
    ) {
        String userId = authentication.getName();
        log.info("PWA: Getting groups for user: {}", userId);
        List<GroupResponse> groups = groupService.getUserGroups(userId);
        return ResponseEntity.ok(ApiResponse.success("Групи отримано", groups));
    }

    /**
     * Отримати деталі групи (без шифрування)
     */
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroupById(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        GroupResponse response = groupService.getGroupById(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Групу знайдено", response));
    }

    /**
     * Створити групу (без шифрування)
     */
    @PostMapping("/groups")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        log.info("PWA: Creating group: {} by user: {}", request.getExternalName(), userId);
        GroupResponse response = groupService.createGroup(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Групу створено", response));
    }

    /**
     * Приєднатися до групи (без шифрування)
     */
    @PostMapping("/groups/join")
    public ResponseEntity<ApiResponse<GroupResponse>> joinGroup(
            @Valid @RequestBody JoinGroupRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        log.info("PWA: Joining group with code: {} by user: {}", request.getAccessCode(), userId);
        GroupResponse response = groupService.joinGroupByAccessCode(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Заявку надіслано", response));
    }

    /**
     * Надіслати простий звіт (без шифрування)
     */
    @PostMapping("/groups/{groupId}/reports/simple")
    public ResponseEntity<ApiResponse<ReportResponse>> submitSimpleReport(
            @PathVariable String groupId,
            @Valid @RequestBody SimpleReportRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        // Ensure groupId from path matches request
        request.setGroupId(groupId);
        log.info("PWA: Submitting simple report to group: {} by user: {}", groupId, userId);
        ReportResponse response = reportService.createSimpleReport(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Звіт надіслано", response));
    }

    /**
     * Отримати звіти групи (без шифрування)
     * Адміни бачать всі звіти, звичайні члени - тільки свої
     */
    @GetMapping("/groups/{groupId}/reports")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getGroupReports(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports;

        try {
            // Спробуємо отримати всі звіти (працює тільки для адмінів)
            reports = reportService.getAllGroupReports(groupId, userId);
        } catch (RuntimeException e) {
            // Якщо не адмін - отримуємо тільки свої звіти
            reports = reportService.getMyReports(groupId, userId);
        }

        return ResponseEntity.ok(ApiResponse.success("Звіти отримано", reports));
    }

    /**
     * Отримати всі звіти користувача (без шифрування)
     */
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getUserReports(
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getAllMyReports(userId);
        return ResponseEntity.ok(ApiResponse.success("Звіти отримано", reports));
    }

    /**
     * Вийти з групи (без шифрування)
     */
    @DeleteMapping("/groups/{groupId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        log.info("PWA: User {} leaving group: {}", userId, groupId);
        groupService.leaveGroup(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Ви вийшли з групи", null));
    }

    /**
     * Оновити профіль (без шифрування)
     */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            @RequestBody UpdateProfileRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        log.info("PWA: Updating profile for user: {}", userId);
        userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Профіль оновлено", null));
    }
}
