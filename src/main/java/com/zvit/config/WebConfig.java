package com.zvit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.http.CacheControl;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * Web конфігурація для PWA Service Worker.
 * Додає необхідні заголовки для обмеження scope Service Worker.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Фільтр для додавання Service-Worker-Allowed header.
     * Дозволяє реєструвати SW з /service-worker.js для scope /app/
     */
    @Bean
    public FilterRegistrationBean<Filter> serviceWorkerFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                // Додаємо header для service-worker.js
                if (httpRequest.getRequestURI().equals("/service-worker.js")) {
                    httpResponse.setHeader("Service-Worker-Allowed", "/app/");
                }

                chain.doFilter(request, response);
            }
        });
        registration.addUrlPatterns("/service-worker.js");
        registration.setName("serviceWorkerFilter");
        registration.setOrder(1);
        return registration;
    }
}
