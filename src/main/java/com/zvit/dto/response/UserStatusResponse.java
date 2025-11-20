package com.zvit.dto.response;

import com.zvit.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusResponse {
    private String userId;
    private String userName;
    private Role role;
    private boolean hasReported;
    private LocalDateTime lastReportAt;
    private String lastReportResponse;
    private String colorHex;
    private Double percentageElapsed;
}