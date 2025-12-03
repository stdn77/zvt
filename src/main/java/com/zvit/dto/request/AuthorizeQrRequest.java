package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizeQrRequest {

    @NotBlank(message = "Session token обов'язковий")
    private String sessionToken; // Токен з QR коду

    @NotNull(message = "Group ID обов'язковий")
    private Long groupId; // ID групи для перегляду звітів
}
