package com.zvit.service;

import com.zvit.dto.request.AddMemberRequest;
import com.zvit.dto.request.CreateGroupRequest;
import com.zvit.dto.request.JoinGroupRequest;
import com.zvit.dto.response.GroupMemberResponse;
import com.zvit.dto.response.GroupResponse;
import com.zvit.entity.Group;
import com.zvit.entity.GroupMember;
import com.zvit.entity.Report;
import com.zvit.entity.User;
import com.zvit.repository.GroupMemberRepository;
import com.zvit.repository.GroupRepository;
import com.zvit.repository.ReportRepository;
import com.zvit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        Group group = Group.builder()
                .externalName(request.getExternalName())
                .maxMembers(request.getMaxMembers())
                .reportType(request.getReportType())
                .createdBy(userId)
                .build();

        groupRepository.save(group);

        GroupMember adminMember = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupMember.Role.ADMIN)
                .status(GroupMember.MemberStatus.ACCEPTED) // Адміністратор автоматично прийнятий
                .build();

        groupMemberRepository.save(adminMember);

        return mapToGroupResponse(group, adminMember);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getUserGroups(String userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);

        // Фільтруємо тільки групи зі статусом ACCEPTED
        return memberships.stream()
                .filter(member -> member.getStatus() == GroupMember.MemberStatus.ACCEPTED)
                .map(member -> mapToGroupResponse(member.getGroup(), member))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupById(String groupId, String userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        return mapToGroupResponse(group, member);
    }

    @Transactional
    public void addMemberToGroup(String groupId, AddMemberRequest request, String adminUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, adminUserId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може додавати учасників");
        }

        long currentMembers = groupMemberRepository.countByGroupId(groupId);
        if (currentMembers >= group.getMaxMembers()) {
            throw new RuntimeException("Досягнуто максимальну кількість учасників");
        }

        User newUser = userRepository.findByPhoneHash(hashPhone(request.getPhone()))
                .orElseThrow(() -> new RuntimeException("Користувача з таким телефоном не знайдено"));

        if (groupMemberRepository.findByGroupIdAndUserId(groupId, newUser.getId()).isPresent()) {
            throw new RuntimeException("Користувач вже є учасником групи");
        }

        GroupMember newMember = GroupMember.builder()
                .group(group)
                .user(newUser)
                .role(GroupMember.Role.MEMBER)
                .status(GroupMember.MemberStatus.ACCEPTED) // Додані адміністратором автоматично прийняті
                .build();

        groupMemberRepository.save(newMember);
    }

    @Transactional
    public GroupResponse joinGroupByAccessCode(JoinGroupRequest request, String userId) {
        Group group = groupRepository.findByAccessCode(request.getAccessCode())
                .orElseThrow(() -> new RuntimeException("Групу з таким кодом не знайдено"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено"));

        if (groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId).isPresent()) {
            throw new RuntimeException("Ви вже є учасником цієї групи");
        }

        long currentMembers = groupMemberRepository.countByGroupId(group.getId());
        if (currentMembers >= group.getMaxMembers()) {
            throw new RuntimeException("Досягнуто максимальну кількість учасників");
        }

        GroupMember newMember = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupMember.Role.MEMBER)
                .status(GroupMember.MemberStatus.PENDING) // Очікує затвердження адміністратором
                .build();

        groupMemberRepository.save(newMember);

        return mapToGroupResponse(group, newMember);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getGroupMembers(String groupId, String userId) {
        GroupMember requesterMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        // Перевіряємо чи запитувач є адміністратором
        boolean isAdmin = requesterMember.getRole() == GroupMember.Role.ADMIN;

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);

        return members.stream()
                .map(member -> mapToMemberResponse(member, isAdmin))
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeMemberFromGroup(String groupId, String memberUserId, String adminUserId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, adminUserId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може видаляти учасників");
        }

        if (memberUserId.equals(adminUserId)) {
            throw new RuntimeException("Використовуйте endpoint /leave для виходу з групи");
        }

        GroupMember memberToRemove = groupMemberRepository.findByGroupIdAndUserId(groupId, memberUserId)
                .orElseThrow(() -> new RuntimeException("Учасника не знайдено в групі"));

        groupMemberRepository.delete(memberToRemove);
    }

    @Transactional
    public void leaveGroup(String groupId, String userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        long currentMembers = groupMemberRepository.countByGroupId(groupId);
        
        if (member.getRole() == GroupMember.Role.ADMIN && currentMembers > 1) {
            throw new RuntimeException("Адміністратор не може вийти доки в групі є інші учасники");
        }

        groupMemberRepository.delete(member);

        if (currentMembers == 1) {
            groupRepository.delete(group);
        }
    }

    @Transactional
    public void deleteGroup(String groupId, String userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може видалити групу");
        }

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        groupMemberRepository.deleteAll(members);

        groupRepository.delete(group);
    }

    @Transactional
    public void changeUserRole(String groupId, String memberUserId, GroupMember.Role newRole, String adminUserId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        // Перевіряємо що запитувач є адміністратором
        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, adminUserId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може змінювати ролі");
        }

        // Знаходимо учасника, роль якого потрібно змінити
        GroupMember memberToChange = groupMemberRepository.findByGroupIdAndUserId(groupId, memberUserId)
                .orElseThrow(() -> new RuntimeException("Учасника не знайдено в групі"));

        // Якщо змінюємо адміністратора на користувача, перевіряємо що це не останній адмін
        if (memberToChange.getRole() == GroupMember.Role.ADMIN && newRole == GroupMember.Role.MEMBER) {
            long adminCount = groupMemberRepository.findByGroupId(groupId).stream()
                    .filter(m -> m.getRole() == GroupMember.Role.ADMIN)
                    .count();

            if (adminCount < 2) {
                throw new RuntimeException("Неможливо зняти останнього адміністратора");
            }
        }

        // Змінюємо роль
        memberToChange.setRole(newRole);
        groupMemberRepository.save(memberToChange);
    }

    @Transactional
    public void updateGroupSettings(String groupId, com.zvit.dto.request.UpdateGroupSettingsRequest request, String adminUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Групу не знайдено"));

        // Перевіряємо що запитувач є адміністратором
        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, adminUserId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може змінювати налаштування групи");
        }

        // Оновлюємо тип звіту
        if (request.getReportType() != null) {
            group.setReportType(Group.ReportType.valueOf(request.getReportType()));
        }

        // Оновлюємо розклад звітів
        if (request.getScheduleType() != null) {
            Group.ScheduleType scheduleType = Group.ScheduleType.valueOf(request.getScheduleType());
            group.setScheduleType(scheduleType);

            if (scheduleType == Group.ScheduleType.FIXED_TIMES) {
                // Очищаємо старі значення
                group.setFixedTime1(null);
                group.setFixedTime2(null);
                group.setFixedTime3(null);
                group.setFixedTime4(null);
                group.setFixedTime5(null);
                group.setIntervalMinutes(null);
                group.setIntervalStartTime(null);

                // Встановлюємо нові часи (до 5)
                if (request.getFixedTimes() != null) {
                    List<String> times = request.getFixedTimes();
                    if (times.size() > 0) group.setFixedTime1(times.get(0));
                    if (times.size() > 1) group.setFixedTime2(times.get(1));
                    if (times.size() > 2) group.setFixedTime3(times.get(2));
                    if (times.size() > 3) group.setFixedTime4(times.get(3));
                    if (times.size() > 4) group.setFixedTime5(times.get(4));
                }
            } else if (scheduleType == Group.ScheduleType.INTERVAL) {
                // Очищаємо старі значення
                group.setFixedTime1(null);
                group.setFixedTime2(null);
                group.setFixedTime3(null);
                group.setFixedTime4(null);
                group.setFixedTime5(null);

                // Встановлюємо інтервал
                group.setIntervalMinutes(request.getIntervalMinutes());
                group.setIntervalStartTime(request.getIntervalStartTime());
            }
        }

        groupRepository.save(group);
    }

    @Transactional
    public void approveMember(String groupId, String userId, String adminUserId) {
        // Перевіряємо що запитувач є адміністратором
        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, adminUserId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може затверджувати учасників"));
        }

        // Знаходимо учасника що очікує затвердження
        GroupMember pendingMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено в групі"));

        if (pendingMember.getStatus() != GroupMember.MemberStatus.PENDING) {
            throw new RuntimeException("Користувач не очікує затвердження"));
        }

        // Затверджуємо учасника
        pendingMember.setStatus(GroupMember.MemberStatus.ACCEPTED);
        groupMemberRepository.save(pendingMember);
    }

    @Transactional
    public void rejectMember(String groupId, String userId, String adminUserId) {
        // Перевіряємо що запитувач є адміністратором
        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, adminUserId)
                .orElseThrow(() -> new RuntimeException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new RuntimeException("Тільки адміністратор може відхиляти учасників"));
        }

        // Знаходимо учасника що очікує затвердження
        GroupMember pendingMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new RuntimeException("Користувача не знайдено в групі"));

        if (pendingMember.getStatus() != GroupMember.MemberStatus.PENDING) {
            throw new RuntimeException("Користувач не очікує затвердження"));
        }

        // Відхиляємо і видаляємо учасника
        groupMemberRepository.delete(pendingMember);
    }

    private GroupResponse mapToGroupResponse(Group group, GroupMember member) {
        boolean isAdmin = member.getRole() == GroupMember.Role.ADMIN;
        long currentMembers = groupMemberRepository.countByGroupId(group.getId());

        // Збираємо fixed times в список
        List<String> fixedTimes = new java.util.ArrayList<>();
        if (group.getFixedTime1() != null) fixedTimes.add(group.getFixedTime1());
        if (group.getFixedTime2() != null) fixedTimes.add(group.getFixedTime2());
        if (group.getFixedTime3() != null) fixedTimes.add(group.getFixedTime3());
        if (group.getFixedTime4() != null) fixedTimes.add(group.getFixedTime4());
        if (group.getFixedTime5() != null) fixedTimes.add(group.getFixedTime5());

        // Отримуємо час останнього звіту користувача
        Optional<Report> lastReport = reportRepository.findFirstByGroup_IdAndUser_IdOrderBySubmittedAtDesc(
                group.getId(), member.getUser().getId());

        return GroupResponse.builder()
                .groupId(group.getId())
                .externalName(group.getExternalName())
                .accessCode(isAdmin ? group.getAccessCode() : null)
                .maxMembers(group.getMaxMembers())
                .currentMembers((int) currentMembers)
                .reportType(group.getReportType())
                .userRole(member.getRole().name())
                .createdAt(group.getCreatedAt())
                .scheduleType(group.getScheduleType())
                .fixedTimes(fixedTimes.isEmpty() ? null : fixedTimes)
                .intervalMinutes(group.getIntervalMinutes())
                .intervalStartTime(group.getIntervalStartTime())
                .lastReportAt(lastReport.map(Report::getSubmittedAt).orElse(null))
                .serverTime(java.time.LocalDateTime.now())  // Серверний час
                .timezone("Europe/Kiev")                    // Часова зона
                .build();
    }

    private GroupMemberResponse mapToMemberResponse(GroupMember member, boolean isRequesterAdmin) {
        GroupMemberResponse.GroupMemberResponseBuilder builder = GroupMemberResponse.builder()
                .userId(member.getUser().getId())
                .name(member.getUser().getName())
                .role(member.getRole().name())
                .status(member.getStatus().name())
                .joinedAt(member.getJoinedAt());

        // Тільки адміністратори можуть бачити реальні номери телефонів
        // Не-адміністратори не бачать нічого (ні хеш, ні реальний номер)
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

    private String hashPhone(String phone) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phone.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Помилка хешування телефону", e);
        }
    }
}