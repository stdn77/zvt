package com.zvit.dto.response;

import com.zvit.entity.Group;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupResponse {
    private String groupId;
    private String externalName;
    private String accessCode;
    private Integer maxMembers;
    private Integer currentMembers;
    private Group.ReportType reportType;
    private String userRole;
    private LocalDateTime createdAt;
}