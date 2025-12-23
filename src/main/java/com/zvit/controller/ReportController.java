package com.zvit.controller;

import com.zvit.dto.request.ExtendedReportRequest;
import com.zvit.dto.request.SimpleReportRequest;
import com.zvit.dto.request.UrgentReportRequest;
import com.zvit.dto.response.EncryptedData;
import com.zvit.dto.response.ReportResponse;
import com.zvit.dto.response.UserStatusResponse;
import com.zvit.service.ReportService;
import com.zvit.service.ResponseEncryptionService;
import com.zvit.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ReportController v1.4
 * - Використовує JWT Authentication
 * - userId береться з SecurityContext
 * - Чутливі дані шифруються AES
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ResponseEncryptionService encryptionService;

    @PostMapping("/simple")
    public ResponseEntity<ApiResponse<ReportResponse>> createSimpleReport(
            @Valid @RequestBody SimpleReportRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        ReportResponse response = reportService.createSimpleReport(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Звіт створено", response));
    }

    @PostMapping("/extended")
    public ResponseEntity<ApiResponse<ReportResponse>> createExtendedReport(
            @Valid @RequestBody ExtendedReportRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        ReportResponse response = reportService.createExtendedReport(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Розгорнутий звіт створено", response));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<EncryptedData>> getAllMyReports(
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getAllMyReports(userId);
        String encryptedPayload = encryptionService.encryptObject(reports);
        return ResponseEntity.ok(ApiResponse.success("Всі звіти отримано", EncryptedData.of(encryptedPayload)));
    }

    @GetMapping("/my/{groupId}")
    public ResponseEntity<ApiResponse<EncryptedData>> getMyReports(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getMyReports(groupId, userId);
        String encryptedPayload = encryptionService.encryptObject(reports);
        return ResponseEntity.ok(ApiResponse.success("Звіти отримано", EncryptedData.of(encryptedPayload)));
    }

    @GetMapping("/my/{groupId}/last")
    public ResponseEntity<ApiResponse<EncryptedData>> getMyLastReport(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        ReportResponse report = reportService.getMyLastReport(groupId, userId);
        String encryptedPayload = encryptionService.encryptObject(report);
        return ResponseEntity.ok(ApiResponse.success("Останній звіт отримано", EncryptedData.of(encryptedPayload)));
    }

    @GetMapping("/statuses/{groupId}")
    public ResponseEntity<ApiResponse<EncryptedData>> getGroupStatuses(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<UserStatusResponse> statuses = reportService.getGroupStatuses(groupId, userId);
        String encryptedPayload = encryptionService.encryptObject(statuses);
        return ResponseEntity.ok(ApiResponse.success("Статуси отримано", EncryptedData.of(encryptedPayload)));
    }

    @PostMapping("/urgent")
    public ResponseEntity<ApiResponse<Integer>> createUrgentRequest(
            @Valid @RequestBody UrgentReportRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        int sentCount = reportService.createUrgentRequest(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Терміновий запит створено. Сповіщень відправлено: " + sentCount, sentCount));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<EncryptedData>> getAllGroupReports(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getAllGroupReports(groupId, userId);
        String encryptedPayload = encryptionService.encryptObject(reports);
        return ResponseEntity.ok(ApiResponse.success("Всі звіти отримано", EncryptedData.of(encryptedPayload)));
    }

    @GetMapping("/group/{groupId}/user/{targetUserId}")
    public ResponseEntity<ApiResponse<EncryptedData>> getUserReportsInGroup(
            @PathVariable String groupId,
            @PathVariable String targetUserId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getUserReportsInGroup(groupId, targetUserId, userId);
        String encryptedPayload = encryptionService.encryptObject(reports);
        return ResponseEntity.ok(ApiResponse.success("Звіти користувача отримано", EncryptedData.of(encryptedPayload)));
    }
}
