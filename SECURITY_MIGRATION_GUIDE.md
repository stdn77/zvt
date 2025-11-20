# ІНСТРУКЦІЯ З ВПРОВАДЖЕННЯ ВИПРАВЛЕНЬ БЕЗПЕКИ

## ОГЛЯД

Цей документ містить покрокову інструкцію для впровадження виправлень безпеки в проект ZVIT.

**⚠️ ВАЖЛИВО:** Виконуйте всі кроки в зазначеній послідовності!

---

## ЕТАП 1: ПІДГОТОВКА (30 хвилин)

### 1.1 Backup поточної системи

```bash
# Backup бази даних
docker exec zvit-mysql mysqldump -u root -pzvitpass123 zvit_db > backup_$(date +%Y%m%d_%H%M%S).sql

# Backup конфігураційних файлів
cp src/main/resources/application.yml src/main/resources/application.yml.backup
cp src/main/resources/application.properties src/main/resources/application.properties.backup
```

### 1.2 Створити .env файл

```bash
# Створити .env з шаблону
cp .env.example .env
```

**Відредагувати .env:**
```bash
# Згенерувати новий JWT secret (256 біт)
JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')
echo "JWT_SECRET=$JWT_SECRET" >> .env

# Згенерувати новий encryption key (256 біт)
ENCRYPTION_KEY=$(openssl rand -base64 32)
echo "ENCRYPTION_KEY=$ENCRYPTION_KEY" >> .env

# Згенерувати новий пароль БД
DB_PASSWORD=$(openssl rand -base64 24 | tr -d '/+' | cut -c1-20)
echo "DATABASE_PASSWORD=$DB_PASSWORD" >> .env
```

Повний .env файл:
```env
# Database
DATABASE_URL=jdbc:mysql://localhost:3307/zvit_db?useSSL=true&requireSSL=true&verifyServerCertificate=false
DATABASE_USERNAME=zvituser
DATABASE_PASSWORD=<згенерований_пароль>

# JWT
JWT_SECRET=<згенерований_base64_secret>
JWT_EXPIRATION=86400000

# Encryption
ENCRYPTION_KEY=<згенерований_base64_key>

# Redis (якщо використовується)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Logging
LOG_LEVEL=INFO
LOG_LEVEL_SECURITY=WARN

# MySQL Root (для Docker)
MYSQL_ROOT_PASSWORD=<strong_root_password>
MYSQL_DATABASE=zvit_db
MYSQL_USER=zvituser
MYSQL_PASSWORD=<same_as_DATABASE_PASSWORD>

# App
SPRING_PROFILES_ACTIVE=production
```

### 1.3 Перевірити .gitignore

```bash
# Переконатися що .env в .gitignore
grep -q "^.env$" .gitignore || echo ".env" >> .gitignore

# Переконатися що backup файли в .gitignore
grep -q "*.backup$" .gitignore || echo "*.backup" >> .gitignore
grep -q "*.sql$" .gitignore || echo "*.sql" >> .gitignore
```

---

## ЕТАП 2: ОНОВЛЕННЯ КОНФІГУРАЦІЇ (1 година)

### 2.1 Оновити application.yml

```bash
# Backup
cp src/main/resources/application.yml src/main/resources/application.yml.old

# Замінити на версію зі змінними оточення
```

**Новий application.yml:**
```yaml
spring:
  application:
    name: zvit-backend

  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false  # Вимкнути в продакшні!
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: false
    open-in-view: false

  # Видалити spring.security.user (default user)

server:
  port: 8080
  servlet:
    context-path: /
  ssl:
    enabled: ${SSL_ENABLED:false}  # Увімкнути для HTTPS

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}

logging:
  level:
    com.zvit: ${LOG_LEVEL:INFO}
    org.springframework.security: ${LOG_LEVEL_SECURITY:WARN}
    org.springframework.web: ${LOG_LEVEL:INFO}
    org.hibernate.SQL: ${LOG_LEVEL:WARN}
```

### 2.2 Оновити application.properties

```properties
app.encryption.key=${ENCRYPTION_KEY}
app.base-url=${BASE_URL:http://localhost:8080}
```

### 2.3 Оновити docker-compose.yml

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
    command:
      - --default-authentication-plugin=mysql_native_password
      - --require-secure-transport=OFF  # Увімкнути ON для production з SSL
      - --max-connections=200
    restart: unless-stopped

  redis:  # Додати Redis для dashboard tokens
    image: redis:7-alpine
    container_name: zvit-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    restart: unless-stopped

volumes:
  mysql-data:
  redis-data:
```

---

## ЕТАП 3: ОНОВЛЕННЯ КОДУ (4-6 годин)

### 3.1 Додати залежності в pom.xml

```xml
<!-- Rate Limiting -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>7.6.0</version>
</dependency>

<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- OWASP Java HTML Sanitizer (опційно для XSS захисту) -->
<dependency>
    <groupId>com.googlecode.owasp-java-html-sanitizer</groupId>
    <artifactId>owasp-java-html-sanitizer</artifactId>
    <version>20220608.1</version>
