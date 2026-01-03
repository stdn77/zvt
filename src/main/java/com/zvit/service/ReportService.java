package com.zvit.service;

import com.zvit.dto.request.ExtendedReportRequest;
import com.zvit.dto.request.SimpleReportRequest;
import com.zvit.dto.request.UrgentReportRequest;
import com.zvit.dto.response.GroupStatusesResponse;
import com.zvit.dto.response.ReportResponse;
import com.zvit.dto.response.UrgentSessionInfo;
import com.zvit.dto.response.UserStatusResponse;
import com.zvit.entity.Group;
import com.zvit.entity.GroupMember;
import com.zvit.entity.Report;
import com.zvit.entity.UrgentResponse;
import com.zvit.entity.User;
import com.zvit.entity.enums.Role;
import com.zvit.repository.GroupMemberRepository;
import com.zvit.repository.GroupRepository;
import com.zvit.repository.ReportRepository;
import com.zvit.repository.UrgentResponseRepository;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final UrgentResponseRepository urgentResponseRepository;
    private final EncryptionService encryptionService;
    private final FirebaseService firebaseService;
    private final RSAKeyService rsaKeyService;

    private static final int REPORT_WINDOW_HOURS = 24;

    /**
     * Перевіряє чи користувач має права адміністратора (ADMIN або MODER)
     */
    private boolean hasAdminRights(GroupMember member) {
        return member.getRole() == GroupMember.Role.ADMIN ||
               member.getRole() == GroupMember.Role.MODER;
    }

    @Transactional
    public ReportResponse createSimpleReport(SimpleReportRequest request, String userId) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        groupMemberRepository.findByGroupIdAndUserId(request.getGroupId(), userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        // Дешифруємо коментар
        String comment = rsaKeyService.decryptIfEncrypted(request.getComment());

        Report report = Report.builder()
                .group(group)
                .user(user)
                .reportType(Report.ReportType.SIMPLE)
                .simpleResponse(request.getSimpleResponse())
                .comment(comment)
                .build();

        reportRepository.save(report);

        // Якщо є активна термінова сесія - записуємо відповідь
        recordUrgentResponseIfActive(group, userId, report.getId());

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

        // Дешифруємо всі текстові поля
        String field1 = rsaKeyService.decryptIfEncrypted(request.getField1());
        String field2 = rsaKeyService.decryptIfEncrypted(request.getField2());
        String field3 = rsaKeyService.decryptIfEncrypted(request.getField3());
        String field4 = rsaKeyService.decryptIfEncrypted(request.getField4());
        String field5 = rsaKeyService.decryptIfEncrypted(request.getField5());
        String comment = rsaKeyService.decryptIfEncrypted(request.getComment());

        Report report = Report.builder()
                .group(group)
                .user(user)
                .reportType(Report.ReportType.EXTENDED)
                .field1Value(field1)
                .field2Value(field2)
                .field3Value(field3)
                .field4Value(field4)
                .field5Value(field5)
                .comment(comment)
                .build();

        reportRepository.save(report);

        // Якщо є активна термінова сесія - записуємо відповідь
        recordUrgentResponseIfActive(group, userId, report.getId());

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
    public GroupStatusesResponse getGroupStatuses(String groupId, String userId) {
        GroupMember requesterMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        // Перевіряємо чи запитувач є адміністратором або модератором
        boolean isAdmin = hasAdminRights(requesterMember);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        // Перевіряємо чи є активна термінова сесія
        boolean hasActiveUrgentSession = isUrgentSessionActive(group);
        String activeUrgentSessionId = hasActiveUrgentSession ? group.getUrgentSessionId() : null;

        // Обчислюємо розклад для групи
        LocalDateTime nextScheduled = calculateNextScheduledTime(group);
        LocalDateTime prevScheduled = calculatePreviousScheduledTime(group);

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);

        LocalDateTime serverTime = LocalDateTime.now();
        String timezone = "Europe/Kiev";

        // Отримуємо статуси користувачів
        List<UserStatusResponse> userStatuses = members.stream()
                // Фільтруємо тільки ACCEPTED користувачів (ігноруємо PENDING)
                .filter(member -> member.getStatus() == GroupMember.MemberStatus.ACCEPTED)
                .map(member -> {
                    Report lastReport = reportRepository
                            .findFirstByGroup_IdAndUser_IdOrderBySubmittedAtDesc(groupId, member.getUser().getId())
                            .orElse(null);

                    // MVZ - час останнього звіту (завжди відправляємо якщо є)
                    LocalDateTime lastReportTime = (lastReport != null) ? lastReport.getSubmittedAt() : null;

                    // Перевіряємо чи відповів користувач на терміновий запит
                    LocalDateTime urgentRespondedAt = null;
                    if (activeUrgentSessionId != null) {
                        urgentRespondedAt = urgentResponseRepository
                                .findByUrgentSessionIdAndUserId(activeUrgentSessionId, member.getUser().getId())
                                .map(UrgentResponse::getRespondedAt)
                                .orElse(null);
                    }

                    return createStatusResponse(member, lastReport, lastReportTime, prevScheduled, nextScheduled,
                            serverTime, timezone, isAdmin, urgentRespondedAt);
                })
                .collect(Collectors.toList());

        // Формуємо інформацію про терміновий збір
        UrgentSessionInfo urgentSession = buildUrgentSessionInfo(group, members, serverTime);

        return GroupStatusesResponse.builder()
                .users(userStatuses)
                .urgentSession(urgentSession)
                .build();
    }

    @Transactional
    public int createUrgentRequest(UrgentReportRequest request, String userId) {
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(request.getGroupId(), userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (!hasAdminRights(adminMember)) {
            throw new RuntimeException("Тільки адміністратор або модератор може створювати термінові запити");
        }

        // Перевіряємо чи немає вже активної термінової сесії
        if (isUrgentSessionActive(group)) {
            throw new RuntimeException("Вже є активний терміновий збір");
        }

        // Дешифруємо повідомлення
        String originalMessage = request.getMessage();
        String message = rsaKeyService.decryptIfEncrypted(originalMessage);

        // Валідуємо довжину тільки якщо повідомлення було дешифровано
        // (якщо дешифрування не вдалось, message == originalMessage, і це зашифрований текст)
        boolean wasDecrypted = !message.equals(originalMessage) || !rsaKeyService.isEncrypted(originalMessage);
        if (wasDecrypted && message != null && message.length() > 200) {
            throw new IllegalArgumentException("Повідомлення максимум 200 символів");
        }

        // Створюємо нову термінову сесію
        LocalDateTime now = LocalDateTime.now();
        String sessionId = UUID.randomUUID().toString();
        int deadlineMinutes = request.getDeadlineMinutes() > 0 ? request.getDeadlineMinutes() : 30;

        group.setUrgentSessionId(sessionId);
        group.setUrgentRequestedAt(now);
        group.setUrgentExpiresAt(now.plusMinutes(deadlineMinutes));
        group.setUrgentRequestedBy(userId);
        group.setUrgentMessage(message);
        groupRepository.save(group);

        log.info("Urgent session created: {} for group {}", sessionId, group.getExternalName());

        // Отримуємо всіх учасників групи (крім адміна, який надіслав)
        List<GroupMember> members = groupMemberRepository.findByGroupId(request.getGroupId());

        log.debug("Urgent report - Group: {}, Admin: {}, Members: {}",
                group.getExternalName(), userId, members.size());

        List<String> fcmTokens = new java.util.ArrayList<>();

        for (GroupMember member : members) {
            if (member.getStatus() != GroupMember.MemberStatus.ACCEPTED) continue;
            if (member.getUser().getId().equals(userId)) continue; // Виключаємо адміна

            // Перевіряємо чи сповіщення увімкнені
            if (!member.getUser().isNotificationsEnabled()) continue;

            // Додаємо Android токен
            String androidToken = member.getUser().getFcmToken();
            if (androidToken != null && !androidToken.isEmpty()) {
                fcmTokens.add(androidToken);
            }

            // Додаємо Web токен (для PWA)
            String webToken = member.getUser().getFcmTokenWeb();
            if (webToken != null && !webToken.isEmpty()) {
                fcmTokens.add(webToken);
            }
        }

        log.debug("FCM tokens to send: {}", fcmTokens.size());

        // Формуємо повідомлення
        String title = "Терміновий звіт: " + group.getExternalName();
        String body = message;

        // Додаткові дані для обробки в додатку
        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("type", "URGENT_REPORT");
        data.put("groupId", request.getGroupId());
        data.put("groupName", group.getExternalName());
        data.put("deadlineMinutes", String.valueOf(deadlineMinutes));
        data.put("urgentSessionId", sessionId);

        // Відправляємо Push-сповіщення
        int sentCount = firebaseService.sendPushNotificationToMultiple(fcmTokens, title, body, data);

        log.info("Urgent report sent: {} of {} notifications", sentCount, fcmTokens.size());

        return sentCount;
    }

    /**
     * Завершує термінову сесію вручну
     */
    @Transactional
    public void endUrgentSession(String groupId, String userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (!hasAdminRights(adminMember)) {
            throw new RuntimeException("Тільки адміністратор або модератор може завершувати терміновий збір");
        }

        if (group.getUrgentSessionId() == null) {
            throw new RuntimeException("Немає активного термінового збору");
        }

        log.info("Ending urgent session {} for group {}", group.getUrgentSessionId(), group.getExternalName());

        // Очищаємо дані термінової сесії (але не видаляємо відповіді - для історії)
        group.setUrgentSessionId(null);
        group.setUrgentRequestedAt(null);
        group.setUrgentExpiresAt(null);
        group.setUrgentRequestedBy(null);
        group.setUrgentMessage(null);
        groupRepository.save(group);
    }

    public List<ReportResponse> getAllGroupReports(String groupId, String userId) {
        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (!hasAdminRights(adminMember)) {
            throw new RuntimeException("Тільки адміністратор або модератор може переглядати всі звіти");
        }

        List<Report> reports = reportRepository.findByGroup_IdOrderBySubmittedAtDesc(groupId);

        return reports.stream()
                .map(this::mapToReportResponse)
                .collect(Collectors.toList());
    }

    public List<ReportResponse> getUserReportsInGroup(String groupId, String targetUserId, String requesterId) {
        GroupMember requesterMember = groupMemberRepository.findByGroupIdAndUserId(groupId, requesterId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (!hasAdminRights(requesterMember)) {
            throw new RuntimeException("Тільки адміністратор або модератор може переглядати звіти інших учасників");
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
            boolean isRequesterAdmin,
            LocalDateTime urgentRespondedAt
    ) {
        // Обчислення colorHex та percentageElapsed для веб-дашборду
        String colorHex = "#E0E0E0";  // За замовчуванням світло-сірий
        Double percentageElapsed = null;

        // АДМІНІСТРАТОР - завжди темно-зелений
        if (member.getRole() == GroupMember.Role.ADMIN) {
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

            // ЛОГІКА КОЛЬОРІВ (світлі відтінки для кращої читабельності)
            if (mvz > (nz - cp)) {
                colorHex = "#C8E6C9";  // Рання подача - світло-зелений
                percentageElapsed = 0.0;
            } else if (mvz < (mzz - cp)) {
                colorHex = "#FFCDD2";  // Дуже старий - світло-червоний
                percentageElapsed = 100.0;
            } else {
                if (ct > (nz - cp)) {
                    colorHex = "#FFCDD2";  // >75% - світло-червоний
                    percentageElapsed = 80.0;
                } else if (ct > (nz - pp)) {
                    colorHex = "#FFF59D";  // 50-75% - світло-жовтий
                    percentageElapsed = 60.0;
                } else {
                    colorHex = "#C8E6C9";  // 0-50% - світло-зелений
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
                .timezone(timezone)                            // Часова зона
                .urgentRespondedAt(urgentRespondedAt);         // Терміновий збір

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
     * Звіти йдуть безперервно 24/7 від часу початку
     */
    private LocalDateTime getNextIntervalTime(Group group, LocalDateTime now) {
        if (group.getIntervalMinutes() == null || group.getIntervalStartTime() == null) {
            return null;
        }

        try {
            String[] parts = group.getIntervalStartTime().split(":");
            int startHours = Integer.parseInt(parts[0]);
            int startMinutes = Integer.parseInt(parts[1]);

            // Знаходимо останнє спрацювання intervalStartTime (сьогодні або вчора)
            LocalDateTime baseTime = now.withHour(startHours).withMinute(startMinutes).withSecond(0).withNano(0);

            // Якщо сьогодні ще не настав час початку, беремо вчорашній день
            if (baseTime.isAfter(now)) {
                baseTime = baseTime.minusDays(1);
            }

            // Від baseTime відраховуємо інтервали до now
            long diffMinutes = Duration.between(baseTime, now).toMinutes();
            long intervalsPassed = diffMinutes / group.getIntervalMinutes();

            // Наступний інтервал після now
            return baseTime.plusMinutes((intervalsPassed + 1) * group.getIntervalMinutes());

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
     * Звіти йдуть безперервно 24/7 від часу початку
     */
    private LocalDateTime getPreviousIntervalTime(Group group, LocalDateTime now) {
        if (group.getIntervalMinutes() == null || group.getIntervalStartTime() == null) {
            return null;
        }

        try {
            String[] parts = group.getIntervalStartTime().split(":");
            int startHours = Integer.parseInt(parts[0]);
            int startMinutes = Integer.parseInt(parts[1]);

            // Знаходимо останнє спрацювання intervalStartTime (сьогодні або вчора)
            LocalDateTime baseTime = now.withHour(startHours).withMinute(startMinutes).withSecond(0).withNano(0);

            // Якщо сьогодні ще не настав час початку, беремо вчорашній день
            if (baseTime.isAfter(now)) {
                baseTime = baseTime.minusDays(1);
            }

            // Від baseTime відраховуємо інтервали до now
            long diffMinutes = Duration.between(baseTime, now).toMinutes();
            long intervalsPassed = diffMinutes / group.getIntervalMinutes();

            // Попередній інтервал (останній що був до now)
            return baseTime.plusMinutes(intervalsPassed * group.getIntervalMinutes());

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

    /**
     * Перевіряє чи є активна термінова сесія
     */
    private boolean isUrgentSessionActive(Group group) {
        if (group.getUrgentSessionId() == null || group.getUrgentExpiresAt() == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(group.getUrgentExpiresAt());
    }

    /**
     * Формує інформацію про терміновий збір
     */
    private UrgentSessionInfo buildUrgentSessionInfo(Group group, List<GroupMember> members, LocalDateTime serverTime) {
        if (!isUrgentSessionActive(group)) {
            return UrgentSessionInfo.builder()
                    .active(false)
                    .build();
        }

        // Рахуємо кількість учасників (без адміністраторів)
        long totalMembers = members.stream()
                .filter(m -> m.getStatus() == GroupMember.MemberStatus.ACCEPTED)
                .filter(m -> m.getRole() != GroupMember.Role.ADMIN)
                .count();

        // Рахуємо скільки відповіли
        long respondedCount = urgentResponseRepository.countByUrgentSessionId(group.getUrgentSessionId());

        // Рахуємо залишок секунд
        long remainingSeconds = ChronoUnit.SECONDS.between(serverTime, group.getUrgentExpiresAt());
        if (remainingSeconds < 0) remainingSeconds = 0;

        // Отримуємо ім'я адміна
        String requestedByName = null;
        if (group.getUrgentRequestedBy() != null) {
            requestedByName = userRepository.findById(group.getUrgentRequestedBy())
                    .map(User::getName)
                    .orElse(null);
        }

        return UrgentSessionInfo.builder()
                .active(true)
                .sessionId(group.getUrgentSessionId())
                .requestedAt(group.getUrgentRequestedAt())
                .expiresAt(group.getUrgentExpiresAt())
                .message(group.getUrgentMessage())
                .requestedByUserId(group.getUrgentRequestedBy())
                .requestedByUserName(requestedByName)
                .totalMembers((int) totalMembers)
                .respondedCount((int) respondedCount)
                .remainingSeconds(remainingSeconds)
                .build();
    }

    /**
     * Записує відповідь на терміновий запит якщо є активна сесія
     */
    private void recordUrgentResponseIfActive(Group group, String userId, String reportId) {
        if (!isUrgentSessionActive(group)) {
            return;
        }

        // Перевіряємо чи користувач ще не відповідав
        if (urgentResponseRepository.existsByUrgentSessionIdAndUserId(group.getUrgentSessionId(), userId)) {
            return;
        }

        // Перевіряємо чи користувач не адміністратор (адміни не відповідають)
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId).orElse(null);
        if (member != null && member.getRole() == GroupMember.Role.ADMIN) {
            return;
        }

        UrgentResponse response = UrgentResponse.builder()
                .urgentSessionId(group.getUrgentSessionId())
                .groupId(group.getId())
                .userId(userId)
                .reportId(reportId)
                .respondedAt(LocalDateTime.now())
                .build();

        urgentResponseRepository.save(response);
        log.info("Urgent response recorded: user {} for session {}", userId, group.getUrgentSessionId());
    }
}