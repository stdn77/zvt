package com.zvit.controller;

import com.zvit.dto.response.ReportResponse;
import com.zvit.dto.response.UserStatusResponse;
import com.zvit.entity.Group;
import com.zvit.entity.QrSession;
import com.zvit.repository.GroupRepository;
import com.zvit.service.QrSessionService;
import com.zvit.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Контролер для веб доступу до звітів через QR session token
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
@Slf4j
public class WebReportController {

    private final QrSessionService qrSessionService;
    private final ReportService reportService;
    private final GroupRepository groupRepository;

    /**
     * GET /api/web/reports/{groupId}/statuses
     * Отримати статуси учасників групи (для веб інтерфейсу)
     * Використовує session token замість JWT
     */
    @GetMapping("/reports/{groupId}/statuses")
    public ResponseEntity<List<UserStatusResponse>> getGroupStatuses(
            @PathVariable String groupId,
            @RequestHeader("X-Session-Token") String sessionToken) {

        // Перевірити та отримати авторизовану сесію
        QrSession session = qrSessionService.getAuthorizedSession(sessionToken);

        // Перевірити що сесія для цієї групи
        if (!session.getGroupId().equals(groupId)) {
            log.warn("Session token {} is for group {}, but requesting group {}",
                     sessionToken, session.getGroupId(), groupId);
            return ResponseEntity.status(403).build();
        }

        // Отримати статуси учасників
        List<UserStatusResponse> statuses = reportService.getGroupStatuses(groupId, session.getUserId());

        return ResponseEntity.ok(statuses);
    }

    /**
     * GET /api/web/groups/{groupId}
     * Отримати інформацію про групу (для веб інтерфейсу)
     * Використовує session token замість JWT
     */
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<Map<String, String>> getGroupInfo(
            @PathVariable String groupId,
            @RequestHeader("X-Session-Token") String sessionToken) {

        // Перевірити та отримати авторизовану сесію
        QrSession session = qrSessionService.getAuthorizedSession(sessionToken);

        // Перевірити що сесія для цієї групи
        if (!session.getGroupId().equals(groupId)) {
            log.warn("Session token {} is for group {}, but requesting group {}",
                     sessionToken, session.getGroupId(), groupId);
            return ResponseEntity.status(403).build();
        }

        // Отримати групу
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Групу не знайдено"));

        // Повернути тільки необхідну інформацію
        return ResponseEntity.ok(Map.of(
                "externalName", group.getExternalName(),
                "reportType", group.getReportType().toString()
        ));
    }

    /**
     * GET /api/web/reports/{groupId}/user/{userId}
     * Отримати всі звіти конкретного користувача (для деталізації)
     */
    @GetMapping("/reports/{groupId}/user/{userId}")
    public ResponseEntity<List<ReportResponse>> getUserReports(
            @PathVariable String groupId,
            @PathVariable String userId,
            @RequestHeader("X-Session-Token") String sessionToken) {

        // Перевірити та отримати авторизовану сесію
        QrSession session = qrSessionService.getAuthorizedSession(sessionToken);

        // Перевірити що сесія для цієї групи
        if (!session.getGroupId().equals(groupId)) {
            log.warn("Session token {} is for group {}, but requesting group {}",
                     sessionToken, session.getGroupId(), groupId);
            return ResponseEntity.status(403).build();
        }

        // Отримати звіти користувача
        List<ReportResponse> reports = reportService.getUserReportsInGroup(groupId, userId, session.getUserId());

        return ResponseEntity.ok(reports);
    }
}
