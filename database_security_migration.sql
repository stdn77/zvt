-- ================================================
-- SECURITY MIGRATION SCRIPT
-- Додає поля безпеки до існуючої бази даних ZVIT
-- ================================================

USE zvit_db;

-- ================================================
-- 1. ДОДАТИ ПОЛЯ ДЛЯ ACCOUNT LOCKOUT
-- ================================================

ALTER TABLE users
ADD COLUMN IF NOT EXISTS failed_login_attempts INT DEFAULT 0 COMMENT 'Кількість невдалих спроб входу',
ADD COLUMN IF NOT EXISTS account_locked_until TIMESTAMP NULL COMMENT 'Час до якого акаунт заблоковано',
ADD INDEX idx_account_locked (account_locked_until);

-- ================================================
-- 2. ДОДАТИ ПОЛЯ ДЛЯ GDPR COMPLIANCE
-- ================================================

ALTER TABLE users
ADD COLUMN IF NOT EXISTS privacy_consent_at TIMESTAMP NULL COMMENT 'Час надання згоди на обробку даних',
ADD COLUMN IF NOT EXISTS privacy_policy_version VARCHAR(10) DEFAULT '1.0' COMMENT 'Версія Privacy Policy',
ADD COLUMN IF NOT EXISTS data_retention_until TIMESTAMP NULL COMMENT 'Дата видалення неактивних даних';

-- ================================================
-- 3. СТВОРИТИ ТАБЛИЦЮ AUDIT LOG
-- ================================================

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36) NULL,
    event_type VARCHAR(50) NOT NULL COMMENT 'LOGIN, LOGOUT, REGISTER, PASSWORD_CHANGE, etc.',
    event_details TEXT NULL,
    ip_address VARCHAR(45) NULL COMMENT 'IPv4 або IPv6',
    user_agent TEXT NULL,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at),
    INDEX idx_success (success),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Security audit log';

-- ================================================
-- 4. СТВОРИТИ ТАБЛИЦЮ ДЛЯ ЗБЕРІГАННЯ DASHBOARD TOKENS (якщо не використовується Redis)
-- ================================================

CREATE TABLE IF NOT EXISTS dashboard_tokens (
    token VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_group_id (group_id),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at),
    FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Dashboard access tokens';

-- ================================================
-- 5. СТВОРИТИ ТАБЛИЦЮ ДЛЯ PASSWORD RESET TOKENS
-- ================================================

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (token),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Password reset tokens';

-- ================================================
-- 6. СТВОРИТИ ТАБЛИЦЮ ДЛЯ 2FA SECRETS
-- ================================================

