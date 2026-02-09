package com.zvit.controller;

import com.zvit.dto.response.ApiResponse;
import com.zvit.entity.AdminLog;
import com.zvit.entity.QrSession;
import com.zvit.entity.SystemAdmin;
import com.zvit.entity.User;
import com.zvit.repository.UserRepository;
import com.zvit.service.AdminLogService;
import com.zvit.service.EncryptionService;
import com.zvit.service.QrSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SystemLogController {

    private final AdminLogService adminLogService;
    private final QrSessionService qrSessionService;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    /**
     * Сторінка перегляду логів (через QR сесію)
     */
    @GetMapping("/system/logs")
    public String logsPage(@RequestParam String token, Model model) {
        // Перевіряємо QR сесію та права адміна
        if (!isSystemAdminByQrToken(token)) {
            return "redirect:/";
        }
        model.addAttribute("sessionToken", token);
        return "system-logs";
    }

    /**
     * API: Перевірити чи є користувач QR сесії системним адміністратором
     */
    @GetMapping("/api/web/check-system-admin")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkSystemAdmin(
            @RequestParam String token) {

        try {
            QrSession session = qrSessionService.getAuthorizedSession(token);
            String phone = getUserPhone(session.getUserId());

            boolean isAdmin = phone != null && adminLogService.isSystemAdmin(phone);
            boolean isMaster = phone != null && adminLogService.isMasterAdmin(phone);

            return ResponseEntity.ok(ApiResponse.success("Перевірка виконана", Map.of(
                    "isSystemAdmin", isAdmin,
                    "isMasterAdmin", isMaster
            )));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success("Перевірка виконана", Map.of(
                    "isSystemAdmin", false,
                    "isMasterAdmin", false
            )));
        }
    }

    /**
     * API: Отримати логи з фільтрами (через QR сесію)
     */
    @GetMapping("/api/system/logs")
    @ResponseBody
    public ResponseEntity<ApiResponse<Page<AdminLog>>> getLogs(
            @RequestParam String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) AdminLog.LogLevel level,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (!isSystemAdminByQrToken(token)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Доступ заборонено"));
        }

        Page<AdminLog> logs = adminLogService.getLogs(
                dateFrom, dateTo, userName, phoneNumber, ipAddress, level, method, status, page, size
        );

        return ResponseEntity.ok(ApiResponse.success("Логи отримано", logs));
    }

    /**
     * API: Додати нового системного адміністратора
     */
    @PostMapping("/api/system/admins")
    @ResponseBody
    public ResponseEntity<ApiResponse<SystemAdmin>> addAdmin(
            @RequestParam String token,
            @RequestBody Map<String, String> request) {

        String adminPhone = getPhoneByQrToken(token);
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
            @RequestParam String token,
            @PathVariable String phoneNumber) {

        String adminPhone = getPhoneByQrToken(token);
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
            @RequestParam String token) {

        if (!isSystemAdminByQrToken(token)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Доступ заборонено"));
        }

        List<SystemAdmin> admins = adminLogService.getAllAdmins();
        return ResponseEntity.ok(ApiResponse.success("Список адміністраторів", admins));
    }

    /**
     * API: Видалити логи за фільтром
     */
    @DeleteMapping("/api/system/logs")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteLogs(
            @RequestParam String token,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) AdminLog.LogLevel level,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean deleteAll) {

        String adminPhone = getPhoneByQrToken(token);
        if (adminPhone == null || !adminLogService.isMasterAdmin(adminPhone)) {
            return ResponseEntity.status(403).body(ApiResponse.error("Тільки головний адміністратор може видаляти логи"));
        }

        int deleted;
        if (deleteAll) {
            deleted = adminLogService.deleteAllLogs();
        } else {
            deleted = adminLogService.deleteLogs(dateFrom, dateTo, userName, level, status);
        }

        return ResponseEntity.ok(ApiResponse.success("Логи видалено", Map.of("deleted", deleted)));
    }

    /**
     * Перевірка чи користувач QR сесії є системним адміном
     */
    private boolean isSystemAdminByQrToken(String token) {
        String phone = getPhoneByQrToken(token);
        return phone != null && adminLogService.isSystemAdmin(phone);
    }

    /**
     * Отримати телефон користувача за QR токеном
     */
    private String getPhoneByQrToken(String token) {
        try {
            QrSession session = qrSessionService.getAuthorizedSession(token);
            return getUserPhone(session.getUserId());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Отримати телефон користувача за userId
     */
    private String getUserPhone(String userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    try {
                        return encryptionService.decrypt(user.getPhoneEncrypted());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);
    }
}
