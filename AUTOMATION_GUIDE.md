# SECUREBANK — AUTOMATION SYSTEM GUIDE & RUNBOOK

## 1. Executive Summary & Philosophy

SecureBank is an automated enterprise core banking system built on Java, JDBC, and MySQL/embedded H2. The automation philosophy strictly follows the core tenet:
> **Automate all repetitive and rule-based operations wherever technically appropriate, while NEVER automating sensitive financial actions without proper authorization, customer verification, ACID transaction boundaries, and immutable audit logging.**

This runbook documents every automated workflow, background scheduler service, fraud heuristic rule, ledger reconciliation auditor, and recovery protocol in the system.

---

## 2. Automated Banking Operations Workflows

### 2.1 Automated Account Creation & Onboarding
* **Trigger**: Customer self-registration or teller onboarding request (`/api/accounts/create`).
* **Automated Steps**:
  1. Input parameter sanitization and validation (holder name, account type, initial deposit, PIN complexity).
  2. Uniqueness check across database accounts.
  3. System assigns a cryptographically unique 4-to-6 digit account number.
  4. Applies product policy: Minimum balance enforcement (₹1,000 for SAVINGS) or overdraft facility setup (up to ₹10,000 limit for CURRENT).
  5. Salting and BCrypt hashing of PIN (cost factor 10).
  6. Atomic insertion into `accounts` and initial `transactions` ledger (`TXN-INIT-...`).
  7. Event notification auto-dispatched to customer inbox: *"Welcome to SecureBank. Your account is active."*
  8. Immutable audit event recorded with actor metadata and IP address.

### 2.2 Automated Deposit Pipeline
* **Trigger**: Authorized customer or teller deposit submission (`/api/banking/deposit`).
* **Automated Steps**:
  1. Server-side session authentication verification and CSRF-safe input parsing.
  2. Role authorization check: Customer permitted only to deposit to own account; Teller permitted branch-wide.
  3. Account status verification (`ACTIVE` vs `LOCKED`/`FROZEN`/`CLOSED`).
  4. Monetary amount validation (`amount > 0`, maximum single-deposit ceiling ₹500,000).
  5. Acquire pessimistic row-lock (`SELECT balance, status FROM accounts WHERE account_number = ? FOR UPDATE`).
  6. Calculate new balance using exact `BigDecimal` arithmetic.
  7. Update account balance atomically.
  8. Append immutable financial transaction record (`txn_type = 'DEPOSIT'`).
  9. Generate cryptographically random, timestamped transaction reference (`TXN-YYYYMMDD-XXXXXX`).
  10. Execute real-time fraud monitoring heuristics (e.g., check for high-value transaction burst > ₹50,000).
  11. Dispatch in-app transaction confirmation notification to customer.
  12. Log audit trail (`ACTION = 'DEPOSIT'`).
  13. Commit database transaction and return structured receipt.

### 2.3 Automated Withdrawal Pipeline
* **Trigger**: Authorized withdrawal submission (`/api/banking/withdraw`).
* **Automated Steps**:
  1. Validate monetary amount (`BigDecimal > 0`).
  2. Verify account status and ownership or teller delegation rights.
  3. Validate account PIN via salted BCrypt verification (`BCrypt.checkpw()`).
     * On failure: Increment `failed_attempts`. If `failed_attempts >= 3`, automatically lock account (`is_locked = TRUE, status = 'LOCKED'`), log security audit event, trigger fraud alert (`REPEATED_FAILED_LOGINS`), and dispatch warning notification.
  4. Acquire pessimistic row-lock (`FOR UPDATE`) to prevent concurrent race conditions.
  5. Retrieve authoritative balance and evaluate account-type rules:
     * **SAVINGS**: Require `balance - amount >= ₹1,000.00` (Minimum Balance Rule).
     * **CURRENT**: Allow overdraft up to `balance - amount >= -₹10,000.00` (Overdraft Limit Rule).
  6. If insufficient funds: Roll back, trigger fraud heuristic (`REJECTED_WITHDRAWAL_PROBE`), log failed audit event, and return error.
  7. Deduct amount atomically, record append-only `WITHDRAW` ledger entry.
  8. Reset `failed_attempts` to 0 upon successful PIN verification.
  9. Commit transaction, dispatch transaction receipt notification, and return receipt.

### 2.4 Automated Fund Transfer (Double-Entry Engine)
* **Trigger**: Inter-account fund transfer submission (`/api/banking/transfer`).
* **Automated Steps**:
  1. Validate distinct accounts (`senderAccNo != receiverAccNo`).
  2. Authenticate sender ownership or teller override.
  3. Validate sender PIN against BCrypt hash.
  4. **Deadlock Prevention Algorithm**: Order locks strictly by account number:
     ```java
     int firstLock = Math.min(senderAccNo, receiverAccNo);
     int secondLock = Math.max(senderAccNo, receiverAccNo);
     // SELECT FOR UPDATE firstLock, then SELECT FOR UPDATE secondLock
     ```
  5. Verify both accounts are `ACTIVE`.
  6. Verify sender balance meets minimum balance / overdraft constraints.
  7. Compute sender new balance and receiver new balance via `BigDecimal`.
  8. Update sender balance and receiver balance atomically.
  9. Generate atomic double-entry ledger records:
     * Outbound: `TRANSFER_OUT` on sender account.
     * Inbound: `TRANSFER_IN` on receiver account.
  10. Record transfer linkage in `transfer_details` table with unique `transfer_ref`.
  11. Check fraud heuristics (velocity bursts within 10-minute window).
  12. Dispatch notifications to both sender and receiver.
  13. Commit transaction. On any exception, entire unit rolls back atomically.

