-- ================================================================================
-- SecureBank: Database Schema Bootstrap
-- Role-Based Core Banking & Transaction Management System
-- Compatible with MySQL 8.0 and Embedded H2 (MySQL Mode)
-- ================================================================================

CREATE TABLE IF NOT EXISTS roles (
    role_name        VARCHAR(20)     NOT NULL PRIMARY KEY,
    description      VARCHAR(100)    NOT NULL,
    access_level     INT             NOT NULL DEFAULT 1
);

INSERT INTO roles (role_name, description, access_level) 
    SELECT 'CUSTOMER', 'Retail Banking Customer with personal account access', 1 
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'CUSTOMER');
INSERT INTO roles (role_name, description, access_level) 
    SELECT 'TELLER',   'Bank Branch Staff with counter transaction & onboarding access', 2 
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'TELLER');
INSERT INTO roles (role_name, description, access_level) 
    SELECT 'ADMIN',    'System Administrator with audit, policy & security control', 3 
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE role_name = 'ADMIN');

CREATE TABLE IF NOT EXISTS users (
    user_id          INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username         VARCHAR(50)     NOT NULL UNIQUE,
    password_hash    VARCHAR(255)    NOT NULL,
    role             VARCHAR(20)     NOT NULL DEFAULT 'CUSTOMER',
    failed_logins    INT             NOT NULL DEFAULT 0,
    is_locked        BOOLEAN         NOT NULL DEFAULT FALSE,
    active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login       TIMESTAMP       NULL DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS customers (
    customer_id      INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          INT             NOT NULL UNIQUE,
    full_name        VARCHAR(100)    NOT NULL,
    email            VARCHAR(100)    NULL,
    phone            VARCHAR(20)     NULL,
    address          VARCHAR(255)    NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accounts (
    account_number   INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT             NULL,
    holder_name      VARCHAR(100)    NOT NULL,
    account_type     VARCHAR(20)     NOT NULL,
    balance          DECIMAL(15, 2)  NOT NULL DEFAULT 0.00,
    pin              VARCHAR(255)    NOT NULL,
    failed_attempts  INT             NOT NULL DEFAULT 0,
    is_locked        BOOLEAN         NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id   INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_ref  VARCHAR(30)     NULL,
    account_number   INT             NOT NULL,
    txn_type         VARCHAR(20)     NOT NULL,
    amount           DECIMAL(15, 2)  NOT NULL,
    balance_after    DECIMAL(15, 2)  NOT NULL,
    remarks          VARCHAR(255)    NOT NULL,
    related_account_number INT       NULL,
    txn_timestamp    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS transfer_details (
    transfer_id      INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transfer_ref     VARCHAR(30)     NOT NULL UNIQUE,
    sender_account   INT             NOT NULL,
    receiver_account INT             NOT NULL,
    amount           DECIMAL(15, 2)  NOT NULL,
    remarks          VARCHAR(255)    NULL,
    transfer_timestamp TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    idempotency_key      VARCHAR(100)    NOT NULL PRIMARY KEY,
    operation_type       VARCHAR(50)     NOT NULL,
    request_payload_hash VARCHAR(64)     NOT NULL,
    response_json        TEXT            NOT NULL,
    status               VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    created_at           TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ledger_transactions (
    ledger_txn_id    INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    transaction_ref  VARCHAR(30)     NOT NULL UNIQUE,
    txn_type         VARCHAR(20)     NOT NULL,
    description      VARCHAR(255)    NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'POSTED',
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    entry_id         INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ledger_txn_id    INT             NOT NULL,
    account_number   INT             NOT NULL,
    entry_type       VARCHAR(10)     NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount           DECIMAL(15, 2)  NOT NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS beneficiaries (
    beneficiary_id      INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id         INT             NOT NULL,
    beneficiary_account INT             NOT NULL,
    beneficiary_name    VARCHAR(100)    NOT NULL,
    bank_name           VARCHAR(100)    NOT NULL DEFAULT 'SecureBank',
    ifsc_code           VARCHAR(20)     NOT NULL DEFAULT 'SCBK0001001',
    transfer_limit      DECIMAL(15, 2)  NOT NULL DEFAULT 100000.00,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS budgets (
    budget_id        INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id      INT             NOT NULL,
    category         VARCHAR(50)     NOT NULL,
    monthly_limit    DECIMAL(15, 2)  NOT NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS billers (
    biller_id        INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biller_name      VARCHAR(100)    NOT NULL,
    category         VARCHAR(50)     NOT NULL,
    biller_code      VARCHAR(20)     NOT NULL UNIQUE
);

INSERT INTO billers (biller_id, biller_name, category, biller_code)
    SELECT 1, 'BESCOM Electricity Bangalore', 'ELECTRICITY', 'BLR_ELEC_01'
    WHERE NOT EXISTS (SELECT 1 FROM billers WHERE biller_code = 'BLR_ELEC_01');
INSERT INTO billers (biller_id, biller_name, category, biller_code)
    SELECT 2, 'Tata Power Distribution', 'ELECTRICITY', 'TATA_ELEC_02'
    WHERE NOT EXISTS (SELECT 1 FROM billers WHERE biller_code = 'TATA_ELEC_02');
INSERT INTO billers (biller_id, biller_name, category, biller_code)
    SELECT 3, 'BWSSB Water Supply', 'WATER', 'BWSSB_WTR_01'
    WHERE NOT EXISTS (SELECT 1 FROM billers WHERE biller_code = 'BWSSB_WTR_01');
INSERT INTO billers (biller_id, biller_name, category, biller_code)
    SELECT 4, 'Airtel Broadband & Fiber', 'TELECOM', 'AIRTEL_FIBER_01'
    WHERE NOT EXISTS (SELECT 1 FROM billers WHERE biller_code = 'AIRTEL_FIBER_01');

CREATE TABLE IF NOT EXISTS bill_payments (
    payment_id       INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_ref      VARCHAR(30)     NOT NULL UNIQUE,
    customer_id      INT             NOT NULL,
    account_number   INT             NOT NULL,
    biller_id        INT             NOT NULL,
    consumer_number  VARCHAR(50)     NOT NULL,
    amount           DECIMAL(15, 2)  NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    paid_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS fixed_deposits (
    fd_id            INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fd_ref           VARCHAR(30)     NOT NULL UNIQUE,
    customer_id      INT             NOT NULL,
    account_number   INT             NOT NULL,
    principal_amount DECIMAL(15, 2)  NOT NULL,
    tenure_months    INT             NOT NULL,
    interest_rate    DECIMAL(5, 2)   NOT NULL,
    maturity_amount  DECIMAL(15, 2)  NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    start_date       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    maturity_date    TIMESTAMP       NOT NULL
);

CREATE TABLE IF NOT EXISTS loans (
    loan_id             INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    loan_ref            VARCHAR(30)     NOT NULL UNIQUE,
    customer_id         INT             NOT NULL,
    loan_type           VARCHAR(50)     NOT NULL,
    principal_amount    DECIMAL(15, 2)  NOT NULL,
    interest_rate       DECIMAL(5, 2)   NOT NULL,
    tenure_months       INT             NOT NULL,
    emi_amount          DECIMAL(15, 2)  NOT NULL,
    outstanding_balance DECIMAL(15, 2)  NOT NULL,
    disbursed_account   INT             NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING_APPROVAL',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS loan_repayments (
    repayment_id     INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    loan_id          INT             NOT NULL,
    payment_ref      VARCHAR(30)     NOT NULL UNIQUE,
    amount           DECIMAL(15, 2)  NOT NULL,
    repayment_date   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cards (
    card_id             INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    account_number      INT             NOT NULL UNIQUE,
    customer_id         INT             NOT NULL,
    card_number_masked  VARCHAR(25)     NOT NULL,
    card_type           VARCHAR(20)     NOT NULL DEFAULT 'VISA_PLATINUM',
    daily_limit         DECIMAL(15, 2)  NOT NULL DEFAULT 50000.00,
    is_blocked          BOOLEAN         NOT NULL DEFAULT FALSE,
    contactless_enabled BOOLEAN         NOT NULL DEFAULT TRUE,
    expiry_date         VARCHAR(10)     NOT NULL DEFAULT '12/29'
);

CREATE TABLE IF NOT EXISTS audit_logs (
    log_id           INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_username   VARCHAR(50)     NOT NULL,
    role             VARCHAR(20)     NULL,
    action           VARCHAR(50)     NOT NULL,
    entity_type      VARCHAR(50)     NULL,
    entity_id        VARCHAR(50)     NULL,
    details          VARCHAR(500)    NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    ip_address       VARCHAR(45)     NULL DEFAULT 'LOCAL',
    log_timestamp    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id  INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          INT             NOT NULL,
    title            VARCHAR(100)    NOT NULL,
    message          VARCHAR(500)    NOT NULL,
    type             VARCHAR(20)     NOT NULL DEFAULT 'INFO',
    is_read          BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS suspicious_alerts (
    alert_id         INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_timestamp  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id          INT             NULL,
    account_number   INT             NULL,
    rule_triggered   VARCHAR(100)    NOT NULL,
    severity         VARCHAR(20)     NOT NULL,
    evidence         TEXT            NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    risk_score       INT             NOT NULL DEFAULT 0,
    review_decision  VARCHAR(50)     NULL,
    reviewer_username VARCHAR(50)    NULL,
    reviewed_at      TIMESTAMP       NULL
);

CREATE TABLE IF NOT EXISTS reconciliation_logs (
    recon_id         INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    run_timestamp    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_accounts   INT             NOT NULL,
    matched_accounts INT             NOT NULL,
    discrepancies    INT             NOT NULL,
    status           VARCHAR(20)     NOT NULL,
    summary          TEXT            NOT NULL
);

CREATE TABLE IF NOT EXISTS scheduled_job_logs (
    job_id           INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_name         VARCHAR(100)    NOT NULL,
    executed_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status           VARCHAR(20)     NOT NULL,
    details          VARCHAR(500)    NULL
);

CREATE TABLE IF NOT EXISTS external_payments (
    payment_id          INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_reference    VARCHAR(50)     NOT NULL UNIQUE,
    idempotency_key     VARCHAR(100)    NOT NULL UNIQUE,
    account_number      INT             NOT NULL,
    amount              DECIMAL(15, 2)  NOT NULL,
    destination_account VARCHAR(50)     NOT NULL,
    destination_bank    VARCHAR(50)     NOT NULL,
    provider_name       VARCHAR(50)     NOT NULL,
    provider_reference  VARCHAR(100)    NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    is_simulated        BOOLEAN         NOT NULL DEFAULT TRUE,
    purpose             VARCHAR(255)    NULL,
    failure_reason      VARCHAR(255)    NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payment_callbacks (
    callback_id         INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    provider_reference  VARCHAR(100)    NOT NULL,
    client_reference    VARCHAR(50)     NOT NULL,
    signature           VARCHAR(255)    NOT NULL,
    raw_payload         TEXT            NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    is_valid_signature  BOOLEAN         NOT NULL DEFAULT FALSE,
    processed           BOOLEAN         NOT NULL DEFAULT FALSE,
    received_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_sessions (
    session_id          VARCHAR(64)     NOT NULL PRIMARY KEY,
    user_id             INT             NOT NULL,
    username            VARCHAR(50)     NOT NULL,
    role                VARCHAR(20)     NOT NULL,
    login_time          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logout_time         TIMESTAMP       NULL DEFAULT NULL,
    last_activity_time  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    session_status      VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    session_end_reason  VARCHAR(100)    NULL DEFAULT NULL,
    ip_address          VARCHAR(45)     NULL DEFAULT '127.0.0.1',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

