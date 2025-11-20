package com.zvit.service;

import com.zvit.dto.request.LoginRequest;
import com.zvit.dto.request.RegisterRequest;
import com.zvit.dto.response.LoginResponse;
import com.zvit.dto.response.RegisterResponse;
import com.zvit.entity.User;
import com.zvit.exception.BusinessException;
import com.zvit.exception.UnauthorizedException;
import com.zvit.exception.ValidationException;
import com.zvit.repository.UserRepository;
import com.zvit.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EncryptionService encryptionService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!isValidPhone(request.getPhone())) {
            throw new ValidationException("Невірний формат телефону");
        }

        if (request.getEmail() != null && !isValidEmail(request.getEmail())) {
            throw new ValidationException("Невірний формат email");
        }

        String phoneHash = HashUtil.hashPhone(request.getPhone());
        String phoneEncrypted = encryptionService.encrypt(request.getPhone());
        String emailHash = request.getEmail() != null ? HashUtil.hashEmail(request.getEmail()) : null;

        if (userRepository.existsByPhoneHash(phoneHash)) {
            throw new BusinessException("Користувач з таким телефоном вже існує");
        }

        if (emailHash != null && userRepository.existsByEmailHash(emailHash)) {
            throw new BusinessException("Користувач з таким email вже існує");
        }

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .phoneHash(phoneHash)
                .phoneEncrypted(phoneEncrypted)
                .emailHash(emailHash)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phoneVerified(false)
                .emailVerified(false)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.save(user);

        return RegisterResponse.builder()
                .userId(user.getId())
                .phoneVerificationRequired(true)
                .emailVerificationRequired(emailHash != null)
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String phoneHash = HashUtil.hashPhone(request.getPhone());

        User user = userRepository.findByPhoneHash(phoneHash)
                .orElseThrow(() -> new UnauthorizedException("Невірний телефон або пароль"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Невірний телефон або пароль");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("Обліковий запис деактивовано");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String jwtToken = jwtService.generateToken(user.getId(), request.getPhone());

        return LoginResponse.builder()
                .userId(user.getId())
                .token(jwtToken)
                .name(user.getName())
                .build();
    }

    public String test() {
        return "Auth API працює! Версія: 1.5 (Оптимізовано)";
    }

    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^\\+\\d{10,14}$");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}