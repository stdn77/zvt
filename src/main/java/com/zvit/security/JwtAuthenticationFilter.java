package com.zvit.security;

import com.zvit.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT Authentication Filter для v1.3
 * Перехоплює всі запити та перевіряє JWT токен
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        // 1. Отримати Authorization header
        String authHeader = request.getHeader("Authorization");
        
        // 2. Якщо немає токену або не Bearer - пропустити
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        try {
            // 3. Витягти токен
            String token = authHeader.substring(7);
            
            // 4. Витягти userId з токену
            String userId = jwtService.extractUserId(token);
            
            // 5. Якщо userId існує і користувач ще не аутентифікований
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                
                // 6. Валідувати токен
                if (jwtService.validateToken(token, userId)) {
                    
                    // 7. Створити Authentication об'єкт
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                    );
                    
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // 8. Встановити в SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            
        } catch (Exception e) {
            // Логувати помилку але не блокувати запит
            logger.error("JWT Authentication error: " + e.getMessage());
        }
        
        // 9. Продовжити фільтрацію
        filterChain.doFilter(request, response);
    }
}
