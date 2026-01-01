package com.zvit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FcmTokenRequest {

    @NotBlank(message = "FCM токен обов'язковий")
    private String fcmToken;

    private String deviceType; // "ANDROID" | "WEB"

    // Alias for PWA compatibility
    public String getToken() {
        return fcmToken;
    }

    public void setToken(String token) {
        this.fcmToken = token;
    }
}
