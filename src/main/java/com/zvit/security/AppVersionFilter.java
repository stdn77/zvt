package com.zvit.security;

import com.zvit.entity.User;
import com.zvit.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Фільтр для відстеження версії додатку користувачів.
 * Зчитує X-App-Version та X-App-Platform headers і оновлює дані користувача.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppVersionFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Отримати версію та платформу з headers
            String appVersion = request.getHeader("X-App-Version");
            String appPlatform = request.getHeader("X-App-Platform");

            // Якщо є інформація про версію - спробувати оновити користувача
            if (appVersion != null && !appVersion.isEmpty()) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();

                if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String) {
                    String userId = (String) auth.getPrincipal();
                    updateUserAppInfo(userId, appVersion, appPlatform);
                }
            }
        } catch (Exception e) {
            // Не блокувати запит при помилці оновлення версії
            log.debug("Error updating app version info: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void updateUserAppInfo(String userId, String appVersion, String appPlatform) {
        userRepository.findById(userId).ifPresent(user -> {
            boolean needsUpdate = false;

            // Оновити версію якщо змінилась
            if (!appVersion.equals(user.getAppVersion())) {
                user.setAppVersion(appVersion);
                needsUpdate = true;
            }

            // Оновити платформу якщо змінилась
            if (appPlatform != null && !appPlatform.equals(user.getAppPlatform())) {
                user.setAppPlatform(appPlatform);
                needsUpdate = true;
            }

            // Завжди оновлювати lastSeenAt
            user.setLastSeenAt(LocalDateTime.now(KYIV_ZONE));
            needsUpdate = true;

            if (needsUpdate) {
                userRepository.save(user);
                log.debug("Updated app info for user {}: version={}, platform={}",
                    userId, appVersion, appPlatform);
            }
        });
    }
}
