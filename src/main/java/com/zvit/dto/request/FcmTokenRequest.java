package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FcmTokenRequest {

    @NotBlank(message = "FCM токен обов'язковий")
    private String fcmToken;
}
