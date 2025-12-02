package com.zvit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String userId;
    private String token;
    private String name;
    private String phone;
    private String email;
    private LocalDateTime serverTime;  // Серверний час для синхронізації
    private String timezone;           // Часова зона сервера
}