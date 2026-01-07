package com.zvit.config;

import com.zvit.security.JwtAuthenticationFilter;
import com.zvit.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/").permitAll() // Landing page
                .requestMatchers("/privacy-policy").permitAll() // Privacy policy
                .requestMatchers("/terms").permitAll() // Terms of service
                .requestMatchers("/delete-account").permitAll() // Account deletion instructions
                .requestMatchers("/guide").permitAll() // User guide
                .requestMatchers("/app").permitAll() // PWA application
                .requestMatchers("/pwa/**").permitAll() // PWA static files
                .requestMatchers("/icons/**").permitAll() // PWA icons
                .requestMatchers("/manifest.json").permitAll() // PWA manifest
                .requestMatchers("/service-worker.js").permitAll() // Service Worker
                .requestMatchers("/firebase-messaging-sw.js").permitAll() // Firebase Messaging Service Worker
                .requestMatchers("/feature-graphic").permitAll() // Feature graphic for Play Store
                .requestMatchers("/app-icon").permitAll() // App icon for Play Store
                .requestMatchers("/admin/**").permitAll() // Admin QR access (з rate limiting в контролері)
                .requestMatchers("/api/health").permitAll() // Health check endpoint
                .requestMatchers("/api/v1/auth/register").permitAll()
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/reset-password").permitAll()
                .requestMatchers("/api/v1/auth/test").permitAll()
                .requestMatchers("/api/v1/auth/public-key").permitAll()
                .requestMatchers("/api/v1/pwa/login").permitAll() // PWA login without encryption
                .requestMatchers("/api/v1/pwa/register").permitAll() // PWA register without encryption
                .requestMatchers("/error").permitAll()
                .requestMatchers("/web/**").permitAll()
                .requestMatchers("/api/web/**").permitAll() // Web API з session token
                .requestMatchers("/api/web-session/**").permitAll() // QR session status
                .requestMatchers("/css/**").permitAll()
                .requestMatchers("/js/**").permitAll()
                .requestMatchers("/favicon.ico").permitAll()
                .requestMatchers("/api/v1/admin/dashboard/**").permitAll()
                .requestMatchers("/api/v1/admin/member/*/details").permitAll()
                .requestMatchers("/api/v1/admin/**").authenticated()
                .requestMatchers("/api/v1/groups/**").authenticated()
                .requestMatchers("/api/v1/reports/**").authenticated()
                .requestMatchers("/api/v1/pwa/**").authenticated() // PWA API (no encryption)
                .anyRequest().authenticated()
            )
            // Rate limiting фільтр виконується першим
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}