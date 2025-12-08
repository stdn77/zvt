package com.zvit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @Column(length = 36)
    private String id;
    
    @Column(name = "phone_hash", nullable = false, unique = true, length = 64)
    private String phoneHash;
    
    @Column(name = "phone_encrypted", nullable = false, length = 500)
    private String phoneEncrypted;

    @Column(name = "email_hash", unique = true, length = 64)
    private String emailHash;

    @Column(name = "email_encrypted", length = 500)
    private String emailEncrypted;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;
    
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;
    
    @Column(name = "is_active", nullable = false)
    private boolean active;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;
}