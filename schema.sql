-- ============================================================================
-- SecureBank: Role-Based Core Banking & Transaction Management System
-- Database Schema: securebank_db (MySQL 8.0+ / InnoDB / ACID Engine)
-- ============================================================================

CREATE DATABASE IF NOT EXISTS securebank_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE securebank_db;

-- ----------------------------------------------------------------------------
-- 1. ROLES TABLE (RBAC Tiers)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS roles (
    role_name        VARCHAR(20)     NOT NULL,
    description      VARCHAR(100)    NOT NULL,
    access_level     INT             NOT NULL DEFAULT 1,
    CONSTRAINT pk_roles PRIMARY KEY (role_name)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 2. USERS TABLE (Authentication & Security State)
-- ----------------------------------------------------------------------------
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

CREATE INDEX idx_users_username ON users (username);

-- ----------------------------------------------------------------------------
-- 3. CUSTOMERS TABLE (Customer Profiles)
-- ----------------------------------------------------------------------------
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

CREATE INDEX idx_customers_name ON customers (full_name);

-- ----------------------------------------------------------------------------
-- 4. ACCOUNTS TABLE (Core Accounts Ledger)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS accounts (
    account_number   INT             NOT NULL AUTO_INCREMENT,
    customer_id      INT             NULL,
    holder_name      VARCHAR(100)    NOT NULL,
    account_type     ENUM('SAVINGS', 'CURRENT') NOT NULL,
    balance          DECIMAL(15, 2)  NOT NULL DEFAULT 0.00,
    pin              VARCHAR(255)    NOT NULL,
    failed_attempts  INT             NOT NULL DEFAULT 0,
    is_locked        BOOLEAN         NOT NULL DEFAULT FALSE,
    status           ENUM('ACTIVE', 'LOCKED', 'FROZEN', 'CLOSED') NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_accounts PRIMARY KEY (account_number),
    CONSTRAINT chk_failed_attempts CHECK (failed_attempts BETWEEN 0 AND 3)
) ENGINE=InnoDB AUTO_INCREMENT=1001;

CREATE INDEX idx_accounts_status ON accounts (status);
CREATE INDEX idx_accounts_customer ON accounts (customer_id);

-- ----------------------------------------------------------------------------
-- 5. TRANSACTIONS TABLE (Append-Only Financial Ledger)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id   INT             NOT NULL AUTO_INCREMENT,
    transaction_ref  VARCHAR(30)     NULL,
    account_number   INT             NOT NULL,
    txn_type         ENUM('OPEN', 'DEPOSIT', 'WITHDRAW', 'TRANSFER_IN', 'TRANSFER_OUT', 'INTEREST') NOT NULL,
    amount           DECIMAL(15, 2)  NOT NULL,
    balance_after    DECIMAL(15, 2)  NOT NULL,
    remarks          VARCHAR(255)    NOT NULL,
    related_account_number INT       NULL,
    txn_timestamp    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transactions PRIMARY KEY (transaction_id),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_number) REFERENCES accounts(account_number) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_txn_account_time ON transactions (account_number, txn_timestamp DESC, transaction_id DESC);
CREATE INDEX idx_txn_ref ON transactions (transaction_ref);

-- ----------------------------------------------------------------------------
-- 6. TRANSFER_DETAILS TABLE (Double-Entry Transfer Linkage)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS transfer_details (
    transfer_id      INT             NOT NULL AUTO_INCREMENT,
    transfer_ref     VARCHAR(30)     NOT NULL UNIQUE,
    sender_account   INT             NOT NULL,
    receiver_account INT             NOT NULL,
    amount           DECIMAL(15, 2)  NOT NULL,
    remarks          VARCHAR(255)    NULL,
    transfer_timestamp TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transfer_details PRIMARY KEY (transfer_id),
    CONSTRAINT fk_transfer_sender FOREIGN KEY (sender_account) REFERENCES accounts(account_number) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_receiver FOREIGN KEY (receiver_account) REFERENCES accounts(account_number) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- 7. AUDIT_LOGS TABLE (Security & Administrative Audit Trail)
-- ----------------------------------------------------------------------------
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

CREATE INDEX idx_audit_time ON audit_logs (log_timestamp DESC);
CREATE INDEX idx_audit_actor ON audit_logs (actor_username);

-- -----------------------------------------------------------------------------
-- 7. NOTIFICATIONS (In-app user event notifications)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    notification_id  INT             NOT NULL AUTO_INCREMENT,
    user_id          INT             NOT NULL,
    title            VARCHAR(100)    NOT NULL,
    message          VARCHAR(500)    NOT NULL,
    type             VARCHAR(20)     NOT NULL DEFAULT 'INFO',
    is_read          BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_notifications PRIMARY KEY (notification_id),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_notif_user ON notifications (user_id, is_read);

-- -----------------------------------------------------------------------------
-- 8. SUSPICIOUS_ALERTS (Automated Rule-Based Fraud Monitoring)
-- -----------------------------------------------------------------------------
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

CREATE INDEX idx_alert_time ON suspicious_alerts (event_timestamp DESC);

-- -----------------------------------------------------------------------------
-- 9. RECONCILIATION_LOGS (Automated Transaction Reconciliation Records)
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- 10. SCHEDULED_JOB_LOGS (Recurring Scheduler Execution History)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS scheduled_job_logs (
    job_id           INT             NOT NULL AUTO_INCREMENT,
    job_name         VARCHAR(100)    NOT NULL,
    executed_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status           VARCHAR(20)     NOT NULL,
    details          VARCHAR(500)    NULL,
    CONSTRAINT pk_sched_job_logs PRIMARY KEY (job_id)
) ENGINE=InnoDB;

