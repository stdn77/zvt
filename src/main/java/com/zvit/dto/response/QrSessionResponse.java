package com.zvit.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrSessionResponse {
    private String sessionToken; // Токен для QR коду
    private String qrUrl; // URL для генерації QR (містить sessionToken)
    private Boolean isAuthorized; // Чи авторизована сесія
    private Long expiresIn; // Скільки секунд залишилось до закінчення
}