CREATE TABLE IF NOT EXISTS two_factor_auth (
    user_id VARCHAR(36) PRIMARY KEY,
    secret VARCHAR(255) NOT NULL COMMENT 'TOTP secret (encrypted)',
    enabled BOOLEAN DEFAULT FALSE,
    backup_codes TEXT NULL COMMENT 'Encrypted backup codes (JSON array)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Two-factor authentication data';

-- ================================================
-- 7. СТВОРИТИ ТАБЛИЦЮ ДЛЯ SESSION MANAGEMENT
-- ================================================

CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    session_token VARCHAR(255) NOT NULL UNIQUE COMMENT 'JWT token hash',
    device_info TEXT NULL COMMENT 'User agent info',
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    INDEX idx_user_id (user_id),
    INDEX idx_session_token (session_token),
    INDEX idx_expires_at (expires_at),
    INDEX idx_is_active (is_active),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Active user sessions for JWT invalidation';

-- ================================================
-- 8. ДОДАТИ ПОЛЯ ДЛЯ EMAIL VERIFICATION
-- ================================================

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    email_hash VARCHAR(64) NOT NULL COMMENT 'Hash of email to verify',
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_token (token),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Email verification tokens';

-- ================================================
-- 9. SECURITY POLICY TABLE
-- ================================================

CREATE TABLE IF NOT EXISTS security_policies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    policy_name VARCHAR(50) NOT NULL UNIQUE,
    policy_value TEXT NOT NULL,
    description TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Security policy configuration';

-- Вставити дефолтні політики безпеки
INSERT INTO security_policies (policy_name, policy_value, description) VALUES
('PASSWORD_MIN_LENGTH', '8', 'Мінімальна довжина пароля'),
('PASSWORD_REQUIRE_UPPERCASE', 'true', 'Вимагати велику літеру'),
('PASSWORD_REQUIRE_LOWERCASE', 'true', 'Вимагати малу літеру'),
('PASSWORD_REQUIRE_DIGIT', 'true', 'Вимагати цифру'),
('PASSWORD_REQUIRE_SPECIAL', 'true', 'Вимагати спеціальний символ'),
('MAX_FAILED_LOGIN_ATTEMPTS', '5', 'Максимум невдалих спроб входу'),
('ACCOUNT_LOCKOUT_DURATION_MINUTES', '15', 'Тривалість блокування акаунту'),
('JWT_EXPIRATION_HOURS', '24', 'Час життя JWT токена'),
('DASHBOARD_TOKEN_EXPIRATION_HOURS', '24', 'Час життя dashboard токена'),
('PASSWORD_RESET_TOKEN_EXPIRATION_HOURS', '1', 'Час життя токена скидання пароля'),
('ENABLE_2FA_FOR_ADMINS', 'false', 'Обов\'язкова 2FA для адміністраторів'),
('DATA_RETENTION_DAYS', '730', 'Зберігати дані неактивних акаунтів (2 роки)'),
('AUDIT_LOG_RETENTION_DAYS', '365', 'Зберігати audit logs (1 рік)')
ON DUPLICATE KEY UPDATE policy_value=VALUES(policy_value);

-- ================================================
-- 10. ДОДАТИ ІНДЕКСИ ДЛЯ PERFORMANCE
-- ================================================

-- Індекси для швидкого пошуку
ALTER TABLE users
ADD INDEX IF NOT EXISTS idx_phone_hash (phone_hash),
ADD INDEX IF NOT EXISTS idx_email_hash (email_hash),
ADD INDEX IF NOT EXISTS idx_active (is_active),
ADD INDEX IF NOT EXISTS idx_last_login (last_login_at);

ALTER TABLE `groups`
ADD INDEX IF NOT EXISTS idx_access_code (access_code),
ADD INDEX IF NOT EXISTS idx_internal_code (internal_code);

-- ================================================
-- 11. STORED PROCEDURE для очищення старих токенів
-- ================================================

DELIMITER //

CREATE PROCEDURE IF NOT EXISTS cleanup_expired_tokens()
BEGIN
    -- Видалити протерміновані dashboard tokens
    DELETE FROM dashboard_tokens WHERE expires_at < NOW();

    -- Видалити використані або протерміновані password reset tokens
    DELETE FROM password_reset_tokens WHERE expires_at < NOW() OR used = TRUE;

    -- Видалити протерміновані email verification tokens
    DELETE FROM email_verification_tokens WHERE expires_at < NOW() OR used = TRUE;

    -- Видалити протерміновані sessions
    DELETE FROM user_sessions WHERE expires_at < NOW() AND is_active = FALSE;

    -- Видалити старі audit logs (старіше ніж retention policy)
    DELETE FROM audit_log
    WHERE created_at < DATE_SUB(NOW(), INTERVAL
        (SELECT CAST(policy_value AS UNSIGNED) FROM security_policies WHERE policy_name = 'AUDIT_LOG_RETENTION_DAYS')
        DAY
    );

    SELECT 'Cleanup completed' AS status;
END //

DELIMITER ;

-- ================================================
-- 12. STORED PROCEDURE для анонімізації користувача
-- ================================================

DELIMITER //

CREATE PROCEDURE IF NOT EXISTS anonymize_user(IN target_user_id VARCHAR(36))
BEGIN
    -- Оновити користувача з анонімізованими даними
    UPDATE users
    SET
        phone_hash = SHA2(CONCAT('DELETED_', id), 256),
        phone_encrypted = 'DELETED',
        email_hash = NULL,
        password_hash = 'DELETED',
        name = 'Deleted User',
        is_active = FALSE,
        phone_verified = FALSE,
        email_verified = FALSE
    WHERE id = target_user_id;

    -- Видалити all related sensitive data
    DELETE FROM user_sessions WHERE user_id = target_user_id;
    DELETE FROM password_reset_tokens WHERE user_id = target_user_id;
    DELETE FROM email_verification_tokens WHERE user_id = target_user_id;
    DELETE FROM two_factor_auth WHERE user_id = target_user_id;

    -- Зберегти audit log
    INSERT INTO audit_log (user_id, event_type, event_details, success)
    VALUES (target_user_id, 'ACCOUNT_DELETED', 'User account anonymized per GDPR request', TRUE);

    SELECT 'User anonymized' AS status;
END //

DELIMITER ;

-- ================================================
-- 13. EVENT для автоматичного очищення (MySQL 8.0+)
-- ================================================

-- Увімкнути event scheduler
SET GLOBAL event_scheduler = ON;

-- Створити event для щоденного очищення
CREATE EVENT IF NOT EXISTS daily_cleanup
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP + INTERVAL 1 HOUR
DO
    CALL cleanup_expired_tokens();

-- ================================================
-- 14. VIEW для безпечного перегляду користувачів
-- ================================================

CREATE OR REPLACE VIEW users_safe_view AS
SELECT
    id,
    name,
    CONCAT(LEFT(phone_hash, 8), '...') AS phone_hint,
    phone_verified,
    email_verified,
    is_active,
    created_at,
    last_login_at,
    failed_login_attempts,
    CASE
        WHEN account_locked_until IS NOT NULL AND account_locked_until > NOW()
        THEN TRUE
        ELSE FALSE
    END AS is_locked
FROM users;

-- ================================================
-- 15. ДОДАТИ ТРИГЕРИ ДЛЯ AUDIT LOGGING
-- ================================================

DELIMITER //

-- Тригер для логування створення користувача
CREATE TRIGGER IF NOT EXISTS user_created_audit
AFTER INSERT ON users
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (user_id, event_type, event_details, success)
    VALUES (NEW.id, 'USER_CREATED', JSON_OBJECT('name', NEW.name), TRUE);
END //

-- Тригер для логування зміни пароля
CREATE TRIGGER IF NOT EXISTS password_changed_audit
AFTER UPDATE ON users
FOR EACH ROW
BEGIN
    IF OLD.password_hash != NEW.password_hash THEN
        INSERT INTO audit_log (user_id, event_type, event_details, success)
        VALUES (NEW.id, 'PASSWORD_CHANGED', 'Password was updated', TRUE);
    END IF;
END //

-- Тригер для логування деактивації акаунту
CREATE TRIGGER IF NOT EXISTS account_deactivated_audit
AFTER UPDATE ON users
FOR EACH ROW
BEGIN
    IF OLD.is_active = TRUE AND NEW.is_active = FALSE THEN
        INSERT INTO audit_log (user_id, event_type, event_details, success)
        VALUES (NEW.id, 'ACCOUNT_DEACTIVATED', 'Account was deactivated', TRUE);
    END IF;
END //

DELIMITER ;

-- ================================================
-- 16. STATISTICS VIEWS для моніторингу безпеки
-- ================================================

CREATE OR REPLACE VIEW security_stats AS
SELECT
    (SELECT COUNT(*) FROM users WHERE is_active = TRUE) AS active_users,
    (SELECT COUNT(*) FROM users WHERE account_locked_until > NOW()) AS locked_users,
    (SELECT COUNT(*) FROM audit_log WHERE event_type = 'LOGIN_FAILED' AND created_at > NOW() - INTERVAL 1 HOUR) AS failed_logins_last_hour,
    (SELECT COUNT(*) FROM user_sessions WHERE is_active = TRUE) AS active_sessions,
    (SELECT COUNT(*) FROM dashboard_tokens WHERE expires_at > NOW()) AS active_dashboard_tokens;

-- ================================================
-- 17. GRANT PERMISSIONS (якщо потрібно)
-- ================================================

-- Надати права на нові таблиці
GRANT SELECT, INSERT, UPDATE, DELETE ON zvit_db.audit_log TO 'zvituser'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON zvit_db.dashboard_tokens TO 'zvituser'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON zvit_db.password_reset_tokens TO 'zvituser'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON zvit_db.two_factor_auth TO 'zvituser'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON zvit_db.user_sessions TO 'zvituser'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON zvit_db.email_verification_tokens TO 'zvituser'@'%';
GRANT SELECT ON zvit_db.security_policies TO 'zvituser'@'%';
GRANT EXECUTE ON PROCEDURE zvit_db.cleanup_expired_tokens TO 'zvituser'@'%';
GRANT EXECUTE ON PROCEDURE zvit_db.anonymize_user TO 'zvituser'@'%';

-- ================================================
-- 18. VERIFICATION QUERIES
-- ================================================

-- Перевірити створення таблиць
SELECT
    TABLE_NAME,
    TABLE_ROWS,
    CREATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'zvit_db'
    AND TABLE_NAME IN (
        'audit_log',
        'dashboard_tokens',
        'password_reset_tokens',
        'two_factor_auth',
        'user_sessions',
        'email_verification_tokens',
        'security_policies'
    );

-- Перевірити нові колонки в users
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'zvit_db'
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME IN (
        'failed_login_attempts',
        'account_locked_until',
        'privacy_consent_at',
        'privacy_policy_version',
        'data_retention_until'
    );

-- Перевірити індекси
SELECT
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'zvit_db'
    AND TABLE_NAME = 'users'
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- ================================================
-- MIGRATION COMPLETED
-- ================================================

SELECT '✅ Security migration completed successfully!' AS status;
