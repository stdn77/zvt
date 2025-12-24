package com.zvit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zvit.service.RateLimitService;
import com.zvit.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Фільтр для rate limiting на auth endpoints.
 * Захищає від brute force атак на логін та реєстрацію.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Перевіряємо тільки POST запити на login/register
        if (!"POST".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        if (LOGIN_PATH.equals(path)) {
            if (!rateLimitService.isLoginAllowed(clientIp)) {
                sendRateLimitResponse(response, clientIp, true);
                return;
            }
            rateLimitService.recordLoginAttempt(clientIp);
        } else if (REGISTER_PATH.equals(path)) {
            if (!rateLimitService.isRegisterAllowed(clientIp)) {
                sendRateLimitResponse(response, clientIp, false);
                return;
            }
            rateLimitService.recordRegisterAttempt(clientIp);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Отримує реальний IP клієнта (враховуючи proxy)
     */
    private String getClientIp(HttpServletRequest request) {
        // Перевіряємо X-Forwarded-For (якщо за reverse proxy)
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // Беремо перший IP зі списку
            return xff.split(",")[0].trim();
        }

        // Перевіряємо X-Real-IP
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * Відправляє відповідь про перевищення ліміту
     */
    private void sendRateLimitResponse(HttpServletResponse response, String ip, boolean isLogin)
            throws IOException {

        long secondsUntilUnblock = rateLimitService.getSecondsUntilUnblock(ip, isLogin);
        String message = String.format(
            "Занадто багато спроб. Спробуйте через %d секунд.",
            secondsUntilUnblock
        );

        log.warn("Rate limit exceeded for IP: {} on {} endpoint. Blocked for {} seconds.",
                ip, isLogin ? "login" : "register", secondsUntilUnblock);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(secondsUntilUnblock));

        ApiResponse<Void> apiResponse = ApiResponse.error(message);
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
