package com.zvit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "qr_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_token", unique = true, nullable = false, length = 64)
    private String sessionToken; // Унікальний токен сесії для QR коду

    @Column(name = "user_id")
    private Long userId; // ID користувача який авторизувався (null до авторизації)

    @Column(name = "group_id")
    private Long groupId; // ID групи для перегляду звітів (null до авторизації)

    @Column(name = "is_authorized", nullable = false)
    @Builder.Default
    private Boolean isAuthorized = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // Час закінчення сесії (5 хвилин для QR, 24 години після авторизації)

    @Column(name = "authorized_at")
    private LocalDateTime authorizedAt; // Час коли був авторизований

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt; // Останнє звернення до сесії
}
