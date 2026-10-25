-- ================================================================================
-- SecureBank: Capstone Automated Test Suite Seed Data
-- Used exclusively by Automated Integration Tests (TestSuiteRunner, EndToEndLiveServerTest)
-- ================================================================================

INSERT INTO users (user_id, username, password_hash, role, failed_logins, is_locked, active) VALUES (101, 'admin_rajesh',  '$2a$12$bbyAA9ZHc/EbHzRITjtcdOGMprkxUwxcr.bXx10n3i0VYRpWTLPdC', 'ADMIN',    0, FALSE, TRUE);
INSERT INTO users (user_id, username, password_hash, role, failed_logins, is_locked, active) VALUES (102, 'teller_suresh', '$2a$12$sx9kyrbTx33gOky0O1EGCubD.jK4HIRek29zghQXNL4pIf4R8zoKO', 'TELLER',   0, FALSE, TRUE);
INSERT INTO users (user_id, username, password_hash, role, failed_logins, is_locked, active) VALUES (103, 'aarav',         '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 0, FALSE, TRUE);
INSERT INTO users (user_id, username, password_hash, role, failed_logins, is_locked, active) VALUES (104, 'priya',         '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 0, FALSE, TRUE);
INSERT INTO users (user_id, username, password_hash, role, failed_logins, is_locked, active) VALUES (105, 'rohan',         '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 3, TRUE,  TRUE);
INSERT INTO users (user_id, username, password_hash, role, failed_logins, is_locked, active) VALUES (106, 'apex_corp',     '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 0, FALSE, TRUE);
INSERT INTO users (user_id, username, password_hash, role, failed_logins, is_locked, active) VALUES (107, 'meera',         '$2a$12$tbAsbsrHjvQ73M4b/GBe0ubARvQDrRyFiDQd8Q7zTc9cKcMYEHmOu', 'CUSTOMER', 0, FALSE, TRUE);

INSERT INTO customers (customer_id, user_id, full_name, email, phone, address) VALUES (501, 103, 'Aarav Sharma',       'aarav.sharma@example.com',  '+91 98765 43210', '12 Park Street, Bangalore, Karnataka');
INSERT INTO customers (customer_id, user_id, full_name, email, phone, address) VALUES (502, 104, 'Priya Nair',         'priya.nair@example.com',    '+91 98450 11223', '45 MG Road, Kochi, Kerala');
INSERT INTO customers (customer_id, user_id, full_name, email, phone, address) VALUES (503, 105, 'Rohan Verma',        'rohan.verma@example.com',   '+91 97112 33445', '88 Sector 18, Noida, Uttar Pradesh');
INSERT INTO customers (customer_id, user_id, full_name, email, phone, address) VALUES (504, 106, 'Apex Logistics Pvt', 'finance@apexlogistics.in',  '+91 99001 55667', 'Unit 402 Tech Hub, Mumbai, Maharashtra');
INSERT INTO customers (customer_id, user_id, full_name, email, phone, address) VALUES (505, 107, 'Meera Krishnan',     'meera.k@example.com',       '+91 94440 99887', '77 Anna Salai, Chennai, Tamil Nadu');

INSERT INTO accounts (account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status) VALUES (1001, 501, 'Aarav Sharma',        'SAVINGS',  14500.00, '$2a$10$BYaiXtCpGnM8FDkjrRn3ouf7KitDWfq0dDuvBR/6.5F8kB5QJMH3G', 0, FALSE, 'ACTIVE');
INSERT INTO accounts (account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status) VALUES (1002, 502, 'Priya Nair',          'SAVINGS',   8200.00, '$2a$10$ihchGC20agRBLULWo5qiZe5SxyxqwzSFTvS/Lbfpydny15N8gio62', 0, FALSE, 'ACTIVE');
INSERT INTO accounts (account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status) VALUES (1003, 503, 'Rohan Verma',         'SAVINGS',   2500.00, '$2a$10$DxF5bn5Wfg0KIeicgbOsTOcPKyromyiiPKvbu9.es0u7PzDi7IVrm', 3, TRUE,  'LOCKED');
INSERT INTO accounts (account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status) VALUES (1004, 504, 'Apex Logistics Pvt',  'CURRENT',  -1500.00, '$2a$10$1qnSaLZrR16V7DnupPKqN.yHoyth/z46lq0efpZfGDidyXwfp4LOW', 0, FALSE, 'ACTIVE');
INSERT INTO accounts (account_number, customer_id, holder_name, account_type, balance, pin, failed_attempts, is_locked, status) VALUES (1005, 505, 'Meera Krishnan',      'CURRENT',  32000.00, '$2a$10$535loy2mfao/GyxZcnmWVe9xYnuABjkfSX7oaLQMIWPe2mOvDQUmS', 0, FALSE, 'ACTIVE');

INSERT INTO transactions (transaction_ref, account_number, txn_type, amount, balance_after, remarks) VALUES ('TXN-INIT-1001', 1001, 'OPEN', 14500.00, 14500.00, 'Initial Opening Balance');
INSERT INTO transactions (transaction_ref, account_number, txn_type, amount, balance_after, remarks) VALUES ('TXN-INIT-1002', 1002, 'OPEN', 8200.00, 8200.00, 'Initial Opening Balance');
INSERT INTO transactions (transaction_ref, account_number, txn_type, amount, balance_after, remarks) VALUES ('TXN-INIT-1003', 1003, 'OPEN', 2500.00, 2500.00, 'Initial Opening Balance');
INSERT INTO transactions (transaction_ref, account_number, txn_type, amount, balance_after, remarks) VALUES ('TXN-INIT-1004', 1004, 'OPEN', 0.00, 0.00, 'Initial Account Facility Opening');
INSERT INTO transactions (transaction_ref, account_number, txn_type, amount, balance_after, remarks) VALUES ('TXN-INIT-1004B', 1004, 'WITHDRAW', 1500.00, -1500.00, 'Commercial Overdraft Drawdown');
INSERT INTO transactions (transaction_ref, account_number, txn_type, amount, balance_after, remarks) VALUES ('TXN-INIT-1005', 1005, 'OPEN', 32000.00, 32000.00, 'Initial Opening Balance');

-- Double-Entry Ledger Initial Records (Vault/Clearing Account #0 balancing Customer accounts)
INSERT INTO ledger_transactions (ledger_txn_id, transaction_ref, txn_type, description, status) VALUES (1, 'TXN-INIT-1001', 'OPEN', 'Opening Deposit for Aarav Sharma', 'POSTED');
INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) VALUES (1, 0, 'DEBIT', 14500.00);
INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) VALUES (1, 1001, 'CREDIT', 14500.00);

INSERT INTO ledger_transactions (ledger_txn_id, transaction_ref, txn_type, description, status) VALUES (2, 'TXN-INIT-1002', 'OPEN', 'Opening Deposit for Priya Nair', 'POSTED');
INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) VALUES (2, 0, 'DEBIT', 8200.00);
INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) VALUES (2, 1002, 'CREDIT', 8200.00);

INSERT INTO ledger_transactions (ledger_txn_id, transaction_ref, txn_type, description, status) VALUES (3, 'TXN-INIT-1003', 'OPEN', 'Opening Deposit for Rohan Verma', 'POSTED');
INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) VALUES (3, 0, 'DEBIT', 2500.00);
INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) VALUES (3, 1003, 'CREDIT', 2500.00);