</dependency>
```

### 3.2 Створити нові сервіси

Скопіювати код з файлу `SECURITY_FIXES_EXAMPLES.md`:

1. ✅ **EncryptionService.java** - оновити з AES-GCM
2. ✅ **RateLimitingService.java** - створити новий
3. ✅ **PasswordValidator.java** - створити новий

### 3.3 Оновити існуючі сервіси

1. ✅ **AuthService.java** - додати валідацію пароля, account lockout
2. ✅ **JwtService.java** - видалити phone з claims
3. ✅ **AdminService.java** - використовувати Redis для tokens
4. ✅ **SecurityConfig.java** - додати security headers

### 3.4 Оновити Entity

1. ✅ **User.java** - додати поля:
   - `failedLoginAttempts`
   - `accountLockedUntil`
   - `privacyConsentAt`
   - `dataRetentionUntil`

### 3.5 Замінити System.out.println на Logger

```bash
# Знайти всі System.out.println
grep -r "System.out.println" src/main/java/

# Замінити на logger
```

---

## ЕТАП 4: МІГРАЦІЯ БАЗИ ДАНИХ (1 година)

### 4.1 Запустити міграцію

```bash
# Завантажити скрипт міграції
mysql -h localhost -P 3307 -u root -pzvitpass123 zvit_db < database_security_migration.sql
```

### 4.2 Перевірити міграцію

```sql
-- Підключитися до БД
mysql -h localhost -P 3307 -u root -pzvitpass123 zvit_db

-- Перевірити нові таблиці
SHOW TABLES;

-- Перевірити нові колонки
DESCRIBE users;

-- Перевірити security policies
SELECT * FROM security_policies;

-- Перевірити статистику
SELECT * FROM security_stats;
```

### 4.3 Реєнкрипція даних

**КРИТИЧНО ВАЖЛИВО:** Перед зміною encryption key треба реєнкриптувати всі існуючі телефони!

```java
// Створити тимчасовий endpoint для міграції (видалити після використання!)
@RestController
@RequestMapping("/api/v1/migration")
public class MigrationController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("oldEncryption")
    private EncryptionService oldEncryptionService;

    @Autowired
    @Qualifier("newEncryption")
    private EncryptionService newEncryptionService;

    @PostMapping("/reencrypt-phones")
    public ResponseEntity<String> reencryptPhones(@RequestParam String adminSecret) {
        if (!"YOUR_TEMPORARY_SECRET".equals(adminSecret)) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        List<User> users = userRepository.findAll();
        int count = 0;

        for (User user : users) {
            try {
                String phone = oldEncryptionService.decrypt(user.getPhoneEncrypted());
                String newEncrypted = newEncryptionService.encrypt(phone);
                user.setPhoneEncrypted(newEncrypted);
                userRepository.save(user);
                count++;
            } catch (Exception e) {
                // Log error
            }
        }

        return ResponseEntity.ok("Reencrypted " + count + " phones");
    }
}
```

**Виконати:**
```bash
curl -X POST "http://localhost:8080/api/v1/migration/reencrypt-phones?adminSecret=YOUR_TEMPORARY_SECRET"
```

**ВИДАЛИТИ endpoint після міграції!**

---

## ЕТАП 5: ТЕСТУВАННЯ (2 години)

### 5.1 Unit тести

```bash
mvn clean test
```

### 5.2 Інтеграційні тести

```bash
# Запустити додаток
mvn spring-boot:run

# Тести аутентифікації
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+380991234567",
    "password": "Test123!@#",
    "name": "Test User"
  }'

# Тест логіна
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+380991234567",
    "password": "Test123!@#"
  }'
```

### 5.3 Тести безпеки

```bash
# Тест rate limiting (має заблокувати після 5 спроб)
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"phone": "+380991234567", "password": "wrong"}'
  echo "\nAttempt $i"
done

# Тест account lockout
# Після 5 невдалих спроб акаунт має бути заблокований на 15 хвилин

# Тест слабких паролів (має відхилити)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+380991234568",
    "password": "12345678",
    "name": "Test"
  }'
```

### 5.4 Security scanning

```bash
# OWASP Dependency Check
mvn org.owasp:dependency-check-maven:check

# SonarQube (якщо налаштований)
mvn sonar:sonar

# Перевірка на витоки секретів
git secrets --scan
```

---

## ЕТАП 6: DEPLOYMENT (1 година)

### 6.1 Pre-deployment checklist

- [ ] Backup БД створено
- [ ] .env файл налаштований з production значеннями
- [ ] SSL сертифікати налаштовані
- [ ] Firewall rules налаштовані
- [ ] Monitoring налаштований
- [ ] Alerting налаштований
- [ ] Всі тести пройшли успішно

### 6.2 Deployment steps

```bash
# 1. Зупинити додаток
docker-compose down

