package com.zvit.dto.response;

import com.zvit.entity.enums.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private String reportId;
    private String userId;
    private String userName;
    private String groupId;
    private String groupName;
    private ReportType reportType;
    private String simpleResponse;
    private String comment;
    private LocalDateTime submittedAt;
}