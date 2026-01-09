package com.zvit.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class FcmTokenRequest {

    @JsonAlias("token")  // Accepts both "fcmToken" and "token" from JSON
    private String fcmToken;

    private String deviceType; // "ANDROID" | "WEB"

    // Getter alias for PWA compatibility
    public String getToken() {
        return fcmToken;
    }
}
