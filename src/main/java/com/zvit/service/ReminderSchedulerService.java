package com.zvit.service;

import com.zvit.entity.Group;
import com.zvit.entity.GroupMember;
import com.zvit.entity.User;
import com.zvit.repository.GroupMemberRepository;
import com.zvit.repository.GroupRepository;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Сервіс для серверних нагадувань (для PWA/Web користувачів).
 * Android користувачі отримують локальні нагадування через AlarmManager.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderSchedulerService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final FirebaseService firebaseService;

    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Запускається кожну хвилину і перевіряє чи потрібно надіслати нагадування
     */
    @Scheduled(cron = "0 * * * * *") // Every minute at :00 seconds
    public void checkAndSendReminders() {
        if (!firebaseService.isFirebaseInitialized()) {
            return;
        }

        LocalTime now = LocalTime.now(KYIV_ZONE);
        String currentTime = now.format(TIME_FORMATTER);

        log.debug("🔔 Checking reminders at {} (Kyiv)", currentTime);

        try {
            // Get all groups that have schedule configured
            List<Group> allGroups = groupRepository.findAll();

            int remindersSent = 0;

            for (Group group : allGroups) {
                if (shouldSendReminderNow(group, now)) {
                    remindersSent += sendRemindersForGroup(group);
                }
            }

            if (remindersSent > 0) {
                log.info("🔔 Sent {} server reminders at {}", remindersSent, currentTime);
            }

        } catch (Exception e) {
            log.error("Error checking reminders: {}", e.getMessage(), e);
        }
    }

    /**
     * Перевіряє чи потрібно надіслати нагадування для групи зараз
     */
    private boolean shouldSendReminderNow(Group group, LocalTime now) {
        if (group.getScheduleType() == null) {
            return false;
        }

        String currentTime = now.format(TIME_FORMATTER);

        if (group.getScheduleType() == Group.ScheduleType.FIXED_TIMES) {
            // Check if current time matches any fixed time
            return currentTime.equals(group.getFixedTime1()) ||
                   currentTime.equals(group.getFixedTime2()) ||
                   currentTime.equals(group.getFixedTime3()) ||
                   currentTime.equals(group.getFixedTime4()) ||
                   currentTime.equals(group.getFixedTime5());

        } else if (group.getScheduleType() == Group.ScheduleType.INTERVAL) {
            // Check if current time matches the interval pattern
            return isIntervalMatch(now, group.getIntervalStartTime(), group.getIntervalMinutes());
        }

        return false;
    }

    /**
     * Перевіряє чи поточний час відповідає інтервальному розкладу
     */
    private boolean isIntervalMatch(LocalTime now, String startTimeStr, Integer intervalMinutes) {
        if (startTimeStr == null || intervalMinutes == null || intervalMinutes <= 0) {
            return false;
        }

        try {
            LocalTime startTime = LocalTime.parse(startTimeStr, TIME_FORMATTER);

            // Calculate minutes from start of day
            int nowMinutes = now.getHour() * 60 + now.getMinute();
            int startMinutes = startTime.getHour() * 60 + startTime.getMinute();

            // Check if we're at or after start time
            if (nowMinutes < startMinutes) {
                return false;
            }

            // Check if the difference is divisible by interval
            int diff = nowMinutes - startMinutes;
            return diff % intervalMinutes == 0;

        } catch (Exception e) {
            log.debug("Error parsing interval time: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Надсилає нагадування всім учасникам групи через Push (PWA/Web)
     */
    private int sendRemindersForGroup(Group group) {
        log.debug("Sending reminders for group: {}", group.getExternalName());

        // Get all active members of the group
        List<GroupMember> members = groupMemberRepository.findByGroupIdAndStatus(
                group.getId(), GroupMember.MemberStatus.ACCEPTED);

        if (members.isEmpty()) {
            return 0;
        }

        // Розділяємо токени по ролях
        List<String> memberTokens = new ArrayList<>();  // MEMBER - тільки "Час звітувати"
        List<String> moderTokens = new ArrayList<>();   // MODER - обидва повідомлення
        List<String> adminTokens = new ArrayList<>();   // ADMIN - тільки "Перегляньте звіти"

        Map<String, String> data = new HashMap<>();
        data.put("groupId", group.getId());
        data.put("groupName", group.getExternalName());
        data.put("type", "REMINDER");
        data.put("tag", "reminder-" + group.getId());

        for (GroupMember member : members) {
            try {
                User user = member.getUser();
                if (user == null) continue;

                // Skip users who disabled notifications
                if (!user.isNotificationsEnabled()) {
                    log.debug("User {} has notifications disabled, skipping", user.getId());
                    continue;
                }

                // Відправляємо тільки на ОДИН пристрій щоб уникнути дублювання
                String platform = user.getAppPlatform();
                String tokenToUse = null;

                if ("PWA".equals(platform)) {
                    if (user.getFcmTokenWeb() != null && !user.getFcmTokenWeb().isEmpty()) {
                        tokenToUse = user.getFcmTokenWeb();
                    } else if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                        tokenToUse = user.getFcmToken();
                    }
                } else {
                    if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                        tokenToUse = user.getFcmToken();
                    } else if (user.getFcmTokenWeb() != null && !user.getFcmTokenWeb().isEmpty()) {
                        tokenToUse = user.getFcmTokenWeb();
                    }
                }

                if (tokenToUse != null) {
                    // Розподіляємо по ролях
                    if (member.getRole() == GroupMember.Role.ADMIN) {
                        adminTokens.add(tokenToUse);
                    } else if (member.getRole() == GroupMember.Role.MODER) {
                        moderTokens.add(tokenToUse);
                    } else {
                        memberTokens.add(tokenToUse);
                    }
                }

            } catch (Exception e) {
                log.debug("Error processing member {}: {}", member.getUser().getId(), e.getMessage());
            }
        }

        int totalSent = 0;
        String groupName = group.getExternalName();

        // 1. MEMBER - тільки "Час звітувати"
        if (!memberTokens.isEmpty()) {
            String title = "⏰ Час звітувати!";
            String body = groupName + " - надішліть свій звіт";
            totalSent += firebaseService.sendPushNotificationToMultiple(memberTokens, title, body, data);
        }

        // 2. MODER - "Час звітувати" + "Перегляньте звіти"
        if (!moderTokens.isEmpty()) {
            // Спочатку "Час звітувати"
            String title1 = "⏰ Час звітувати!";
            String body1 = groupName + " - надішліть свій звіт";
            Map<String, String> data1 = new HashMap<>(data);
            data1.put("tag", "reminder-report-" + group.getId());
            totalSent += firebaseService.sendPushNotificationToMultiple(moderTokens, title1, body1, data1);

            // Потім "Перегляньте звіти"
            String title2 = "📋 Перегляньте звіти";
            String body2 = groupName + " - перевірте звіти учасників";
            Map<String, String> data2 = new HashMap<>(data);
            data2.put("tag", "reminder-review-" + group.getId());
            totalSent += firebaseService.sendPushNotificationToMultiple(moderTokens, title2, body2, data2);
        }

        // 3. ADMIN - тільки "Перегляньте звіти"
        if (!adminTokens.isEmpty()) {
            String title = "📋 Перегляньте звіти";
            String body = groupName + " - перевірте звіти учасників";
            Map<String, String> data3 = new HashMap<>(data);
            data3.put("tag", "reminder-review-" + group.getId());
            totalSent += firebaseService.sendPushNotificationToMultiple(adminTokens, title, body, data3);
        }

        log.info("🔔 Sent {} reminders for group {} (members: {}, moders: {}, admins: {})",
                totalSent, groupName, memberTokens.size(), moderTokens.size(), adminTokens.size());

        return totalSent;
    }
}
