package com.zvit.service;

import com.zvit.dto.request.ExtendedReportRequest;
import com.zvit.dto.request.SimpleReportRequest;
import com.zvit.dto.request.UrgentReportRequest;
import com.zvit.dto.response.ReportResponse;
import com.zvit.dto.response.UserStatusResponse;
import com.zvit.entity.Group;
import com.zvit.entity.GroupMember;
import com.zvit.entity.Report;
import com.zvit.entity.User;
import com.zvit.entity.enums.Role;
import com.zvit.repository.GroupMemberRepository;
import com.zvit.repository.GroupRepository;
import com.zvit.repository.ReportRepository;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    private static final int REPORT_WINDOW_HOURS = 24;

    @Transactional
    public ReportResponse createSimpleReport(SimpleReportRequest request, String userId) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        groupMemberRepository.findByGroupIdAndUserId(request.getGroupId(), userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        Report report = Report.builder()
                .group(group)
                .user(user)
                .reportType(Report.ReportType.SIMPLE)
                .simpleResponse(request.getSimpleResponse())
                .comment(request.getComment())
                .build();

        reportRepository.save(report);

        return mapToReportResponse(report);
    }

    @Transactional
    public ReportResponse createExtendedReport(ExtendedReportRequest request, String userId) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        groupMemberRepository.findByGroupIdAndUserId(request.getGroupId(), userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        Report report = Report.builder()
                .group(group)
                .user(user)
                .reportType(Report.ReportType.EXTENDED)
                .field1Value(request.getField1())
                .field2Value(request.getField2())
                .field3Value(request.getField3())
                .field4Value(request.getField4())
                .field5Value(request.getField5())
                .comment(request.getComment())
                .build();

        reportRepository.save(report);

        return mapToReportResponse(report);
    }

    public List<ReportResponse> getAllMyReports(String userId) {
        List<Report> reports = reportRepository.findByUser_IdOrderBySubmittedAtDesc(userId);

        return reports.stream()
                .map(this::mapToReportResponse)
                .collect(Collectors.toList());
    }

    public List<ReportResponse> getMyReports(String groupId, String userId) {
        groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        List<Report> reports = reportRepository.findByGroup_IdAndUser_IdOrderBySubmittedAtDesc(groupId, userId);

        return reports.stream()
                .map(this::mapToReportResponse)
                .collect(Collectors.toList());
    }

    public ReportResponse getMyLastReport(String groupId, String userId) {
        groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        Report lastReport = reportRepository.findFirstByGroup_IdAndUser_IdOrderBySubmittedAtDesc(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Звітів не знайдено"));

        return mapToReportResponse(lastReport);
    }

    @Transactional(readOnly = true)
    public List<UserStatusResponse> getGroupStatuses(String groupId, String userId) {
        GroupMember requesterMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        // Перевіряємо чи запитувач є адміністратором
        boolean isAdmin = requesterMember.getRole() == GroupMember.Role.ADMIN;

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(REPORT_WINDOW_HOURS);

        return members.stream()
                .map(member -> {
                    Report lastReport = reportRepository
                            .findFirstByGroup_IdAndUser_IdOrderBySubmittedAtDesc(groupId, member.getUser().getId())
                            .orElse(null);

                    String colorHex;
                    Double percentageElapsed;

                    if (lastReport == null || lastReport.getSubmittedAt().isBefore(windowStart)) {
                        colorHex = "#CCCCCC";
                        percentageElapsed = null;
                    } else {
                        Duration elapsed = Duration.between(lastReport.getSubmittedAt(), now);
                        long elapsedHours = elapsed.toHours();
                        percentageElapsed = (elapsedHours * 100.0) / REPORT_WINDOW_HOURS;
                        colorHex = calculateGradientColor(percentageElapsed);
                    }

                    return createStatusResponse(member, lastReport, colorHex, percentageElapsed, isAdmin);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void createUrgentRequest(UrgentReportRequest request, String userId) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(request.getGroupId(), userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може створювати термінові запити");
        }

        System.out.println("Терміновий запит для групи: " + group.getExternalName());
        System.out.println("Повідомлення: " + request.getMessage());
    }

    public List<ReportResponse> getAllGroupReports(String groupId, String userId) {
        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може переглядати всі звіти");
        }

        List<Report> reports = reportRepository.findByGroup_IdOrderBySubmittedAtDesc(groupId);

        return reports.stream()
                .map(this::mapToReportResponse)
                .collect(Collectors.toList());
    }

    public List<ReportResponse> getUserReportsInGroup(String groupId, String targetUserId, String requesterId) {
        GroupMember requesterMember = groupMemberRepository.findByGroupIdAndUserId(groupId, requesterId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (requesterMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може переглядати звіти інших учасників");
        }

        groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new RuntimeException("Користувач не є учасником цієї групи"));

        List<Report> reports = reportRepository.findByGroup_IdAndUser_IdOrderBySubmittedAtDesc(groupId, targetUserId);

        return reports.stream()
                .map(this::mapToReportResponse)
                .collect(Collectors.toList());
    }

    private UserStatusResponse createStatusResponse(
            GroupMember member,
            Report lastReport,
            String colorHex,
            Double percentageElapsed,
            boolean isRequesterAdmin
    ) {
        UserStatusResponse.UserStatusResponseBuilder builder = UserStatusResponse.builder()
                .userId(member.getUser().getId())
                .userName(member.getUser().getName())
                .role(Role.valueOf(member.getRole().name()))
                .hasReported(lastReport != null)
                .lastReportAt(lastReport != null ? lastReport.getSubmittedAt() : null)
                .lastReportResponse(lastReport != null ? lastReport.getSimpleResponse() : null)
                .colorHex(colorHex)
                .percentageElapsed(percentageElapsed);

        // Тільки адміністратори можуть бачити реальні номери телефонів
        if (isRequesterAdmin) {
            try {
                String decryptedPhone = encryptionService.decrypt(member.getUser().getPhoneEncrypted());
                builder.phoneNumber(decryptedPhone);
            } catch (Exception e) {
                // Якщо не вдалося розшифрувати, просто не додаємо номер
                builder.phoneNumber(null);
            }
        }

        return builder.build();
    }

    private ReportResponse mapToReportResponse(Report report) {
        return ReportResponse.builder()
                .reportId(report.getId())
                .userId(report.getUser().getId())
                .userName(report.getUser().getName())
                .groupId(report.getGroup().getId())
                .groupName(report.getGroup().getExternalName())
                .reportType(com.zvit.entity.enums.ReportType.valueOf(report.getReportType().name()))
                .simpleResponse(report.getSimpleResponse())
                .comment(report.getComment())
                .submittedAt(report.getSubmittedAt())
                .field1Value(report.getField1Value())
                .field2Value(report.getField2Value())
                .field3Value(report.getField3Value())
                .field4Value(report.getField4Value())
                .field5Value(report.getField5Value())
                .build();
    }

    private String calculateGradientColor(double percentage) {
        percentage = Math.max(0, Math.min(100, percentage));

        int red, green;

        if (percentage <= 50) {
            red = (int) (255 * (percentage / 50.0));
            green = 255;
        } else {
            red = 255;
            green = (int) (255 * ((100 - percentage) / 50.0));
        }

        return String.format("#%02X%02X00", red, green);
    }
}