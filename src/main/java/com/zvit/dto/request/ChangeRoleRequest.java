package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ChangeRoleRequest {

    @NotBlank(message = "Роль обов'язкова")
    @Pattern(regexp = "^(ADMIN|MEMBER)$", message = "Роль має бути ADMIN або MEMBER")
    private String role;
}
