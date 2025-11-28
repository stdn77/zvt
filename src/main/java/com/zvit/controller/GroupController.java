package com.zvit.controller;

import com.zvit.dto.request.AddMemberRequest;
import com.zvit.dto.request.ChangeRoleRequest;
import com.zvit.dto.request.CreateGroupRequest;
import com.zvit.dto.request.JoinGroupRequest;
import com.zvit.dto.response.GroupMemberResponse;
import com.zvit.dto.response.GroupResponse;
import com.zvit.entity.GroupMember;
import com.zvit.service.GroupService;
import com.zvit.util.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GroupController v1.3
 * - Використовує JWT Authentication
 * - userId береться з SecurityContext
 */
@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

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
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getUserGroups(
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<GroupResponse> groups = groupService.getUserGroups(userId);
        return ResponseEntity.ok(ApiResponse.success("Групи отримано", groups));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroupById(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        GroupResponse response = groupService.getGroupById(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Групу знайдено", response));
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
    public ResponseEntity<ApiResponse<GroupResponse>> joinGroup(
            @Valid @RequestBody JoinGroupRequest request,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        GroupResponse response = groupService.joinGroupByAccessCode(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Ви приєдналися до групи", response));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<ApiResponse<List<GroupMemberResponse>>> getGroupMembers(
            @PathVariable String groupId,
            Authentication authentication
    ) {
        String userId = authentication.getName();
        List<GroupMemberResponse> members = groupService.getGroupMembers(groupId, userId);
        return ResponseEntity.ok(ApiResponse.success("Учасники отримано", members));
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
}