---

## 3. Automated Transaction Reconciliation Auditor

### 3.1 Overview
The **Reconciliation Engine** (`com.securebank.service.ReconciliationService`) independently recalculates the expected balance of every account in the system directly from the append-only transactions ledger and compares it against the stored account balance.

### 3.2 Authoritative Formula
$$\text{Calculated Balance} = \sum \text{OPEN} + \sum \text{DEPOSIT} + \sum \text{TRANSFER\_IN} + \sum \text{INTEREST} - \sum \text{WITHDRAW} - \sum \text{TRANSFER\_OUT}$$

### 3.3 Operating Principles
* **Read-Only Detection Mode**: Reconciliation NEVER silently modifies or overwrites balances. Any discrepancy is flagged with the exact variance, recorded in `reconciliation_logs`, and escalated via high-severity alerts to Administrators.
* **Command-Line Runner**: `reconcile.bat` / `reconcile.ps1` executes standalone audits.
* **REST API**: `POST /api/reconciliation/run` allows authorized administrators to trigger audits from the Web UI.
* **Audit History**: `GET /api/reconciliation/history` retrieves past reconciliation audits with full itemized breakdown of any detected anomalies.

---

## 4. Automated Suspicious Activity Monitoring & Fraud Engine

### 4.1 Heuristics & Detection Rules
Implemented in `com.securebank.service.SuspiciousActivityService`:

| Rule Name | Severity | Detection Logic | Automated Action |
|:---|:---:|:---|:---|
| `REPEATED_FAILED_LOGINS` | **CRITICAL** | User or account records $\ge 3$ consecutive authentication failures. | Locks account, logs audit event, creates critical alert, sends customer warning notification. |
| `RAPID_TRANSACTION_BURST` | **HIGH** | Account records $> 5$ financial transactions within a rolling 10-minute window. | Flags velocity burst anomaly, records evidence timestamp window. |
| `HIGH_VALUE_TRANSACTION` | **MEDIUM** | Single deposit, withdrawal, or transfer exceeds ₹50,000.00. | Logs high-value outlier alert for administrative surveillance. |
| `REJECTED_WITHDRAWAL_PROBE` | **MEDIUM** | Repeated withdrawal attempts rejected due to insufficient funds. | Flags probing pattern, alerts teller/admin. |
| `UNAUTHORIZED_ACCESS_ATTEMPT` | **CRITICAL** | Customer attempts IDOR access to another customer's account. | Rejects request with HTTP 403, logs security violation with IP address. |

### 4.2 Alert Lifecycle
Alerts progress through standard states: `OPEN` $\rightarrow$ `INVESTIGATING` $\rightarrow$ `RESOLVED` / `DISMISSED`.
Administrators can update alert statuses directly via `POST /api/suspicious/update-status` or the Admin Dashboard UI.

---

## 5. Automated Financial Reports & RFC 4180 CSV Export

### 5.1 Reporting Engine
Implemented in `com.securebank.service.ReportService`:
* **Period Summaries**: Computes total deposits, withdrawals, transfers, net turnover, active accounts, and transaction counts across `DAILY`, `WEEKLY`, `MONTHLY`, and `ALL-TIME` ranges.
* **RFC 4180 CSV Stream Export**:
  * `GET /api/reports/export/transactions`: Exports the complete financial ledger with headers, escaped quotes, and accurate timestamps.
  * `GET /api/reports/export/audit-logs`: Exports system audit logs for external compliance auditing.
* **Command-Line CLI**: `report.bat` / `report.ps1` generates terminal summaries and writes CSVs to `target/reports/`.

---

## 6. Automated Event Notifications

* **Engine**: `com.securebank.service.NotificationService` & `NotificationDAO`.
* **Privacy & Security**: Notifications contain descriptive operational details but NEVER include PINs, password hashes, or session tokens.
* **Real-Time Web UI Integration**:
  * Notification Bell with live unread badge counter in header.
  * Interactive dropdown drawer displaying latest events.
  * One-click "Mark All as Read" (`POST /api/notifications/mark-read`).

---

## 7. Automated Background Job Scheduler

### 7.1 Scheduler Architecture
* **Class**: `com.securebank.service.BankingSchedulerService`
* **Concurrency**: Managed via Java `ScheduledExecutorService` with daemon thread pool.
* **Guards**: `AtomicBoolean` execution locks ensure recurring tasks never overlap or duplicate operations.
* **Persistent Telemetry**: Every scheduled job execution is logged into `scheduled_job_logs` with execution time, outcome status (`SUCCESS` / `FAILED`), and metrics.

### 7.2 Scheduled Recurring Workflows
1. **Periodic Transaction Reconciliation**: Automatically audits financial ledgers every 60 minutes.
2. **Expired Session Cleanup**: Purges inactive login sessions and cache objects every 30 minutes.
3. **Daily Backup Job**: Runs scheduled database export snapshots every 24 hours.

---

## 8. Failure Recovery & Bounded Retries

* **Financial Operations**: Retries are NEVER performed blindly if the outcome of a financial debit/credit is indeterminate. Transactions fail fast, rollback completely, and display authoritative diagnostics.
* **Transient Database Errors**: Idempotent read operations and read-only reports utilize bounded retries (up to 3 attempts with exponential backoff).
* **Database Connection Fallback**: If MySQL becomes temporarily unreachable on startup, `DatabaseConfig` transparently switches to the embedded H2 in-memory engine (MySQL compatibility mode) to prevent system downtime.
