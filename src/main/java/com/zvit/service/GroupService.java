package com.zvit.service;

import com.zvit.dto.request.AddMemberRequest;
import com.zvit.dto.request.CreateGroupRequest;
import com.zvit.dto.request.JoinGroupRequest;
import com.zvit.dto.response.GroupMemberResponse;
import com.zvit.dto.response.GroupResponse;
import com.zvit.entity.Group;
import com.zvit.entity.GroupMember;
import com.zvit.entity.User;
import com.zvit.exception.BusinessException;
import com.zvit.exception.ResourceNotFoundException;
import com.zvit.exception.UnauthorizedException;
import com.zvit.repository.GroupMemberRepository;
import com.zvit.repository.GroupRepository;
import com.zvit.repository.UserRepository;
import com.zvit.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Користувача не знайдено"));

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
                .build();

        groupMemberRepository.save(adminMember);

        return mapToGroupResponse(group, adminMember);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> getUserGroups(String userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Користувача не знайдено"));

        List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);

        return memberships.stream()
                .map(member -> mapToGroupResponse(member.getGroup(), member))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroupById(String groupId, String userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Групу не знайдено"));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new UnauthorizedException("Ви не є учасником цієї групи"));

        return mapToGroupResponse(group, member);
    }

    @Transactional
    public void addMemberToGroup(String groupId, AddMemberRequest request, String adminUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, adminUserId)
                .orElseThrow(() -> new UnauthorizedException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new UnauthorizedException("Тільки адміністратор може додавати учасників");
        }

        long currentMembers = groupMemberRepository.countByGroupId(groupId);
        if (currentMembers >= group.getMaxMembers()) {
            throw new BusinessException("Досягнуто максимальну кількість учасників");
        }

        User newUser = userRepository.findByPhoneHash(HashUtil.hashPhone(request.getPhone()))
                .orElseThrow(() -> new ResourceNotFoundException("Користувача з таким телефоном не знайдено"));

        if (groupMemberRepository.findByGroupIdAndUserId(groupId, newUser.getId()).isPresent()) {
            throw new BusinessException("Користувач вже є учасником групи");
        }

        GroupMember newMember = GroupMember.builder()
                .group(group)
                .user(newUser)
                .role(GroupMember.Role.MEMBER)
                .build();

        groupMemberRepository.save(newMember);
    }

    @Transactional
    public GroupResponse joinGroupByAccessCode(JoinGroupRequest request, String userId) {
        Group group = groupRepository.findByAccessCode(request.getAccessCode())
                .orElseThrow(() -> new ResourceNotFoundException("Групу з таким кодом не знайдено"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Користувача не знайдено"));

        if (groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId).isPresent()) {
            throw new BusinessException("Ви вже є учасником цієї групи");
        }

        long currentMembers = groupMemberRepository.countByGroupId(group.getId());
        if (currentMembers >= group.getMaxMembers()) {
            throw new BusinessException("Досягнуто максимальну кількість учасників");
        }

        GroupMember newMember = GroupMember.builder()
                .group(group)
                .user(user)
                .role(GroupMember.Role.MEMBER)
                .build();

        groupMemberRepository.save(newMember);

        return mapToGroupResponse(group, newMember);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getGroupMembers(String groupId, String userId) {
        groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new UnauthorizedException("Ви не є учасником цієї групи"));

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);

        return members.stream()
                .map(this::mapToMemberResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeMemberFromGroup(String groupId, String memberUserId, String adminUserId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, adminUserId)
                .orElseThrow(() -> new UnauthorizedException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new UnauthorizedException("Тільки адміністратор може видаляти учасників");
        }

        if (memberUserId.equals(adminUserId)) {
            throw new BusinessException("Використовуйте endpoint /leave для виходу з групи");
        }

        GroupMember memberToRemove = groupMemberRepository.findByGroupIdAndUserId(groupId, memberUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Учасника не знайдено в групі"));

        groupMemberRepository.delete(memberToRemove);
    }

    @Transactional
    public void leaveGroup(String groupId, String userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Групу не знайдено"));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new UnauthorizedException("Ви не є учасником цієї групи"));

        long currentMembers = groupMemberRepository.countByGroupId(groupId);

        if (member.getRole() == GroupMember.Role.ADMIN && currentMembers > 1) {
            throw new BusinessException("Адміністратор не може вийти доки в групі є інші учасники");
        }

        groupMemberRepository.delete(member);

        if (currentMembers == 1) {
            groupRepository.delete(group);
        }
    }

    @Transactional
    public void deleteGroup(String groupId, String userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Групу не знайдено"));

        GroupMember adminMember = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new UnauthorizedException("Ви не є учасником цієї групи"));

        if (adminMember.getRole() != GroupMember.Role.ADMIN) {
            throw new UnauthorizedException("Тільки адміністратор може видалити групу");
        }

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        groupMemberRepository.deleteAll(members);

        groupRepository.delete(group);
    }

    private GroupResponse mapToGroupResponse(Group group, GroupMember member) {
        boolean isAdmin = member.getRole() == GroupMember.Role.ADMIN;
        long currentMembers = groupMemberRepository.countByGroupId(group.getId());

        return GroupResponse.builder()
                .groupId(group.getId())
                .externalName(group.getExternalName())
                .accessCode(isAdmin ? group.getAccessCode() : null)
                .maxMembers(group.getMaxMembers())
                .currentMembers((int) currentMembers)
                .reportType(group.getReportType())
                .userRole(member.getRole().name())
                .createdAt(group.getCreatedAt())
                .build();
    }

    private GroupMemberResponse mapToMemberResponse(GroupMember member) {
        return GroupMemberResponse.builder()
                .userId(member.getUser().getId())
                .name(member.getUser().getName())
                .phone(member.getUser().getPhoneHash())
                .role(member.getRole().name())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}