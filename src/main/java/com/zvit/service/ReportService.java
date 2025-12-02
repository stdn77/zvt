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
import com.zvit.entity.enums.GroupMemberRole;
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
import java.time.ZoneId;
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

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        // Обчислюємо розклад для групи
        LocalDateTime nextScheduled = calculateNextScheduledTime(group);
        LocalDateTime prevScheduled = calculatePreviousScheduledTime(group);

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);

        LocalDateTime serverTime = LocalDateTime.now();
        String timezone = "Europe/Kiev";

        return members.stream()
                .map(member -> {
                    Report lastReport = reportRepository
                            .findFirstByGroup_IdAndUser_IdOrderBySubmittedAtDesc(groupId, member.getUser().getId())
                            .orElse(null);

                    // MVZ - час останнього звіту (тільки якщо актуальний)
                    LocalDateTime lastReportTime = null;
                    if (lastReport != null && prevScheduled != null) {
                        // Обчислюємо CP (чверть періоду) для фільтрації старих звітів
                        long periodMinutes = Duration.between(prevScheduled, nextScheduled).toMinutes();
                        long cpMinutes = periodMinutes / 4;

                        // Відправляти MVZ тільки якщо звіт не дуже старий (пізніше ніж MZZ - CP)
                        if (lastReport.getSubmittedAt().isAfter(prevScheduled.minusMinutes(cpMinutes))) {
                            lastReportTime = lastReport.getSubmittedAt();
                        }
                    }

                    return createStatusResponse(member, lastReport, lastReportTime, prevScheduled, nextScheduled, serverTime, timezone, isAdmin);
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
            LocalDateTime lastReportTime,
            LocalDateTime previousScheduledTime,
            LocalDateTime nextScheduledTime,
            LocalDateTime serverTime,
            String timezone,
            boolean isRequesterAdmin
    ) {
        // Обчислення colorHex та percentageElapsed для веб-дашборду
        String colorHex = "#CCCCCC";  // За замовчуванням сірий
        Double percentageElapsed = null;

        // АДМІНІСТРАТОР - завжди темно-зелений
        if (member.getRole() == GroupMemberRole.ADMIN) {
            colorHex = "#006400";
            percentageElapsed = 0.0;
        }
        // Якщо є дані для обчислення кольору
        else if (lastReportTime != null && previousScheduledTime != null && nextScheduledTime != null) {
            long mzz = previousScheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long mvz = lastReportTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long nz = nextScheduledTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long ct = serverTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            long periodMillis = nz - mzz;
            long cp = periodMillis / 4;
            long pp = periodMillis / 2;

            // ЛОГІКА КОЛЬОРІВ (використовуємо серверний час)
            if (mvz > (nz - cp)) {
                colorHex = "#00FF00";  // Рання подача
                percentageElapsed = 0.0;
            } else if (mvz < (mzz - cp)) {
                colorHex = "#FF0000";  // Дуже старий
                percentageElapsed = 100.0;
            } else {
                if (ct > (nz - cp)) {
                    colorHex = "#FF0000";  // >75%
                    percentageElapsed = 80.0;
                } else if (ct > (nz - pp)) {
                    colorHex = "#FFFF00";  // 50-75%
                    percentageElapsed = 60.0;
                } else {
                    colorHex = "#00FF00";  // 0-50%
                    percentageElapsed = 25.0;
                }
            }
        }

        UserStatusResponse.UserStatusResponseBuilder builder = UserStatusResponse.builder()
                .userId(member.getUser().getId())
                .userName(member.getUser().getName())
                .role(Role.valueOf(member.getRole().name()))
                .hasReported(lastReport != null)
                .lastReportAt(lastReportTime)  // MVZ - може бути null якщо звіт старий
                .lastReportResponse(lastReport != null ? lastReport.getSimpleResponse() : null)
                .colorHex(colorHex)                            // Для веб-дашборду
                .percentageElapsed(percentageElapsed)          // Для веб-дашборду
                .previousScheduledTime(previousScheduledTime)  // MZZ
                .nextScheduledTime(nextScheduledTime)          // NZ
                .serverTime(serverTime)                        // Серверний час
                .timezone(timezone);                           // Часова зона

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

    /**
     * Обчислює наступний запланований час звіту для групи
     */
    private LocalDateTime calculateNextScheduledTime(Group group) {
        if (group.getScheduleType() == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        if (group.getScheduleType() == Group.ScheduleType.FIXED_TIMES) {
            return getNextFixedTime(group, now);
        } else if (group.getScheduleType() == Group.ScheduleType.INTERVAL) {
            return getNextIntervalTime(group, now);
        }

        return null;
    }

    /**
     * Обчислює попередній запланований час звіту для групи
     */
    private LocalDateTime calculatePreviousScheduledTime(Group group) {
        if (group.getScheduleType() == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        if (group.getScheduleType() == Group.ScheduleType.FIXED_TIMES) {
            return getPreviousFixedTime(group, now);
        } else if (group.getScheduleType() == Group.ScheduleType.INTERVAL) {
            return getPreviousIntervalTime(group, now);
        }

        return null;
    }

    /**
     * Знаходить наступний фіксований час звіту
     */
    private LocalDateTime getNextFixedTime(Group group, LocalDateTime now) {
        List<String> fixedTimes = getFixedTimesFromGroup(group);
        if (fixedTimes.isEmpty()) {
            return null;
        }

        int currentMinutes = now.getHour() * 60 + now.getMinute();
        LocalDateTime nextTime = null;
        int minDiff = Integer.MAX_VALUE;

        for (String time : fixedTimes) {
            try {
                String[] parts = time.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int timeInMinutes = hours * 60 + minutes;
                int diff = timeInMinutes - currentMinutes;

                if (diff > 0 && diff < minDiff) {
                    minDiff = diff;
                    nextTime = now.withHour(hours).withMinute(minutes).withSecond(0).withNano(0);
                }
            } catch (Exception e) {
                // Пропускаємо невалідні часи
            }
        }

        // Якщо не знайшли час сьогодні, беремо перший час завтра
        if (nextTime == null && !fixedTimes.isEmpty()) {
            String firstTime = fixedTimes.get(0);
            String[] parts = firstTime.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            nextTime = now.plusDays(1).withHour(hours).withMinute(minutes).withSecond(0).withNano(0);
        }

        return nextTime;
    }

    /**
     * Обчислює наступний час звіту для інтервального розкладу
     */
    private LocalDateTime getNextIntervalTime(Group group, LocalDateTime now) {
        if (group.getIntervalMinutes() == null || group.getIntervalStartTime() == null) {
            return null;
        }

        try {
            String[] parts = group.getIntervalStartTime().split(":");
            int startHours = Integer.parseInt(parts[0]);
            int startMinutes = Integer.parseInt(parts[1]);

            LocalDateTime startTime = now.withHour(startHours).withMinute(startMinutes).withSecond(0).withNano(0);

            // Якщо час початку ще не настав сьогодні
            if (now.isBefore(startTime)) {
                return startTime;
            }

            // Обраховуємо скільки інтервалів пройшло
            long diffMinutes = Duration.between(startTime, now).toMinutes();
            long intervalsPassed = diffMinutes / group.getIntervalMinutes();

            // Наступний інтервал
            return startTime.plusMinutes((intervalsPassed + 1) * group.getIntervalMinutes());

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Знаходить попередній фіксований час звіту
     */
    private LocalDateTime getPreviousFixedTime(Group group, LocalDateTime now) {
        List<String> fixedTimes = getFixedTimesFromGroup(group);
        if (fixedTimes.isEmpty()) {
            return null;
        }

        int currentMinutes = now.getHour() * 60 + now.getMinute();
        LocalDateTime previousTime = null;
        int maxDiff = Integer.MIN_VALUE;

        for (String time : fixedTimes) {
            try {
                String[] parts = time.split(":");
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int timeInMinutes = hours * 60 + minutes;
                int diff = currentMinutes - timeInMinutes;

                // Шукаємо найближчий час, що менший за поточний
                if (diff > 0 && diff > maxDiff) {
                    maxDiff = diff;
                    previousTime = now.withHour(hours).withMinute(minutes).withSecond(0).withNano(0);
                }
            } catch (Exception e) {
                // Пропускаємо невалідні часи
            }
        }

        // Якщо не знайшли час сьогодні, беремо останній час вчора
        if (previousTime == null && !fixedTimes.isEmpty()) {
            String lastTime = fixedTimes.get(fixedTimes.size() - 1);
            String[] parts = lastTime.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            previousTime = now.minusDays(1).withHour(hours).withMinute(minutes).withSecond(0).withNano(0);
        }

        return previousTime;
    }

    /**
     * Обчислює попередній час звіту для інтервального розкладу
     */
    private LocalDateTime getPreviousIntervalTime(Group group, LocalDateTime now) {
        if (group.getIntervalMinutes() == null || group.getIntervalStartTime() == null) {
            return null;
        }

        try {
            String[] parts = group.getIntervalStartTime().split(":");
            int startHours = Integer.parseInt(parts[0]);
            int startMinutes = Integer.parseInt(parts[1]);

            LocalDateTime startTime = now.withHour(startHours).withMinute(startMinutes).withSecond(0).withNano(0);

            // Якщо час початку ще не настав сьогодні, попередній звіт був вчора
            if (now.isBefore(startTime)) {
                return startTime;
            }

            // Обраховуємо скільки інтервалів пройшло
            long diffMinutes = Duration.between(startTime, now).toMinutes();
            long intervalsPassed = diffMinutes / group.getIntervalMinutes();

            // Попередній інтервал
            return startTime.plusMinutes(intervalsPassed * group.getIntervalMinutes());

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Збирає всі фіксовані часи з групи в список
     */
    private List<String> getFixedTimesFromGroup(Group group) {
        List<String> times = new java.util.ArrayList<>();
        if (group.getFixedTime1() != null) times.add(group.getFixedTime1());
        if (group.getFixedTime2() != null) times.add(group.getFixedTime2());
        if (group.getFixedTime3() != null) times.add(group.getFixedTime3());
        if (group.getFixedTime4() != null) times.add(group.getFixedTime4());
        if (group.getFixedTime5() != null) times.add(group.getFixedTime5());
        return times;
    }
}