package com.zvit.controller;

import com.zvit.dto.request.AddMemberRequest;
import com.zvit.dto.request.ChangeRoleRequest;
import com.zvit.dto.request.CreateGroupRequest;
import com.zvit.dto.request.JoinGroupRequest;
import com.zvit.dto.response.EncryptedData;
import com.zvit.dto.response.GroupMemberResponse;
import com.zvit.dto.response.GroupResponse;
import com.zvit.entity.GroupMember;
import com.zvit.service.GroupService;
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
 * GroupController v1.4
 * - Використовує JWT Authentication
 * - userId береться з SecurityContext
 * - Чутливі дані шифруються AES
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final ResponseEncryptionService encryptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName(); // Отримуємо з JWT
        GroupResponse response = groupService.createGroup(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Групу створено", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<EncryptedData>> getUserGroups(
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<GroupResponse> groups = groupService.getUserGroups(userId);
        // Шифруємо чутливі дані
        String encryptedPayload = encryptionService.encryptObject(groups);
        return ResponseEntity.ok(ApiResponse.success("Групи отримано", EncryptedData.of(encryptedPayload)));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<EncryptedData>> getGroupById(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        GroupResponse response = groupService.getGroupById(groupId, userId);
        String encryptedPayload = encryptionService.encryptObject(response);
        return ResponseEntity.ok(ApiResponse.success("Групу знайдено", EncryptedData.of(encryptedPayload)));
    }

    @PostMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<Void>> addMember(
            @PathVariable String groupId,
            @Valid @RequestBody AddMemberRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        groupService.addMemberToGroup(groupId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Учасника додано", null));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<EncryptedData>> joinGroup(
            @Valid @RequestBody JoinGroupRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        GroupResponse response = groupService.joinGroupByAccessCode(request, userId);
        String encryptedPayload = encryptionService.encryptObject(response);
        return ResponseEntity.ok(ApiResponse.success("Ви приєдналися до групи", EncryptedData.of(encryptedPayload)));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<EncryptedData>> getGroupMembers(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<GroupMemberResponse> members = groupService.getGroupMembers(groupId, userId);
        String encryptedPayload = encryptionService.encryptObject(members);
        return ResponseEntity.ok(ApiResponse.success("Учасники отримано", EncryptedData.of(encryptedPayload)));
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable String groupId,
            @PathVariable String memberId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        groupService.removeMemberFromGroup(groupId, memberId, userId);
        return ResponseEntity.ok(ApiResponse.success("Учасника видалено", null));
    }

    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveGroup(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        groupService.leaveGroup(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Ви вийшли з групи", null));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        groupService.deleteGroup(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Групу видалено", null));
    }

    @PutMapping("/{groupId}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<Void>> changeUserRole(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @Valid @RequestBody ChangeRoleRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        GroupMember.Role newRole = GroupMember.Role.valueOf(request.getRole());
        groupService.changeUserRole(groupId, memberId, newRole, userId);
        return ResponseEntity.ok(ApiResponse.success("Роль користувача змінено", null));
    }

    @PutMapping("/{groupId}/settings")
    public ResponseEntity<ApiResponse<Void>> updateGroupSettings(
            @PathVariable String groupId,
            @Valid @RequestBody com.zvit.dto.request.UpdateGroupSettingsRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        groupService.updateGroupSettings(groupId, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Налаштування групи оновлено", null));
    }

    @PostMapping("/{groupId}/members/{memberId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveMember(
            @PathVariable String groupId,
            @PathVariable String memberId,
            Authentication authentication
    ) {
        String adminUserId = authentication.getName();
        groupService.approveMember(groupId, memberId, adminUserId);
        return ResponseEntity.ok(ApiResponse.success("Учасника затверджено", null));
    }

    @PostMapping("/{groupId}/members/{memberId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectMember(
            @PathVariable String groupId,
            @PathVariable String memberId,
            Authentication authentication
    ) {
        String adminUserId = authentication.getName();
        groupService.rejectMember(groupId, memberId, adminUserId);
        return ResponseEntity.ok(ApiResponse.success("Заявку на приєднання відхилено", null));
    }
}
