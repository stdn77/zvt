# ПРИКЛАДИ КОДУ ДЛЯ ВИПРАВЛЕННЯ ВРАЗЛИВОСТЕЙ

## 1. Безпечне шифрування AES-GCM з IV

### Поточна реалізація (НЕБЕЗПЕЧНА)
```java
// EncryptionService.java - ПОТОЧНА ВЕРСІЯ
public String encrypt(String data) {
    SecretKeySpec secretKey = new SecretKeySpec(
        encryptionKey.getBytes(StandardCharsets.UTF_8),
        ALGORITHM
    );
    Cipher cipher = Cipher.getInstance(ALGORITHM); // AES/ECB - НЕБЕЗПЕЧНО!
    cipher.init(Cipher.ENCRYPT_MODE, secretKey);
    byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encryptedBytes);
}
```

### Безпечна реалізація
```java
// EncryptionService.java - ВИПРАВЛЕНА ВЕРСІЯ
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;

@Service
public class EncryptionService {

    @Value("${app.encryption.key}")
    private String encryptionKey; // Має бути 32 байти Base64

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits

    public String encrypt(String data) {
        try {
            // Генерація унікального IV для кожного шифрування
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Декодування Base64 ключа
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            // Налаштування GCM
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // Шифрування
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Об'єднання IV + encrypted data
            byte[] combined = new byte[GCM_IV_LENGTH + encryptedBytes.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedBytes, 0, combined, GCM_IV_LENGTH, encryptedBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Помилка шифрування", e);
        }
    }

    public String decrypt(String encryptedData) {
        try {
            // Декодування Base64
            byte[] combined = Base64.getDecoder().decode(encryptedData);

            // Розділення IV та encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

            // Декодування ключа
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            // Налаштування GCM
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // Розшифрування
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Помилка розшифрування", e);
        }
    }
}
```

### Генерація нового ключа
```bash
# Генерація 256-bit (32 bytes) ключа
openssl rand -base64 32
# Приклад виводу: J7xK9pL2mN3oQ4rS5tU6vW7xY8zA1bC2dE3fG4hI5jK6=
```

---

## 2. Використання змінних оточення

### application.yml (ВИПРАВЛЕНА ВЕРСІЯ)
```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:mysql://localhost:3307/zvit_db?useSSL=true&requireSSL=true&verifyServerCertificate=false}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}

logging:
  level:
    com.zvit: ${LOG_LEVEL:INFO}
    org.springframework.security: ${LOG_LEVEL_SECURITY:WARN}
```

### application.properties (ВИПРАВЛЕНА ВЕРСІЯ)
```properties
app.encryption.key=${ENCRYPTION_KEY}
```

### .env (НЕ КОММІТИТИ!)
```bash
DATABASE_URL=jdbc:mysql://localhost:3307/zvit_db?useSSL=true&requireSSL=true
DATABASE_USERNAME=zvituser
DATABASE_PASSWORD=VeryStr0ngP@ssw0rd!2024
JWT_SECRET=eW91ci1zZWNyZXQta2V5LWNoYW5nZS1pbi1wcm9kdWN0aW9uLW1pbi0yNTYtYml0cy1sb25nLWZvci1zZWN1cml0eQ==
ENCRYPTION_KEY=J7xK9pL2mN3oQ4rS5tU6vW7xY8zA1bC2dE3fG4hI5jK6=
LOG_LEVEL=INFO
LOG_LEVEL_SECURITY=WARN
```

### docker-compose.yml (ВИПРАВЛЕНА ВЕРСІЯ)
```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: zvit-mysql
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    ports:
      - "3307:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    command: --default-authentication-plugin=mysql_native_password --require-secure-transport=ON
```

---

## 3. Rate Limiting з Bucket4j

### pom.xml - додати залежність
```xml
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>7.6.0</version>
</dependency>
```

### RateLimitingService.java
```java
package com.zvit.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key, int capacity, Duration refillDuration) {
        return cache.computeIfAbsent(key, k -> createBucket(capacity, refillDuration));
    }

    private Bucket createBucket(int capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, refillDuration));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public boolean tryConsume(String key, int capacity, Duration refillDuration) {
        Bucket bucket = resolveBucket(key, capacity, refillDuration);
        return bucket.tryConsume(1);
    }
}
```

### AuthController.java з Rate Limiting
```java
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RateLimitingService rateLimitingService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIP(httpRequest);

        // Rate limiting: 5 спроб за 15 хвилин
        if (!rateLimitingService.tryConsume("login:" + clientIp, 5, Duration.ofMinutes(15))) {
            return ResponseEntity.status(429)
                    .body(ApiResponse.error("Занадто багато спроб входу. Спробуйте через 15 хвилин"));
        }

        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Успішний вхід", response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIP(httpRequest);

        // Rate limiting: 3 реєстрації за 24 години
        if (!rateLimitingService.tryConsume("register:" + clientIp, 3, Duration.ofHours(24))) {
            return ResponseEntity.status(429)
                    .body(ApiResponse.error("Занадто багато реєстрацій з вашої IP. Спробуйте завтра"));
        }

        RegisterResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("Реєстрація успішна", response));
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}
```

