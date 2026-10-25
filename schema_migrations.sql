-- ============================================================================
-- SecureBank: Role-Based Core Banking & Transaction Management System
-- Schema Migrations: Upgrades legacy schema to v2.0 without data destruction
-- ============================================================================

USE securebank_db;

-- 1. Create Roles Table if missing
CREATE TABLE IF NOT EXISTS roles (
    role_name        VARCHAR(20)     NOT NULL,
    description      VARCHAR(100)    NOT NULL,
    access_level     INT             NOT NULL DEFAULT 1,
    CONSTRAINT pk_roles PRIMARY KEY (role_name)
) ENGINE=InnoDB;

INSERT INTO roles (role_name, description, access_level) VALUES
('CUSTOMER', 'Retail Banking Customer with personal account access', 1),
('TELLER',   'Bank Branch Staff with counter transaction & onboarding access', 2),
('ADMIN',    'System Administrator with audit, policy & security control', 3)
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- 2. Create Users Table if missing
CREATE TABLE IF NOT EXISTS users (
    user_id          INT             NOT NULL AUTO_INCREMENT,
    username         VARCHAR(50)     NOT NULL UNIQUE,
    password_hash    VARCHAR(255)    NOT NULL,
    role             VARCHAR(20)     NOT NULL DEFAULT 'CUSTOMER',
    failed_logins    INT             NOT NULL DEFAULT 0,
    is_locked        BOOLEAN         NOT NULL DEFAULT FALSE,
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login       TIMESTAMP       NULL DEFAULT NULL,
    CONSTRAINT pk_users PRIMARY KEY (user_id),
    CONSTRAINT fk_users_role FOREIGN KEY (role) REFERENCES roles(role_name) ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=101;

-- 3. Create Customers Table if missing
CREATE TABLE IF NOT EXISTS customers (
    customer_id      INT             NOT NULL AUTO_INCREMENT,
    user_id          INT             NOT NULL UNIQUE,
    full_name        VARCHAR(100)    NOT NULL,
    email            VARCHAR(100)    NULL,
    phone            VARCHAR(20)     NULL,
    address          VARCHAR(255)    NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customers PRIMARY KEY (customer_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=501;

-- 4. Safely upgrade accounts table columns
-- Modify pin to VARCHAR(255) to support BCrypt hash
ALTER TABLE accounts MODIFY COLUMN pin VARCHAR(255) NOT NULL;

-- Add customer_id column if not exists
SET @dbname = DATABASE();
SET @tablename = 'accounts';
SET @columnname = 'customer_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE accounts ADD COLUMN customer_id INT NULL AFTER account_number'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Add status column if not exists
SET @columnname = 'status';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE accounts ADD COLUMN status ENUM(\'ACTIVE\', \'LOCKED\', \'FROZEN\', \'CLOSED\') NOT NULL DEFAULT \'ACTIVE\' AFTER is_locked'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 5. Safely upgrade transactions table columns
SET @tablename = 'transactions';
SET @columnname = 'transaction_ref';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE transactions ADD COLUMN transaction_ref VARCHAR(30) NULL AFTER transaction_id'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'related_account_number';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  'ALTER TABLE transactions ADD COLUMN related_account_number INT NULL AFTER remarks'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 6. Create Transfer Details Table
CREATE TABLE IF NOT EXISTS transfer_details (
    transfer_id      INT             NOT NULL AUTO_INCREMENT,
    transfer_ref     VARCHAR(30)     NOT NULL UNIQUE,
    sender_account   INT             NOT NULL,
    receiver_account INT             NOT NULL,
    amount           DECIMAL(15, 2)  NOT NULL,
    remarks          VARCHAR(255)    NULL,
    transfer_timestamp TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transfer_details PRIMARY KEY (transfer_id)
) ENGINE=InnoDB;

-- 7. Create Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    log_id           INT             NOT NULL AUTO_INCREMENT,
    actor_username   VARCHAR(50)     NOT NULL,
    role             VARCHAR(20)     NULL,
    action           VARCHAR(50)     NOT NULL,
    entity_type      VARCHAR(50)     NULL,
    entity_id        VARCHAR(50)     NULL,
    details          VARCHAR(500)    NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    ip_address       VARCHAR(45)     NULL DEFAULT 'LOCAL',
    log_timestamp    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_logs PRIMARY KEY (log_id)
) ENGINE=InnoDB;

-- 8. Create In-App Notifications Table
CREATE TABLE IF NOT EXISTS notifications (
    notification_id  INT             NOT NULL AUTO_INCREMENT,
    user_id          INT             NOT NULL,
    title            VARCHAR(100)    NOT NULL,
    message          VARCHAR(500)    NOT NULL,
    type             VARCHAR(20)     NOT NULL DEFAULT 'INFO',
    is_read          BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notifications PRIMARY KEY (notification_id)
) ENGINE=InnoDB;

-- 9. Create Suspicious Activity Alerts Table
CREATE TABLE IF NOT EXISTS suspicious_alerts (
    alert_id         INT             NOT NULL AUTO_INCREMENT,
    event_timestamp  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id          INT             NULL,
    account_number   INT             NULL,
    rule_triggered   VARCHAR(100)    NOT NULL,
    severity         VARCHAR(20)     NOT NULL,
    evidence         TEXT            NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    CONSTRAINT pk_suspicious_alerts PRIMARY KEY (alert_id)
) ENGINE=InnoDB;

-- 10. Create Transaction Reconciliation Logs Table
CREATE TABLE IF NOT EXISTS reconciliation_logs (
    recon_id         INT             NOT NULL AUTO_INCREMENT,
    run_timestamp    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_accounts   INT             NOT NULL,
    matched_accounts INT             NOT NULL,
    discrepancies    INT             NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    summary          TEXT            NOT NULL,
    CONSTRAINT pk_recon_logs PRIMARY KEY (recon_id)
) ENGINE=InnoDB;

-- 11. Create Scheduled Job Execution Logs Table
CREATE TABLE IF NOT EXISTS scheduled_job_logs (
    job_id           INT             NOT NULL AUTO_INCREMENT,
    job_name         VARCHAR(100)    NOT NULL,
    executed_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status           VARCHAR(20)     NOT NULL,
    details          VARCHAR(500)    NULL,
    CONSTRAINT pk_sched_job_logs PRIMARY KEY (job_id)
) ENGINE=InnoDB;

