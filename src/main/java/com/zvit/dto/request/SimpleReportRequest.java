package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleReportRequest {

    @NotBlank(message = "ID групи обов'язковий")
    private String groupId;

    @NotBlank(message = "Відповідь обов'язкова")
    private String simpleResponse;

    private String comment;
}
