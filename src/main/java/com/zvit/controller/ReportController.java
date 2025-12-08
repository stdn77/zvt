package com.zvit.controller;

import com.zvit.dto.request.ExtendedReportRequest;
import com.zvit.dto.request.SimpleReportRequest;
import com.zvit.dto.request.UrgentReportRequest;
import com.zvit.dto.response.ReportResponse;
import com.zvit.dto.response.UserStatusResponse;
import com.zvit.service.ReportService;
import com.zvit.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ReportController v1.3
 * - Використовує JWT Authentication
 * - userId береться з SecurityContext
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

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
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getAllMyReports(
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getAllMyReports(userId);
        return ResponseEntity.ok(ApiResponse.success("Всі звіти отримано", reports));
    }

    @GetMapping("/my/{groupId}")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getMyReports(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getMyReports(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Звіти отримано", reports));
    }

    @GetMapping("/my/{groupId}/last")
    public ResponseEntity<ApiResponse<ReportResponse>> getMyLastReport(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        ReportResponse report = reportService.getMyLastReport(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Останній звіт отримано", report));
    }

    @GetMapping("/statuses/{groupId}")
    public ResponseEntity<ApiResponse<List<UserStatusResponse>>> getGroupStatuses(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<UserStatusResponse> statuses = reportService.getGroupStatuses(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Статуси отримано", statuses));
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
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getAllGroupReports(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getAllGroupReports(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Всі звіти отримано", reports));
    }

    @GetMapping("/group/{groupId}/user/{targetUserId}")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> getUserReportsInGroup(
            @PathVariable String groupId,
            @PathVariable String targetUserId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<ReportResponse> reports = reportService.getUserReportsInGroup(groupId, targetUserId, userId);
        return ResponseEntity.ok(ApiResponse.success("Звіти користувача отримано", reports));
    }
}
