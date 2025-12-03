package com.zvit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberResponse {
    private String userId;
    private String name;
    private String phoneNumber; // Тільки для адміністраторів, null для інших
    private String role;
    private String status; // PENDING, ACCEPTED, REJECTED
    private LocalDateTime joinedAt;
}