# 2. Оновити .env для production
vim .env

# 3. Збілдити новий Docker image
docker-compose build

# 4. Запустити БД та Redis
docker-compose up -d mysql redis

# 5. Дочекатися старту БД (30 секунд)
sleep 30

# 6. Запустити міграцію БД
docker exec zvit-mysql mysql -u root -p$MYSQL_ROOT_PASSWORD zvit_db < database_security_migration.sql

# 7. Запустити додаток
docker-compose up -d

# 8. Перевірити логи
docker-compose logs -f
```

### 6.3 Post-deployment checklist

- [ ] Додаток запущений без помилок
- [ ] Health check endpoint працює
- [ ] SSL працює (якщо увімкнений)
- [ ] Rate limiting працює
- [ ] Логи пишуться коректно
- [ ] Audit log працює
- [ ] Dashboard tokens зберігаються в Redis

---

## ЕТАП 7: МОНІТОРИНГ (постійно)

### 7.1 Налаштувати моніторинг

```sql
-- Щоденна перевірка security stats
SELECT * FROM security_stats;

-- Перевірка невдалих входів
SELECT
    user_id,
    COUNT(*) as failed_attempts,
    MAX(created_at) as last_attempt
FROM audit_log
WHERE event_type = 'LOGIN_FAILED'
    AND created_at > NOW() - INTERVAL 1 DAY
GROUP BY user_id
HAVING failed_attempts > 3
ORDER BY failed_attempts DESC;

-- Перевірка заблокованих акаунтів
SELECT
    id,
    name,
    failed_login_attempts,
    account_locked_until
FROM users
WHERE account_locked_until > NOW();
```

### 7.2 Налаштувати alerting

Приклад Prometheus/Grafana alerts:

```yaml
groups:
- name: security_alerts
  interval: 5m
  rules:
  - alert: HighFailedLogins
    expr: rate(failed_logins_total[5m]) > 10
    annotations:
      summary: "High number of failed login attempts"

  - alert: TooManyLockedAccounts
    expr: locked_accounts_total > 5
    annotations:
      summary: "Multiple accounts are locked"
```

---

## ЕТАП 8: ДОКУМЕНТАЦІЯ ТА НАВЧАННЯ

### 8.1 Оновити документацію

- [ ] API документація оновлена
- [ ] Security policy документована
- [ ] Incident response plan створений
- [ ] Password policy задокументована

### 8.2 Навчити команду

- [ ] Провести security training для розробників
- [ ] Пояснити нові security features
- [ ] Навчити як реагувати на security incidents

---

## РОЗКЛАД ВИКОНАННЯ

### Термінові дії (сьогодні, 8 годин)
1. ✅ Backup системи (30 хв)
2. ✅ Створити .env з новими секретами (30 хв)
3. ✅ Оновити конфігурацію (1 год)
4. ✅ Оновити код encryption service (2 год)
5. ✅ Міграція БД (1 год)
6. ✅ Реєнкрипція даних (1 год)
7. ✅ Тестування (2 год)

### Високий пріоритет (цього тижня, 16 годин)
8. ✅ Додати rate limiting (4 год)
9. ✅ Додати password validation (2 год)
10. ✅ Додати account lockout (3 год)
11. ✅ Додати security headers (1 год)
12. ✅ Оновити логування (2 год)
13. ✅ Deployment на production (2 год)
14. ✅ Налаштування моніторингу (2 год)

### Середній пріоритет (цього місяця, 40 годин)
15. ✅ Додати 2FA (8 год)
16. ✅ Redis для dashboard tokens (4 год)
17. ✅ GDPR compliance (export/delete) (12 год)
18. ✅ Audit logging (8 год)
19. ✅ Security testing (OWASP ZAP) (8 год)

---

## ROLLBACK PLAN

Якщо щось пішло не так:

```bash
# 1. Зупинити додаток
docker-compose down

# 2. Відновити БД з backup
mysql -h localhost -P 3307 -u root -pzvitpass123 zvit_db < backup_YYYYMMDD_HHMMSS.sql

# 3. Відновити конфігурацію
cp src/main/resources/application.yml.backup src/main/resources/application.yml
cp src/main/resources/application.properties.backup src/main/resources/application.properties

# 4. Перезапустити на старій версії
git checkout <previous_commit>
docker-compose up -d
```

---

## КОНТАКТИ ДЛЯ ПІДТРИМКИ

**Security incidents:**
- Email: security@your-domain.com
- Slack: #security-incidents
- Phone: +380...

**Technical issues:**
- Email: dev@your-domain.com
- Slack: #tech-support

---

## ВИСНОВОК

Після виконання всіх етапів:
- ✅ Всі критичні вразливості виправлені
- ✅ Дані користувачів захищені
- ✅ GDPR compliance досягнуто
- ✅ Моніторинг налаштований
- ✅ Система готова до production

**Наступний security audit:** через 3 місяці

**Дата створення інструкції:** 2025-11-20
