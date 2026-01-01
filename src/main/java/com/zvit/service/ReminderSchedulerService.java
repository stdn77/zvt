package com.zvit.service;

import com.zvit.entity.Group;
import com.zvit.entity.GroupMember;
import com.zvit.entity.ScheduleType;
import com.zvit.entity.User;
import com.zvit.repository.GroupMemberRepository;
import com.zvit.repository.GroupRepository;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
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

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Запускається кожну хвилину і перевіряє чи потрібно надіслати нагадування
     */
    @Scheduled(cron = "0 * * * * *") // Every minute at :00 seconds
    public void checkAndSendReminders() {
        if (!firebaseService.isFirebaseInitialized()) {
            return;
        }

        LocalTime now = LocalTime.now();
        String currentTime = now.format(TIME_FORMATTER);

        log.debug("🔔 Checking reminders at {}", currentTime);

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

        if (group.getScheduleType() == ScheduleType.FIXED_TIMES) {
            // Check if current time matches any fixed time
            return currentTime.equals(group.getFixedTime1()) ||
                   currentTime.equals(group.getFixedTime2()) ||
                   currentTime.equals(group.getFixedTime3()) ||
                   currentTime.equals(group.getFixedTime4()) ||
                   currentTime.equals(group.getFixedTime5());

        } else if (group.getScheduleType() == ScheduleType.INTERVAL) {
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
     * Надсилає нагадування всім учасникам групи, які використовують тільки PWA
     */
    private int sendRemindersForGroup(Group group) {
        log.debug("Sending reminders for group: {}", group.getName());

        // Get all active members of the group
        List<GroupMember> members = groupMemberRepository.findByGroupIdAndStatus(
                group.getId(), GroupMember.MemberStatus.ACTIVE);

        if (members.isEmpty()) {
            return 0;
        }

        // Collect web tokens for users who DON'T have Android token
        List<String> webTokens = new ArrayList<>();
        Map<String, String> data = new HashMap<>();
        data.put("groupId", group.getId());
        data.put("groupName", group.getName());
        data.put("type", "REMINDER");

        for (GroupMember member : members) {
            try {
                User user = userRepository.findById(member.getUserId()).orElse(null);
                if (user == null) continue;

                // Skip users who disabled notifications
                if (!user.isNotificationsEnabled()) {
                    log.debug("User {} has notifications disabled, skipping", user.getId());
                    continue;
                }

                // Skip users who have Android app (they get local reminders)
                if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                    log.debug("User {} has Android token, skipping server reminder", user.getId());
                    continue;
                }

                // Add web token if available
                if (user.getFcmTokenWeb() != null && !user.getFcmTokenWeb().isEmpty()) {
                    webTokens.add(user.getFcmTokenWeb());
                }

            } catch (Exception e) {
                log.debug("Error processing member {}: {}", member.getUserId(), e.getMessage());
            }
        }

        if (webTokens.isEmpty()) {
            return 0;
        }

        // Send notifications
        String title = "⏰ Час звітувати!";
        String body = group.getName() + " - надішліть свій звіт";

        int sent = firebaseService.sendPushNotificationToMultiple(webTokens, title, body, data);
        log.debug("Sent {} web reminders for group {}", sent, group.getName());

        return sent;
    }
}
