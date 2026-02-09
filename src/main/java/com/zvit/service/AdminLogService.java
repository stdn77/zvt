package com.zvit.service;

import com.zvit.entity.AdminLog;
import com.zvit.entity.SystemAdmin;
import com.zvit.entity.User;
import com.zvit.repository.AdminLogRepository;
import com.zvit.repository.SystemAdminRepository;
import com.zvit.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminLogService {

    private final AdminLogRepository adminLogRepository;
    private final SystemAdminRepository systemAdminRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    // Хардкодований головний адміністратор
    private static final String MASTER_ADMIN_PHONE = "+380673355870";

    // Позначки для спеціальних випадків
    public static final String BOT_MARKER = "[БОТ]";
    public static final String UNKNOWN_MARKER = "[НЕВІДОМО]";

    @PostConstruct
    public void initMasterAdmin() {
        // Створюємо головного адміна якщо його немає
        if (!systemAdminRepository.existsByPhoneNumber(MASTER_ADMIN_PHONE)) {
            SystemAdmin masterAdmin = SystemAdmin.builder()
                    .phoneNumber(MASTER_ADMIN_PHONE)
                    .addedBy("SYSTEM")
                    .createdAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")))
                    .active(true)
                    .build();
            systemAdminRepository.save(masterAdmin);
            log.info("Master admin created: {}", MASTER_ADMIN_PHONE);
        }
    }

    public boolean isSystemAdmin(String phoneNumber) {
        if (phoneNumber == null) return false;
        String normalized = normalizePhone(phoneNumber);
        return systemAdminRepository.existsByPhoneNumber(normalized);
    }

    public boolean isMasterAdmin(String phoneNumber) {
        if (phoneNumber == null) return false;
        return normalizePhone(phoneNumber).equals(MASTER_ADMIN_PHONE);
    }

    @Transactional
    public SystemAdmin addAdmin(String phoneNumber, String addedByPhone) {
        String normalized = normalizePhone(phoneNumber);

        if (systemAdminRepository.existsByPhoneNumber(normalized)) {
            throw new RuntimeException("Адміністратор з цим номером вже існує");
        }

        SystemAdmin admin = SystemAdmin.builder()
                .phoneNumber(normalized)
                .addedBy(addedByPhone)
                .createdAt(LocalDateTime.now(ZoneId.of("Europe/Kiev")))
                .active(true)
                .build();

        return systemAdminRepository.save(admin);
    }

    @Transactional
    public void removeAdmin(String phoneNumber) {
        String normalized = normalizePhone(phoneNumber);

        if (normalized.equals(MASTER_ADMIN_PHONE)) {
            throw new RuntimeException("Неможливо видалити головного адміністратора");
        }

        SystemAdmin admin = systemAdminRepository.findByPhoneNumber(normalized)
                .orElseThrow(() -> new RuntimeException("Адміністратора не знайдено"));

        admin.setActive(false);
        systemAdminRepository.save(admin);
    }

    public List<SystemAdmin> getAllAdmins() {
        return systemAdminRepository.findByActiveTrue();
    }

    @Async
    @Transactional
    public void logRequest(String ipAddress, String method, String uri, String requestBody,
                          Integer responseStatus, String userId, AdminLog.LogLevel level, String message) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Kiev"));

        String userName = UNKNOWN_MARKER;
        String phoneNumber = UNKNOWN_MARKER;

        // Визначаємо користувача
        if (userId != null) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                userName = user.getName();
                try {
                    phoneNumber = encryptionService.decrypt(user.getPhoneEncrypted());
                } catch (Exception e) {
                    phoneNumber = "[ПОМИЛКА ДЕШИФРУВАННЯ]";
                }
            }
        }

        // Перевірка на бота (по IP або URI)
        String botInfo = detectBot(ipAddress, uri);
        if (botInfo != null) {
            // Розділяємо "[БОТ] Google" на "[БОТ]" і "Google"
            int spaceIndex = botInfo.indexOf("] ");
            if (spaceIndex > 0) {
                userName = botInfo.substring(0, spaceIndex + 1);  // "[БОТ]" або "[СКАНЕР]"
                phoneNumber = botInfo.substring(spaceIndex + 2);   // "Google", "Bing" і т.д.
            } else {
                userName = botInfo;
                phoneNumber = "-";
            }
        }

        // Обмежуємо довжину тіла запиту
        String truncatedBody = requestBody;
        if (truncatedBody != null && truncatedBody.length() > 10000) {
            truncatedBody = truncatedBody.substring(0, 10000) + "... [ОБРІЗАНО]";
        }

        AdminLog logEntry = AdminLog.builder()
                .logDate(now.toLocalDate())
                .logTime(now.toLocalTime())
                .userName(userName)
                .phoneNumber(phoneNumber)
                .ipAddress(ipAddress)
                .logLevel(level)
                .requestMethod(method)
                .requestUri(uri)
                .requestBody(truncatedBody)
                .responseStatus(responseStatus)
                .message(message)
                .build();

        adminLogRepository.save(logEntry);
    }

    /**
     * Визначає бота по IP адресі або URI
     * @return назва бота або null якщо не бот
     */
    private String detectBot(String ipAddress, String uri) {
        // Спочатку перевіряємо по IP
        if (ipAddress != null) {
            String botByIp = detectBotByIp(ipAddress);
            if (botByIp != null) {
                return botByIp;
            }
        }

        // Потім по URI (сканери/хакери)
        if (uri != null) {
            String lowerUri = uri.toLowerCase();
            if (lowerUri.contains("robots.txt") || lowerUri.contains("sitemap")) {
                return "[БОТ] Crawler";
            }
            if (lowerUri.contains(".php") || lowerUri.contains("wp-") ||
                lowerUri.contains("wordpress") || lowerUri.contains("xmlrpc")) {
                return "[СКАНЕР] WordPress";
            }
            if (lowerUri.contains(".env") || lowerUri.contains("phpmyadmin") ||
                lowerUri.contains("admin/config") || lowerUri.contains("actuator")) {
                return "[СКАНЕР] Вразливості";
            }
            if (lowerUri.contains("/sdk") || lowerUri.contains("/HNAP1") ||
                lowerUri.contains("/evox/") || lowerUri.contains("shell") ||
                lowerUri.contains("cgi-bin")) {
                return "[СКАНЕР] Експлойти";
            }
            if (lowerUri.contains("favicon.ico")) {
                return "[БОТ] Favicon";
            }
        }

        return null;
    }

    /**
     * Визначає бота по діапазону IP адрес
     */
    private String detectBotByIp(String ip) {
        if (ip == null) return null;

        // Google (Googlebot)
        if (ip.startsWith("66.249.") || ip.startsWith("74.125.") ||
            ip.startsWith("64.233.") || ip.startsWith("72.14.") ||
            ip.startsWith("209.85.") || ip.startsWith("216.239.")) {
            return "[БОТ] Google";
        }

        // Bing (Bingbot)
        if (ip.startsWith("157.55.") || ip.startsWith("207.46.") ||
            ip.startsWith("40.77.") || ip.startsWith("52.167.")) {
            return "[БОТ] Bing";
        }

        // Yandex
        if (ip.startsWith("5.255.") || ip.startsWith("77.88.") ||
            ip.startsWith("87.250.") || ip.startsWith("93.158.") ||
            ip.startsWith("95.108.") || ip.startsWith("141.8.") ||
            ip.startsWith("178.154.") || ip.startsWith("213.180.")) {
            return "[БОТ] Yandex";
        }

        // Baidu
        if (ip.startsWith("180.76.") || ip.startsWith("220.181.")) {
            return "[БОТ] Baidu";
        }

        // Facebook
        if (ip.startsWith("31.13.") || ip.startsWith("66.220.") ||
            ip.startsWith("69.63.") || ip.startsWith("69.171.") ||
            ip.startsWith("173.252.") || ip.startsWith("179.60.")) {
            return "[БОТ] Facebook";
        }

        // Apple
        if (ip.startsWith("17.")) {
            return "[БОТ] Apple";
        }

        // Amazon AWS (часто сканери)
        if (ip.startsWith("52.") || ip.startsWith("54.") || ip.startsWith("18.")) {
            return "[СКАНЕР] AWS";
        }

        // DigitalOcean (часто сканери)
        if (ip.startsWith("134.122.") || ip.startsWith("167.172.") ||
            ip.startsWith("165.227.") || ip.startsWith("159.65.")) {
            return "[СКАНЕР] DigitalOcean";
        }

        // Semrush
        if (ip.startsWith("185.191.171.")) {
            return "[БОТ] Semrush";
        }

        // Ahrefs
        if (ip.startsWith("54.36.148.") || ip.startsWith("54.36.149.")) {
            return "[БОТ] Ahrefs";
        }

        return null;
    }

    public Page<AdminLog> getLogs(LocalDate dateFrom, LocalDate dateTo, String userName,
                                   String phoneNumber, String ipAddress, AdminLog.LogLevel level,
                                   String method, String status, int page, int size) {
        // Парсимо статус
        Integer statusExact = null;
        Integer statusMin = null;
        Integer statusMax = null;

        if (status != null && !status.isEmpty()) {
            if (status.equals("2xx")) {
                statusMin = 200;
                statusMax = 299;
            } else if (status.equals("4xx")) {
                statusMin = 400;
                statusMax = 499;
            } else if (status.equals("5xx")) {
                statusMin = 500;
                statusMax = 599;
            } else {
                try {
                    statusExact = Integer.parseInt(status);
                } catch (NumberFormatException e) {
                    // Ігноруємо невалідний статус
                }
            }
        }

        return adminLogRepository.findByFilters(
                dateFrom, dateTo, userName, phoneNumber, ipAddress, level, method,
                statusExact, statusMin, statusMax,
                PageRequest.of(page, size)
        );
    }

    @Transactional
    public void cleanOldLogs(int daysToKeep) {
        LocalDate cutoffDate = LocalDate.now(ZoneId.of("Europe/Kiev")).minusDays(daysToKeep);
        adminLogRepository.deleteByLogDateBefore(cutoffDate);
        log.info("Deleted logs older than {}", cutoffDate);
    }

    @Transactional
    public int deleteLogs(LocalDate dateFrom, LocalDate dateTo, String userName,
                          String ipAddress, AdminLog.LogLevel level, String status) {
        // Парсимо статус
        Integer statusExact = null;
        Integer statusMin = null;
        Integer statusMax = null;

        if (status != null && !status.isEmpty()) {
            if (status.equals("2xx")) {
                statusMin = 200;
                statusMax = 299;
            } else if (status.equals("4xx")) {
                statusMin = 400;
                statusMax = 499;
            } else if (status.equals("5xx")) {
                statusMin = 500;
                statusMax = 599;
            } else {
                try {
                    statusExact = Integer.parseInt(status);
                } catch (NumberFormatException e) {
                    // Ігноруємо невалідний статус
                }
            }
        }

        return adminLogRepository.deleteByFilters(
                dateFrom, dateTo, userName, ipAddress, level,
                statusExact, statusMin, statusMax
        );
    }

    @Transactional
    public int deleteAllLogs() {
        return adminLogRepository.deleteAllLogs();
    }

    private String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9+]", "");
        if (!digits.startsWith("+")) {
            if (digits.startsWith("380")) {
                digits = "+" + digits;
            } else if (digits.startsWith("0")) {
                digits = "+38" + digits;
            } else {
                digits = "+380" + digits;
            }
        }
        return digits;
    }
}
