package com.zvit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Фільтр для логування всіх HTTP запитів та відповідей.
 * УВАГА: Тільки для тестування! Видалити у production!
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip logging for health check endpoint
        String uri = request.getRequestURI();
        if (uri.equals("/api/health") || uri.equals("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request and response to cache content
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            // Log request
            logRequest(wrappedRequest);

            // Process request
            filterChain.doFilter(wrappedRequest, wrappedResponse);

        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Log response
            logResponse(wrappedRequest, wrappedResponse, duration);

            // Copy content to actual response
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = queryString != null ? uri + "?" + queryString : uri;

        // Headers
        String headers = Collections.list(request.getHeaderNames()).stream()
                .filter(name -> !name.equalsIgnoreCase("authorization")) // Не логуємо токен повністю
                .map(name -> name + ": " + request.getHeader(name))
                .collect(Collectors.joining("\n    "));

        // Authorization header (masked)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            if (authHeader.length() > 20) {
                authHeader = authHeader.substring(0, 20) + "...";
            }
            headers += "\n    Authorization: " + authHeader;
        }

        log.info("\n" +
                "╔══════════════════════════════════════════════════════════════════════\n" +
                "║ ➡️  INCOMING REQUEST\n" +
                "╠══════════════════════════════════════════════════════════════════════\n" +
                "║ {} {}\n" +
                "║ Client IP: {}\n" +
                "╠══════════════════════════════════════════════════════════════════════\n" +
                "║ Headers:\n" +
                "    {}\n" +
                "╚══════════════════════════════════════════════════════════════════════",
                method, fullUrl,
                getClientIp(request),
                headers);
    }

    private void logResponse(ContentCachingRequestWrapper request,
                             ContentCachingResponseWrapper response,
                             long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();

        // Request body
        String requestBody = getRequestBody(request);

        // Response body
        String responseBody = getResponseBody(response);

        // Truncate if too long
        if (requestBody.length() > 2000) {
            requestBody = requestBody.substring(0, 2000) + "\n... [TRUNCATED]";
        }
        if (responseBody.length() > 2000) {
            responseBody = responseBody.substring(0, 2000) + "\n... [TRUNCATED]";
        }

        String statusEmoji = status >= 200 && status < 300 ? "✅" :
                            status >= 400 && status < 500 ? "⚠️" :
                            status >= 500 ? "❌" : "ℹ️";

        log.info("\n" +
                "╔══════════════════════════════════════════════════════════════════════\n" +
                "║ {} RESPONSE: {} {} - {} ({}ms)\n" +
                "╠══════════════════════════════════════════════════════════════════════\n" +
                "║ 📥 Request Body:\n" +
                "{}\n" +
                "╠══════════════════════════════════════════════════════════════════════\n" +
                "║ 📤 Response Body:\n" +
                "{}\n" +
                "╚══════════════════════════════════════════════════════════════════════",
                statusEmoji, method, uri, status, duration,
                formatJson(requestBody),
                formatJson(responseBody));
    }

    private String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return "[empty]";
    }

    private String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            return new String(content, StandardCharsets.UTF_8);
        }
        return "[empty]";
    }

    private String formatJson(String json) {
        if (json == null || json.isEmpty() || json.equals("[empty]")) {
            return "    [empty]";
        }
        // Simple indent for readability
        return "    " + json.replace("\n", "\n    ");
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