INSERT INTO ledger_transactions (ledger_txn_id, transaction_ref, txn_type, description, status) VALUES (4, 'TXN-INIT-1005', 'OPEN', 'Opening Deposit for Meera Krishnan', 'POSTED');
INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) VALUES (4, 0, 'DEBIT', 32000.00);
INSERT INTO ledger_entries (ledger_txn_id, account_number, entry_type, amount) VALUES (4, 1005, 'CREDIT', 32000.00);

-- Debit Cards Seed Data
INSERT INTO cards (account_number, customer_id, card_number_masked, card_type, daily_limit, is_blocked, contactless_enabled, expiry_date)
VALUES (1001, 501, '4532 •••• •••• 1001', 'VISA_PLATINUM', 50000.00, FALSE, TRUE, '12/29');
INSERT INTO cards (account_number, customer_id, card_number_masked, card_type, daily_limit, is_blocked, contactless_enabled, expiry_date)
VALUES (1002, 502, '5241 •••• •••• 1002', 'MASTERCARD_GOLD', 25000.00, FALSE, TRUE, '08/28');

-- Beneficiaries Seed Data
INSERT INTO beneficiaries (customer_id, beneficiary_account, beneficiary_name, bank_name, ifsc_code, transfer_limit, status)
VALUES (501, 1002, 'Priya Nair', 'SecureBank', 'SCBK0001001', 50000.00, 'ACTIVE');
INSERT INTO beneficiaries (customer_id, beneficiary_account, beneficiary_name, bank_name, ifsc_code, transfer_limit, status)
VALUES (501, 1005, 'Meera Krishnan', 'SecureBank', 'SCBK0001001', 75000.00, 'ACTIVE');

-- Spending Budgets Seed Data
INSERT INTO budgets (customer_id, category, monthly_limit) VALUES (501, 'SHOPPING', 10000.00);
INSERT INTO budgets (customer_id, category, monthly_limit) VALUES (501, 'FOOD', 8000.00);
INSERT INTO budgets (customer_id, category, monthly_limit) VALUES (501, 'UTILITIES', 5000.00);

-- Notifications Seed Data
INSERT INTO notifications (user_id, title, message, type) VALUES (103, 'Welcome to SecureBank', 'Your Platinum Savings account #1001 is active and ready for digital transactions.', 'INFO');
INSERT INTO notifications (user_id, title, message, type) VALUES (103, 'Monthly Statement Ready', 'Your electronic transaction ledger for the current billing cycle has been reconciled.', 'INFO');