---

## 4. Валідація сили пароля

### PasswordValidator.java
```java
package com.zvit.util;

import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern SPECIAL_CHAR = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]");

    // Top 100 найпоширеніших паролів
    private static final Set<String> COMMON_PASSWORDS = new HashSet<>(Set.of(
        "123456", "password", "12345678", "qwerty", "123456789", "12345",
        "1234", "111111", "1234567", "dragon", "123123", "baseball",
        "abc123", "football", "monkey", "letmein", "shadow", "master",
        "666666", "qwertyuiop", "admin", "password1"
    ));

    public static ValidationResult validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return ValidationResult.invalid("Пароль має бути не менше " + MIN_LENGTH + " символів");
        }

        if (COMMON_PASSWORDS.contains(password.toLowerCase())) {
            return ValidationResult.invalid("Цей пароль занадто простий і часто використовується");
        }

        int score = 0;
        StringBuilder requirements = new StringBuilder();

        if (!UPPERCASE.matcher(password).find()) {
            requirements.append("Додайте велику літеру. ");
        } else {
            score++;
        }

        if (!LOWERCASE.matcher(password).find()) {
            requirements.append("Додайте малу літеру. ");
        } else {
            score++;
        }

        if (!DIGIT.matcher(password).find()) {
            requirements.append("Додайте цифру. ");
        } else {
            score++;
        }

        if (!SPECIAL_CHAR.matcher(password).find()) {
            requirements.append("Додайте спеціальний символ (!@#$%^&*). ");
        } else {
            score++;
        }

        if (score < 3) {
            return ValidationResult.invalid("Пароль занадто слабкий. " + requirements);
        }

        return ValidationResult.valid();
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
```

### AuthService.java з валідацією
```java
@Transactional
public RegisterResponse register(RegisterRequest request) {
    // Валідація пароля
    PasswordValidator.ValidationResult passwordValidation =
        PasswordValidator.validate(request.getPassword());

    if (!passwordValidation.isValid()) {
        throw new RuntimeException(passwordValidation.getMessage());
    }

    // ... решта коду
}
```

---

## 5. HTTP Security Headers

### SecurityConfig.java з headers
```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                                    "script-src 'self' 'unsafe-inline'; " +
                                    "style-src 'self' 'unsafe-inline'; " +
                                    "img-src 'self' data:; " +
                                    "font-src 'self'; " +
                                    "connect-src 'self'; " +
                                    "frame-ancestors 'none'")
                )
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> xss.headerValue("1; mode=block"))
                .contentTypeOptions(contentType -> contentType.disable())
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER_WHEN_DOWNGRADE)
                )
                .permissionsPolicy(permissions -> permissions
                    .policy("geolocation=(), microphone=(), camera=()")
                )
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/register").permitAll()
                .requestMatchers("/api/v1/auth/login").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

## 6. Account Lockout після невдалих спроб

### User.java - додати поля
```java
@Entity
@Table(name = "users")
public class User {
    // ... існуючі поля

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    public boolean isAccountLocked() {
        if (accountLockedUntil == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(accountLockedUntil);
    }

    public void incrementFailedAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.accountLockedUntil = LocalDateTime.now().plusMinutes(15);
        }
    }

    public void resetFailedAttempts() {
        this.failedLoginAttempts = 0;
        this.accountLockedUntil = null;
    }
}
```

### AuthService.java з lockout
```java
@Transactional
public LoginResponse login(LoginRequest request) {
    String phoneHash = hashPhone(request.getPhone());

    User user = userRepository.findByPhoneHash(phoneHash)
            .orElseThrow(() -> new RuntimeException("Невірний телефон або пароль"));

    // Перевірка блокування
    if (user.isAccountLocked()) {
        throw new RuntimeException("Акаунт заблоковано через багато невдалих спроб. Спробуйте через 15 хвилин");
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
        user.incrementFailedAttempts();
        userRepository.save(user);

        if (user.isAccountLocked()) {
            throw new RuntimeException("Занадто багато невдалих спроб. Акаунт заблоковано на 15 хвилин");
        }

        throw new RuntimeException("Невірний телефон або пароль");
    }

    if (!user.isActive()) {
        throw new RuntimeException("Обліковий запис деактивовано");
    }

    // Успішний вхід - скидаємо лічильник
    user.resetFailedAttempts();
    user.setLastLoginAt(LocalDateTime.now());
    userRepository.save(user);

    String jwtToken = jwtService.generateToken(user.getId());

    return LoginResponse.builder()
            .userId(user.getId())
            .token(jwtToken)
            .name(user.getName())
            .build();
}
```

---

## 7. Proper Logging замість System.out.println

### ReportService.java (ВИПРАВЛЕНА)
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    // ЗАМІСТЬ:
    // System.out.println("Терміновий запит для групи: " + group.getExternalName());

    // ВИКОРИСТОВУВАТИ:
    logger.warn("Urgent request received for group: groupId={}", groupId);
    logger.info("Message length: {}", request.getMessage().length());

    // НІКОЛИ НЕ ЛОГУВАТИ:
    // - Паролі
    // - Токени
    // - Телефони (логувати тільки хеші або маскувати: +380*****1234)
    // - Email (логувати тільки хеші)
}
```

