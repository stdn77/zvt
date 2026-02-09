package com.zvit.controller;

import com.zvit.dto.response.ApiResponse;
import com.zvit.entity.AdminLog;
import com.zvit.entity.SystemAdmin;
import com.zvit.entity.User;
import com.zvit.repository.UserRepository;
import com.zvit.security.JwtService;
import com.zvit.service.AdminLogService;
import com.zvit.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SystemLogController {

    private final AdminLogService adminLogService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    /**
     * Сторінка перегляду логів (потребує авторизації)
     */
    @GetMapping("/system/logs")
    public String logsPage(@RequestParam String token) {
        // Перевіряємо токен
        if (!isSystemAdmin(token)) {
            return "redirect:/error";
        }
        return "system-logs";
    }

    /**
     * API: Отримати логи з фільтрами
     */
    @GetMapping("/api/system/logs")
    @ResponseBody
    public ResponseEntity<ApiResponse<Page<AdminLog>>> getLogs(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) AdminLog.LogLevel level,
            @RequestParam(required = false) String method,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (!isSystemAdminByAuth(authHeader)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Доступ заборонено"));
        }

        Page<AdminLog> logs = adminLogService.getLogs(
                dateFrom, dateTo, userName, phoneNumber, ipAddress, level, method, page, size
        );

        return ResponseEntity.ok(ApiResponse.success("Логи отримано", logs));
    }

    /**
     * API: Додати нового системного адміністратора
     */
    @PostMapping("/api/system/admins")
    @ResponseBody
    public ResponseEntity<ApiResponse<SystemAdmin>> addAdmin(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {

        String adminPhone = getPhoneFromAuth(authHeader);
        if (adminPhone == null || !adminLogService.isSystemAdmin(adminPhone)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Доступ заборонено"));
        }

        String phoneNumber = request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Номер телефону обов'язковий"));
        }

        try {
            SystemAdmin newAdmin = adminLogService.addAdmin(phoneNumber, adminPhone);
            return ResponseEntity.ok(ApiResponse.success("Адміністратора додано", newAdmin));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * API: Видалити системного адміністратора
     */
    @DeleteMapping("/api/system/admins/{phoneNumber}")
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> removeAdmin(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String phoneNumber) {

        String adminPhone = getPhoneFromAuth(authHeader);
        if (adminPhone == null || !adminLogService.isMasterAdmin(adminPhone)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Тільки головний адміністратор може видаляти інших"));
        }

        try {
            adminLogService.removeAdmin(phoneNumber);
            return ResponseEntity.ok(ApiResponse.success("Адміністратора видалено", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * API: Отримати список всіх системних адміністраторів
     */
    @GetMapping("/api/system/admins")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<SystemAdmin>>> getAdmins(
            @RequestHeader("Authorization") String authHeader) {

        if (!isSystemAdminByAuth(authHeader)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Доступ заборонено"));
        }

        List<SystemAdmin> admins = adminLogService.getAllAdmins();
        return ResponseEntity.ok(ApiResponse.success("Список адміністраторів", admins));
    }

    /**
     * API: Перевірити чи є користувач системним адміністратором
     */
    @GetMapping("/api/system/check-admin")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAdmin(
            @RequestHeader("Authorization") String authHeader) {

        String phone = getPhoneFromAuth(authHeader);
        boolean isAdmin = phone != null && adminLogService.isSystemAdmin(phone);
        boolean isMaster = phone != null && adminLogService.isMasterAdmin(phone);

        return ResponseEntity.ok(ApiResponse.success("Перевірка виконана", Map.of(
                "isSystemAdmin", isAdmin,
                "isMasterAdmin", isMaster
        )));
    }

    private boolean isSystemAdmin(String token) {
        try {
            String userId = jwtService.extractUserId(token);
            return userRepository.findById(userId)
                    .map(user -> {
                        try {
                            String phone = encryptionService.decrypt(user.getPhoneEncrypted());
                            return adminLogService.isSystemAdmin(phone);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSystemAdminByAuth(String authHeader) {
        String phone = getPhoneFromAuth(authHeader);
        return phone != null && adminLogService.isSystemAdmin(phone);
    }

    private String getPhoneFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authHeader.substring(7);
            String userId = jwtService.extractUserId(token);
            return userRepository.findById(userId)
                    .map(user -> {
                        try {
                            return encryptionService.decrypt(user.getPhoneEncrypted());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
