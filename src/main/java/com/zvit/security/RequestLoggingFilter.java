package com.zvit.security;

import com.zvit.entity.AdminLog;
import com.zvit.service.AdminLogService;
import com.zvit.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Фільтр для логування всіх HTTP запитів у базу даних.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final AdminLogService adminLogService;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Пропускаємо статичні ресурси та health check
        if (shouldSkip(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            saveLog(wrappedRequest, wrappedResponse);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private boolean shouldSkip(String uri) {
        return uri.equals("/api/health") ||
               uri.equals("/actuator/health") ||
               uri.startsWith("/static/") ||
               uri.startsWith("/pwa/") ||
               uri.startsWith("/css/") ||
               uri.startsWith("/js/") ||
               uri.startsWith("/images/") ||
               uri.endsWith(".css") ||
               uri.endsWith(".js") ||
               uri.endsWith(".png") ||
               uri.endsWith(".jpg") ||
               uri.endsWith(".ico") ||
               uri.endsWith(".svg") ||
               uri.endsWith(".woff") ||
               uri.endsWith(".woff2");
    }

    private void saveLog(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response) {
        try {
            String method = request.getMethod();
            String uri = request.getRequestURI();
            String queryString = request.getQueryString();
            String fullUri = queryString != null ? uri + "?" + queryString : uri;
            String ipAddress = getClientIp(request);
            int status = response.getStatus();

            // Отримуємо тіло запиту
            String requestBody = getRequestBody(request);

            // Визначаємо userId з JWT токена
            String userId = extractUserId(request);

            // Визначаємо рівень логу
            AdminLog.LogLevel level = AdminLog.LogLevel.INFO;
            String message = null;

            if (status >= 500) {
                level = AdminLog.LogLevel.ERROR;
                message = "Server Error";
            } else if (status >= 400) {
                level = AdminLog.LogLevel.WARN;
                message = "Client Error";
            }

            // Зберігаємо лог асинхронно
            adminLogService.logRequest(ipAddress, method, fullUri, requestBody,
                    status, userId, level, message);

        } catch (Exception e) {
            // Не блокуємо запит якщо логування не вдалося
        }
    }

    private String extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                return jwtService.extractUserId(token);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            String body = new String(content, StandardCharsets.UTF_8);
            // Маскуємо паролі
            body = maskSensitiveData(body);
            return body;
        }
        return null;
    }

    private String maskSensitiveData(String body) {
        if (body == null) return null;
        // Маскуємо паролі в JSON
        body = body.replaceAll("\"password\"\\s*:\\s*\"[^\"]*\"", "\"password\":\"***\"");
        body = body.replaceAll("\"newPassword\"\\s*:\\s*\"[^\"]*\"", "\"newPassword\":\"***\"");
        body = body.replaceAll("\"oldPassword\"\\s*:\\s*\"[^\"]*\"", "\"oldPassword\":\"***\"");
        return body;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