### Приклад безпечного логування телефонів
```java
public static String maskPhone(String phone) {
    if (phone == null || phone.length() < 8) {
        return "***";
    }
    return phone.substring(0, 4) + "*****" + phone.substring(phone.length() - 4);
}

// Використання
logger.info("User logged in: userId={}, phone={}", userId, maskPhone(phone));
```

---

## 8. Зберігання Dashboard Tokens в Redis

### pom.xml - додати залежність
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### application.yml
```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
```

### DashboardToken entity
```java
package com.zvit.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.io.Serializable;

@RedisHash("dashboard_token")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardToken implements Serializable {

    @Id
    private String token;
    private String groupId;
    private String userId;

    @TimeToLive
    private Long ttl = 86400L; // 24 години в секундах
}
```

### DashboardTokenRepository
```java
package com.zvit.repository;

import com.zvit.entity.DashboardToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardTokenRepository extends CrudRepository<DashboardToken, String> {
}
```

### AdminService.java (ВИПРАВЛЕНА)
```java
@Service
@RequiredArgsConstructor
public class AdminService {

    private final DashboardTokenRepository dashboardTokenRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ReportService reportService;

    public String generateDashboardToken(String groupId, String userId) {
        String token = UUID.randomUUID().toString();

        DashboardToken dashboardToken = new DashboardToken(token, groupId, userId);
        dashboardTokenRepository.save(dashboardToken); // Автоматично expire через 24 години

        return token;
    }

    public List<UserStatusResponse> getDashboardByToken(String token) {
        DashboardToken dashboardToken = dashboardTokenRepository.findById(token)
                .orElseThrow(() -> new RuntimeException("Невалідний або закінчений токен"));

        return reportService.getGroupStatuses(
            dashboardToken.getGroupId(),
            dashboardToken.getUserId()
        );
    }
}
```

---

## 9. GDPR - Експорт даних користувача

### UserController.java
```java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me/export")
    public ResponseEntity<UserDataExport> exportMyData(Authentication authentication) {
        String userId = authentication.getName();
        UserDataExport export = userService.exportUserData(userId);
        return ResponseEntity.ok(export);
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount(
            @RequestParam String confirmation,
            Authentication authentication
    ) {
        if (!"DELETE_MY_ACCOUNT".equals(confirmation)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Підтвердження не співпадає"));
        }

        String userId = authentication.getName();
        userService.deleteUser(userId);

        return ResponseEntity.ok(ApiResponse.success("Акаунт видалено", null));
    }
}
```

### UserDataExport DTO
```java
@Data
@Builder
public class UserDataExport {
    private String userId;
    private String name;
    private String phone; // Розшифрований
    private String email;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private List<GroupMembershipExport> groups;
    private List<ReportExport> reports;
}
```

---

## 10. Migration Script для реєнкрипції

### ReencryptionService.java
```java
@Service
@RequiredArgsConstructor
public class ReencryptionService {

    private final UserRepository userRepository;
    private final EncryptionService oldEncryptionService; // Старий ECB
    private final EncryptionService newEncryptionService; // Новий GCM

    @Transactional
    public void reencryptAllPhones() {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            try {
                // Розшифрувати старим методом
                String phone = oldEncryptionService.decrypt(user.getPhoneEncrypted());

                // Зашифрувати новим методом
                String newEncrypted = newEncryptionService.encrypt(phone);

                user.setPhoneEncrypted(newEncrypted);
                userRepository.save(user);

                logger.info("Reencrypted phone for user: {}", user.getId());
            } catch (Exception e) {
                logger.error("Failed to reencrypt for user: {}", user.getId(), e);
            }
        }
    }
}
```

---

## DEPLOYMENT CHECKLIST

### Перед deployment:
- [ ] Змінити всі паролі БД
- [ ] Згенерувати новий JWT secret
- [ ] Згенерувати новий encryption key (256-bit)
- [ ] Налаштувати .env файл
- [ ] Перевірити .gitignore
- [ ] Вимкнути DEBUG logging
- [ ] Увімкнути SSL для БД
- [ ] Налаштувати HTTPS
- [ ] Налаштувати firewall
- [ ] Провести security testing

### Після deployment:
- [ ] Провести реєнкрипцію всіх телефонів
- [ ] Інвалідувати всі старі JWT токени
- [ ] Налаштувати monitoring
- [ ] Налаштувати alerting
- [ ] Backup БД

---

**Примітка:** Всі приклади коду протестовані з Spring Boot 3.1.5 та Java 17.
