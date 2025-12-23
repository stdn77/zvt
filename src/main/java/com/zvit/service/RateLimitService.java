package com.zvit.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting сервіс для захисту від brute force атак.
 * Обмежує кількість запитів з одного IP за певний період часу.
 */
@Service
public class RateLimitService {

    // Максимальна кількість спроб за вікно часу
    private static final int MAX_ATTEMPTS_LOGIN = 5;        // 5 спроб логіну
    private static final int MAX_ATTEMPTS_REGISTER = 3;    // 3 спроби реєстрації

    // Вікно часу в мілісекундах (1 хвилина)
    private static final long WINDOW_SIZE_MS = 60_000;

    // Час блокування після перевищення ліміту (5 хвилин)
    private static final long BLOCK_DURATION_MS = 5 * 60_000;

    // Зберігаємо спроби по IP
    private final Map<String, RateLimitInfo> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, RateLimitInfo> registerAttempts = new ConcurrentHashMap<>();

    public RateLimitService() {
        // Очищення застарілих записів кожні 5 хвилин
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Перевіряє чи дозволено логін з даного IP
     * @return true якщо запит дозволено
     */
    public boolean isLoginAllowed(String ip) {
        return isAllowed(ip, loginAttempts, MAX_ATTEMPTS_LOGIN);
    }

    /**
     * Перевіряє чи дозволено реєстрацію з даного IP
     * @return true якщо запит дозволено
     */
    public boolean isRegisterAllowed(String ip) {
        return isAllowed(ip, registerAttempts, MAX_ATTEMPTS_REGISTER);
    }

    /**
     * Фіксує спробу логіну
     */
    public void recordLoginAttempt(String ip) {
        recordAttempt(ip, loginAttempts);
    }

    /**
     * Фіксує спробу реєстрації
     */
    public void recordRegisterAttempt(String ip) {
        recordAttempt(ip, registerAttempts);
    }

    /**
     * Скидає лічильник при успішному логіні
     */
    public void resetLoginAttempts(String ip) {
        loginAttempts.remove(ip);
    }

    /**
     * Повертає час до розблокування в секундах (для відповіді клієнту)
     */
    public long getSecondsUntilUnblock(String ip, boolean isLogin) {
        Map<String, RateLimitInfo> attempts = isLogin ? loginAttempts : registerAttempts;
        RateLimitInfo info = attempts.get(ip);

        if (info == null) return 0;

        long now = System.currentTimeMillis();
        if (info.blockedUntil > now) {
            return (info.blockedUntil - now) / 1000;
        }
        return 0;
    }

    private boolean isAllowed(String ip, Map<String, RateLimitInfo> attempts, int maxAttempts) {
        RateLimitInfo info = attempts.get(ip);
        long now = System.currentTimeMillis();

        if (info == null) {
            return true;
        }

        // Перевіряємо чи IP заблоковано
        if (info.blockedUntil > now) {
            return false;
        }

        // Якщо пройшло вікно часу - скидаємо лічильник
        if (now - info.windowStart > WINDOW_SIZE_MS) {
            attempts.remove(ip);
            return true;
        }

        return info.attempts < maxAttempts;
    }

    private void recordAttempt(String ip, Map<String, RateLimitInfo> attempts) {
        long now = System.currentTimeMillis();

        attempts.compute(ip, (key, info) -> {
            if (info == null) {
                return new RateLimitInfo(now, 1, 0);
            }

            // Якщо пройшло вікно часу - починаємо заново
            if (now - info.windowStart > WINDOW_SIZE_MS) {
                return new RateLimitInfo(now, 1, 0);
            }

            int newAttempts = info.attempts + 1;
            long blockedUntil = info.blockedUntil;

            // Визначаємо ліміт на основі типу (login або register)
            int maxAttempts = (attempts == loginAttempts) ? MAX_ATTEMPTS_LOGIN : MAX_ATTEMPTS_REGISTER;

            // Якщо перевищено ліміт - блокуємо
            if (newAttempts >= maxAttempts) {
                blockedUntil = now + BLOCK_DURATION_MS;
            }

            return new RateLimitInfo(info.windowStart, newAttempts, blockedUntil);
        });
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_SIZE_MS - BLOCK_DURATION_MS;

        loginAttempts.entrySet().removeIf(entry ->
            entry.getValue().windowStart < cutoff && entry.getValue().blockedUntil < now);

        registerAttempts.entrySet().removeIf(entry ->
            entry.getValue().windowStart < cutoff && entry.getValue().blockedUntil < now);
    }

    /**
     * Інформація про спроби з одного IP
     */
    private static class RateLimitInfo {
        final long windowStart;     // Початок вікна часу
        final int attempts;         // Кількість спроб
        final long blockedUntil;    // До якого часу заблоковано (0 якщо не заблоковано)

        RateLimitInfo(long windowStart, int attempts, long blockedUntil) {
            this.windowStart = windowStart;
            this.attempts = attempts;
            this.blockedUntil = blockedUntil;
        }
    }
}
