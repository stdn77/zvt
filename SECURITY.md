# 🔒 ZVIT - Налаштування Безпеки

## Критично важливо для Production!

Цей документ описує кроки для захисту вашого додатку ZVIT перед розгортанням у production середовищі.

## 1️⃣ Налаштування Змінних Оточення

### Створення .env файлу

1. Скопіюйте `.env.example` в `.env`:
   ```bash
   cp .env.example .env
   ```

2. Відредагуйте `.env` і встановіть **унікальні безпечні значення**:

### Генерація Безпечних Секретів

#### JWT Secret (мінімум 256 біт)
```bash
openssl rand -base64 64
```

#### Encryption Key (точно 32 символи для AES-256)
```bash
openssl rand -base64 32 | cut -c1-32
```

#### Безпечний Пароль БД
```bash
openssl rand -base64 24
```

### Приклад .env файлу

```bash
# Database Configuration
DB_URL=jdbc:mysql://localhost:3307/zvit_db?useSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=true
DB_USERNAME=zvituser
DB_PASSWORD=Xy9mK2pQ7vN8wR3tF6hJ9sL4d

# Spring Security
SPRING_SECURITY_USER=admin
SPRING_SECURITY_PASSWORD=Zm5xK8pW2vB9yT6hG3jM7sQ4r

# JWT Configuration
JWT_SECRET=kL9mN2pQ5vR8xS1tY4hZ7jC0d3fG6iH9kM2nP5rT8wV1yB4eH7jK0mQ3sU6xA9zC2f5h
JWT_EXPIRATION=86400000

# Encryption Key (точно 32 символи)
ENCRYPTION_KEY=aB3dF6hJ9mN2qS5vX8zA2dF5hK8n
```

## 2️⃣ Налаштування HTTPS/SSL

### Для Production з реальним доменом

1. **Отримайте SSL сертифікат** (наприклад, через Let's Encrypt):
   ```bash
   sudo certbot certonly --standalone -d your-domain.com
   ```

2. **Створіть PKCS12 keystore** з вашого сертифікату:
   ```bash
   openssl pkcs12 -export \
     -in /etc/letsencrypt/live/your-domain.com/fullchain.pem \
     -inkey /etc/letsencrypt/live/your-domain.com/privkey.pem \
     -out keystore.p12 \
     -name tomcat \
     -passout pass:YOUR_KEYSTORE_PASSWORD
   ```

3. **Розмістіть keystore.p12** в `src/main/resources/`

4. **Додайте до .env**:
   ```bash
   SERVER_PORT=8443
   SERVER_SSL_ENABLED=true
   SERVER_SSL_KEY_STORE=classpath:keystore.p12
   SERVER_SSL_KEY_STORE_PASSWORD=YOUR_KEYSTORE_PASSWORD
   SERVER_SSL_KEY_STORE_TYPE=PKCS12
   SERVER_SSL_KEY_ALIAS=tomcat
   ```

5. **Оновіть application.yml** (якщо потрібно):
   ```yaml
   server:
     port: ${SERVER_PORT:8443}
     ssl:
       enabled: ${SERVER_SSL_ENABLED:false}
       key-store: ${SERVER_SSL_KEY_STORE}
       key-store-password: ${SERVER_SSL_KEY_STORE_PASSWORD}
       key-store-type: ${SERVER_SSL_KEY_STORE_TYPE:PKCS12}
       key-alias: ${SERVER_SSL_KEY_ALIAS:tomcat}
   ```

### Для Development (самопідписаний сертифікат)

1. **Створіть самопідписаний сертифікат**:
   ```bash
   keytool -genkeypair \
     -alias tomcat \
     -keyalg RSA \
     -keysize 2048 \
     -storetype PKCS12 \
     -keystore keystore.p12 \
     -validity 3650 \
     -storepass changeit
   ```

2. **Налаштуйте як описано вище**

## 3️⃣ Захист БД MySQL

### Увімкніть SSL для MySQL

1. **Згенеруйте SSL сертифікати для MySQL**:
   ```bash
   mysql_ssl_rsa_setup --datadir=/var/lib/mysql
   ```

2. **Оновіть my.cnf**:
   ```ini
   [mysqld]
   require_secure_transport=ON
   ssl-ca=/var/lib/mysql/ca.pem
   ssl-cert=/var/lib/mysql/server-cert.pem
   ssl-key=/var/lib/mysql/server-key.pem
   ```

3. **Перезапустіть MySQL**:
   ```bash
   sudo systemctl restart mysql
   ```

4. **Оновіть .env**:
   ```bash
   DB_URL=jdbc:mysql://localhost:3307/zvit_db?useSSL=true&requireSSL=true&serverTimezone=UTC
   ```

## 4️⃣ Мобільний Додаток

### Оновіть BASE_URL

Відредагуйте `/app/src/main/java/com/example/zvth/api/ApiClient.java`:

```java
// Для production
private static final String BASE_URL = "https://your-domain.com";

// Для development з реальним пристроєм
private static final String BASE_URL = "https://YOUR_LOCAL_IP:8443";
```

### Certificate Pinning (опціонально, але рекомендовано)

Додайте certificate pinning для додаткового захисту від MITM атак.

## 5️⃣ Контрольний Чеклист Безпеки

Перед запуском у production:

- [ ] ✅ Створено унікальний `.env` файл з безпечними секретами
- [ ] ✅ `.env` додано до `.gitignore` (перевірте!)
- [ ] ✅ Налаштовано HTTPS/SSL з реальним сертифікатом
- [ ] ✅ Увімкнено SSL для MySQL підключення
- [ ] ✅ Змінено всі паролі за замовчуванням
- [ ] ✅ JWT secret мінімум 256 біт
- [ ] ✅ Encryption key точно 32 символи
- [ ] ✅ BASE_URL в мобільному додатку вказує на HTTPS
- [ ] ✅ Вимкнено `show-sql` та debug логування в production
- [ ] ✅ Перевірено що hardcoded секрети видалені з коду

## 6️⃣ Запуск з Змінними Оточення

### Linux/Mac

```bash
# Завантажити змінні з .env
export $(cat .env | xargs)

# Запустити додаток
./mvnw spring-boot:run
```

або з `.env` файлом:

```bash
# Встановіть spring-boot-dotenv
# Додасть автоматичне завантаження .env
```

### Windows

```powershell
# PowerShell
Get-Content .env | ForEach-Object {
    $name, $value = $_.split('=')
    Set-Content env:\$name $value
}

# Запустити додаток
.\mvnw.cmd spring-boot:run
```

### Docker

```dockerfile
# docker-compose.yml
version: '3.8'
services:
  zvit-backend:
    build: .
    env_file:
      - .env
    ports:
      - "8443:8443"
```

## 7️⃣ Перевірка Безпеки

### Перевірте що HTTPS працює

```bash
curl -v https://your-domain.com/api/v1/health
```

### Перевірте що HTTP не працює

```bash
curl -v http://your-domain.com/api/v1/health
# Має перенаправити на HTTPS або відхилити
```

## 📞 Підтримка

Якщо виникають питання з налаштуванням безпеки, зверніться до команди розробників.

---

**ВАЖЛИВО:** Ніколи не комітьте `.env` файл з реальними секретами в git!
