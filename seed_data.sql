-- ============================================================================
-- SecureBank: Role-Based Core Banking & Transaction Management System
-- Seed Data: Development & Demonstration Accounts
-- All passwords: BCrypt work factor 12
-- All PINs: Salted BCrypt work factor 10
-- ============================================================================

USE securebank_db;

-- ----------------------------------------------------------------------------
-- 1. SEED ROLES
-- ----------------------------------------------------------------------------
INSERT INTO roles (role_name, description, access_level) VALUES
('CUSTOMER', 'Retail Banking Customer with personal account access', 1),
('TELLER',   'Bank Branch Staff with counter transaction & onboarding access', 2),
('ADMIN',    'System Administrator with audit, policy & security control', 3)
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- ----------------------------------------------------------------------------
-- 2. SEED USERS
-- Default Passwords:
--   admin_rajesh   : Admin@123
--   teller_suresh  : Teller@123
--   aarav, priya, rohan, apex_corp, meera : Password@123
-- ----------------------------------------------------------------------------
INSERT INTO users (user_id, username, password_hash, role, failed_logins, is_locked, active, created_at) VALUES
(101, 'admin_rajesh',  '$2a$12$bbyAA9ZHc/EbHzRITjtcdOGMprkxUwxcr.bXx10n3i0VYRpWTLPdC', 'ADMIN',    0, FALSE, TRUE, '2026-10-01 08:00:00'),
(102, 'teller_suresh', '$2a$12$sx9kyrbTx33gOky0O1EGCubD.jK4HIRek29zghQXNL4pIf4R8zoKO', 'TELLER',   0, FALSE, TRUE, '2026-10-02 08:30:00'),
(103, 'aarav',         '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 0, FALSE, TRUE, '2026-10-10 09:00:00'),
(104, 'priya',         '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 0, FALSE, TRUE, '2026-10-11 10:00:00'),
(105, 'rohan',         '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 3, TRUE,  TRUE, '2026-10-12 11:00:00'),
(106, 'apex_corp',     '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 0, FALSE, TRUE, '2026-10-13 13:30:00'),
(107, 'meera',         '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 0, FALSE, TRUE, '2026-10-14 15:45:00')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash);

-- ----------------------------------------------------------------------------
-- 3. SEED CUSTOMERS
-- ----------------------------------------------------------------------------
INSERT INTO customers (customer_id, user_id, full_name, email, phone, address, created_at) VALUES
(501, 103, 'Aarav Sharma',       'aarav.sharma@example.com',  '+91 98765 43210', '12 Park Street, Bangalore, Karnataka',    '2026-10-10 09:00:00'),
(502, 104, 'Priya Nair',         'priya.nair@example.com',    '+91 98450 11223', '45 MG Road, Kochi, Kerala',               '2026-10-11 10:00:00'),
(503, 105, 'Rohan Verma',        'rohan.verma@example.com',   '+91 97112 33445', '88 Sector 18, Noida, Uttar Pradesh',      '2026-10-12 11:00:00'),
(504, 106, 'Apex Logistics Pvt', 'finance@apexlogistics.in',  '+91 99001 55667', 'Unit 402 Tech Hub, Mumbai, Maharashtra', '2026-10-13 13:30:00'),
(505, 107, 'Meera Krishnan',     'meera.k@example.com',       '+91 94440 99887', '77 Anna Salai, Chennai, Tamil Nadu',      '2026-10-14 15:45:00')
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

-- ----------------------------------------------------------------------------
-- 4. SEED ACCOUNTS
-- PINs (BCrypt):
--   #1001: 1234
--   #1002: 4321
--   #1003: 9999 (Locked)
--   #1004: 5678
--   #1005: 2468
-- ----------------------------------------------------------------------------
INSERT INTO accounts (account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status, created_at) VALUES
(1001, 501, 'Aarav Sharma',        'SAVINGS',  14500.00, '$2a$10$BYaiXtCpGnM8FDkjrRn3ouf7KitDWfq0dDuvBR/6.5F8kB5QJMH3G', 0, FALSE, 'ACTIVE', '2026-10-10 09:00:00'),
(1002, 502, 'Priya Nair',          'SAVINGS',   8200.00, '$2a$10$ihchGC20agRBLULWo5qiZe5SxyxqwzSFTvS/Lbfpydny15N8gio62', 0, FALSE, 'ACTIVE', '2026-10-11 10:15:00'),
(1003, 503, 'Rohan Verma',         'SAVINGS',   2500.00, '$2a$10$DxF5bn5Wfg0KIeicgbOsTOcPKyromyiiPKvbu9.es0u7PzDi7IVrm', 3, TRUE,  'LOCKED', '2026-10-12 11:30:00'),
(1004, 504, 'Apex Logistics Pvt',  'CURRENT',  -1500.00, '$2a$10$1qnSaLZrR16V7DnupPKqN.yHoyth/z46lq0efpZfGDidyXwfp4LOW', 0, FALSE, 'ACTIVE', '2026-10-13 14:00:00'),
(1005, 505, 'Meera Krishnan',      'CURRENT',  32000.00, '$2a$10$535loy2mfao/GyxZcnmWVe9xYnuABjkfSX7oaLQMIWPe2mOvDQUmS', 0, FALSE, 'ACTIVE', '2026-10-14 16:20:00')
ON DUPLICATE KEY UPDATE balance = VALUES(balance);

-- ----------------------------------------------------------------------------
-- 5. SEED TRANSACTIONS
-- ----------------------------------------------------------------------------
INSERT INTO transactions (transaction_id, transaction_ref, account_number, txn_type, amount, balance_after, remarks, related_account_number, txn_timestamp) VALUES
(1, 'TXN-20261010-001', 1001, 'OPEN',         10000.00, 10000.00, 'Initial Account Opening (SAVINGS)',        NULL, '2026-10-10 09:00:00'),
(2, 'TXN-20261012-002', 1001, 'DEPOSIT',       5000.00, 15000.00, 'Salary NEFT Credit',                       NULL, '2026-10-12 10:05:00'),
(3, 'TXN-20261014-003', 1001, 'WITHDRAW',      1500.00, 13500.00, 'ATM Cash Withdrawal',                      NULL, '2026-10-14 18:22:00'),
(4, 'TXN-20261015-004', 1001, 'TRANSFER_OUT',  1000.00, 12500.00, 'Atomic Transfer to Account #1002',         1002, '2026-10-15 11:10:00'),
(5, 'TXN-20261016-005', 1001, 'DEPOSIT',       2000.00, 14500.00, 'Check Clearance Deposit',                  NULL, '2026-10-16 15:40:00'),

(6, 'TXN-20261011-006', 1002, 'OPEN',          5000.00,  5000.00, 'Initial Account Opening (SAVINGS)',        NULL, '2026-10-11 10:15:00'),
(7, 'TXN-20261013-007', 1002, 'DEPOSIT',       4000.00,  9000.00, 'Counter Cash Deposit',                     NULL, '2026-10-13 09:45:00'),
(8, 'TXN-20261014-008', 1002, 'WITHDRAW',      1500.00,  7500.00, 'Branch POS Withdrawal',                    NULL, '2026-10-14 13:20:00'),
(9, 'TXN-20261015-004', 1002, 'TRANSFER_IN',   1000.00,  8500.00, 'Atomic Transfer from Account #1001',       1001, '2026-10-15 11:10:00'),
(10,'TXN-20261016-009', 1002, 'WITHDRAW',       616.00,  7884.00, 'Utility Bill Debit',                       NULL, '2026-10-16 08:30:00'),
(11,'TXN-20261017-010', 1002, 'INTEREST',       316.00,  8200.00, '4% Yearly Savings Interest Credited',      NULL, '2026-10-17 09:00:00'),

(12,'TXN-20261012-011', 1003, 'OPEN',          3000.00,  3000.00, 'Initial Account Opening (SAVINGS)',        NULL, '2026-10-12 11:30:00'),
(13,'TXN-20261015-012', 1003, 'WITHDRAW',       500.00,  2500.00, 'ATM Cash Withdrawal',                      NULL, '2026-10-15 19:05:00'),

(14,'TXN-20261013-013', 1004, 'OPEN',         12000.00, 12000.00, 'Initial Account Opening (CURRENT)',        NULL, '2026-10-13 14:00:00'),
(15,'TXN-20261014-014', 1004, 'WITHDRAW',      8000.00,  4000.00, 'Fleet Fuel Vendor Payout',                 NULL, '2026-10-14 16:00:00'),
(16,'TXN-20261015-015', 1004, 'TRANSFER_OUT',  3500.00,   500.00, 'Atomic Transfer to Account #1005',         1005, '2026-10-15 17:30:00'),
(17,'TXN-20261016-016', 1004, 'DEPOSIT',       2000.00,  2500.00, 'Client Invoice Settlement',                NULL, '2026-10-16 11:15:00'),
(18,'TXN-20261017-017', 1004, 'WITHDRAW',      4000.00, -1500.00, 'Overdraft Facility Drawdown',              NULL, '2026-10-17 10:00:00'),

(19,'TXN-20261014-018', 1005, 'OPEN',         25000.00, 25000.00, 'Initial Account Opening (CURRENT)',        NULL, '2026-10-14 16:20:00'),
(20,'TXN-20261015-015', 1005, 'TRANSFER_IN',   3500.00, 28500.00, 'Atomic Transfer from Account #1004',       1004, '2026-10-15 17:30:00'),
(21,'TXN-20261016-019', 1005, 'DEPOSIT',       5000.00, 33500.00, 'Merchant Settlement Credit',               NULL, '2026-10-16 14:10:00'),
(22,'TXN-20261017-020', 1005, 'WITHDRAW',      1500.00, 32000.00, 'Office Supplies Disbursement',             NULL, '2026-10-17 11:05:00')
ON DUPLICATE KEY UPDATE amount = VALUES(amount);

-- ----------------------------------------------------------------------------
-- 6. SEED AUDIT LOGS
-- ----------------------------------------------------------------------------
INSERT INTO audit_logs (log_id, actor_username, role, action, entity_type, entity_id, details, status, ip_address, log_timestamp) VALUES
(1, 'admin_rajesh',  'ADMIN',  'SYSTEM_INITIALIZED', 'SYSTEM',  '0',    'SecureBank platform booted and schema verified', 'SUCCESS', '127.0.0.1', '2026-10-01 08:00:00'),
(2, 'teller_suresh', 'TELLER', 'ACCOUNT_CREATED',    'ACCOUNT', '1001', 'Created Savings Account for Aarav Sharma',      'SUCCESS', '127.0.0.1', '2026-10-10 09:00:00'),
(3, 'teller_suresh', 'TELLER', 'ACCOUNT_CREATED',    'ACCOUNT', '1002', 'Created Savings Account for Priya Nair',        'SUCCESS', '127.0.0.1', '2026-10-11 10:15:00'),
(4, 'teller_suresh', 'TELLER', 'ACCOUNT_CREATED',    'ACCOUNT', '1003', 'Created Savings Account for Rohan Verma',       'SUCCESS', '127.0.0.1', '2026-10-12 11:30:00'),
(5, 'aarav',         'CUSTOMER','LOGIN_SUCCESS',     'USER',    '103',  'Customer login via portal',                      'SUCCESS', '127.0.0.1', '2026-10-15 11:08:00'),
(6, 'aarav',         'CUSTOMER','TRANSFER',          'ACCOUNT', '1001', 'Transferred Rs. 1,000.00 to Account #1002',     'SUCCESS', '127.0.0.1', '2026-10-15 11:10:00')
ON DUPLICATE KEY UPDATE details = VALUES(details);
