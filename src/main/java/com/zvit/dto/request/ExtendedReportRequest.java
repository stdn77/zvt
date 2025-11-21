package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtendedReportRequest {

    @NotBlank(message = "ID групи обов'язковий")
    private String groupId;

    private String field1;
    private String field2;
    private String field3;
    private String field4;
    private String field5;

    private String comment;
}
