# SecureBank: Cybersecurity Audit & Threat Mitigation Review

This document presents a comprehensive cybersecurity vulnerability assessment, threat modeling (STRIDE), and hardening verification for the **SecureBank** core banking platform.

---

## 1. Threat Modeling & Vulnerability Inventory

During the codebase inspection of the legacy banking code, high-risk security and financial vulnerabilities were identified and remediated in the enhanced codebase:

| # | Vulnerability Category | Legacy Implementation Risk | Severity | Remediated Status |
| :- | :--- | :--- | :--- | :--- |
| **1** | **Cryptographic Storage** | Plaintext PINs (`CHAR(4)`) stored in DB and memory | **CRITICAL** | **FIXED** (Salted BCrypt work factor 10) |
| **2** | **Credential Storage** | Plaintext / Fast-hash passwords without salting | **CRITICAL** | **FIXED** (BCrypt work factor 12, $2^{12}$ iterations) |
| **3** | **SQL Injection (SQLi)** | String concatenation in ad-hoc queries | **CRITICAL** | **FIXED** (100% Parameterized `PreparedStatement`) |
| **4** | **Credential Hardcoding** | Hardcoded `root` password in Java source code | **HIGH** | **FIXED** (Externalized env vars with `.env.example`) |
| **5** | **Authorization Bypass** | Open execution of financial APIs without role checks | **CRITICAL** | **FIXED** (Service-tier RBAC enforcement) |
| **6** | **Insecure Direct Object Reference (IDOR)**| Customer could query or operate on another's account | **CRITICAL** | **FIXED** (Ownership validation & critical alert logging) |
| **7** | **Financial Precision** | IEEE 754 `double` used for currency math | **HIGH** | **FIXED** (`BigDecimal` scale 2 `ROUND_HALF_EVEN`) |
| **8** | **Atomicity Failure** | Non-transactional transfers; debit without credit | **CRITICAL** | **FIXED** (Explicit JDBC ACID transactions) |
| **9** | **Concurrency Race Conditions**| Simultaneous withdrawals causing double-spending | **CRITICAL** | **FIXED** (Row-level locking `SELECT ... FOR UPDATE`) |
| **10**| **Deadlocks on Transfers** | Reciprocal transfers acquiring locks out of order | **HIGH** | **FIXED** (Deterministic primary key ordering) |
| **11**| **Audit Destruction** | `DELETE FROM accounts` cascades and deletes history | **CRITICAL** | **FIXED** (Soft closure + immutable `audit_logs`) |
| **12**| **Fraud & Velocity Attacks**| Unmonitored rapid burst transfers or login stuffing | **HIGH** | **FIXED** (Automated Suspicious Activity Monitor) |
| **13**| **Ledger Tampering Risk** | Silent account balance alteration | **CRITICAL** | **FIXED** (Automated Reconciliation Auditor) |

---

## 2. In-Depth Technical Remediations

### 2.1 Cryptographic Storage: Salted BCrypt (Passwords & PINs)
- **Problem**: Plaintext PINs in database columns and memory allowed database administrators or compromised SQL dumps to immediately access customer funds.
- **Remediation**: Integrated `jBCrypt-0.4.jar` in `PasswordUtil.java`.
  - **User Passwords**: Hashed with work factor **12** (`BCrypt.gensalt(12)`). Requires ~250ms of CPU compute per attempt, neutralizing GPU-accelerated hashcat cracking.
  - **Transaction PINs**: Hashed with work factor **10** (`BCrypt.gensalt(10)`).
  - **In-Memory Masking**: Visual interfaces use masked password inputs and zero character buffers after verification.

### 2.2 SQL Injection (SQLi) Defense
- **Problem**: Legacy database operations used dynamic string construction (`"SELECT * FROM accounts WHERE account_number = " + accNum`).
- **Remediation**: Enforced 100% parameterized `PreparedStatement` usage across all DAO implementations (`BankDAO`, `UserDAO`, `CustomerDAO`, `TransactionDAO`, `AuditDAO`, `NotificationDAO`, `SuspiciousAlertDAO`, `ReconciliationDAO`).
- **Verification**: Passing automated security test suite `ValidationTest.java` verifying character sanitization and literal parameter binding.

### 2.3 Role-Based Access Control (RBAC) & Principle of Least Privilege
- **Problem**: Authorization checks existed only in the presentation menu. An unauthorized caller invoking the service method directly could bypass UI gates.
- **Remediation**: Implemented strict RBAC enforcement directly in `AuthService`, `AccountService`, and `BankingService`.
  - **CUSTOMER**: Restricted strictly to viewing and operating on their own linked account (`account.getCustomerId() == customer.getCustomerId()`).
  - **TELLER**: Permitted to assist customers with deposits, withdrawals, and account opening; barred from staff administration or system configuration.
  - **ADMINISTRATOR**: Permitted to manage staff, unlock accounts, review audit logs, run reconciliation, inspect fraud alerts, and trigger backups; cannot withdraw funds from personal accounts without active customer credentials.

### 2.4 Insecure Direct Object Reference (IDOR) Protection
- **Problem**: If a customer changed the `account_number` parameter in a request to another customer's ID, legacy services would display or operate on that account.
- **Remediation**: In `BankingService`, every customer interaction validates account ownership:
  ```java
  if (actor.getRole() == Role.CUSTOMER) {
      Customer cust = customerDAO.findByUserId(actor.getUserId());
      if (cust == null || account.getCustomerId() != cust.getCustomerId()) {
          suspiciousActivityService.checkUnauthorizedAccess(actor.getUserId(), actor.getUsername(), account.getAccountNumber());
          throw new AuthorizationException("Access Denied: You do not own account #" + accountNumber);
      }
  }
  ```
  Any attempt to access another customer's account immediately logs a CRITICAL security violation with the client's IP and user ID.

### 2.5 Brute-Force & Credential Stuffing Defense
- **Problem**: Attackers could submit infinite password guesses or brute-force a 4-digit PIN in seconds.
- **Remediation**: Enforced an automated **3-Attempt Lockout State Machine**:
  1. On each invalid password attempt, `failed_logins` is incremented.
  2. On each invalid PIN attempt, `failed_attempts` is incremented.
  3. Upon reaching **3 consecutive failed attempts**, the account or user is immediately locked (`is_locked = TRUE`).
  4. Subsequent authentication or transaction requests are rejected with a generic security notice, preventing credential enumeration.
  5. The fraud heuristic `REPEATED_FAILED_LOGINS` creates a CRITICAL alert and dispatches a customer warning notification.
  6. Only an authorized **Administrator** can unlock a locked account via the admin dashboard.

### 2.6 Deadlock-Safe Concurrency & Double-Spending Prevention
- **Problem**: Simultaneous withdrawals could result in double-spending. Simultaneous transfers between Accounts 1001 and 1002 in opposite directions could cause database deadlocks.
- **Remediation**:
  1. **Row-Level Exclusive Locking**: All balance inquiries within transaction boundaries use `SELECT balance, status, pin_hash FROM accounts WHERE account_number = ? FOR UPDATE`.
  2. **Ordered Account Locking**: In `BankDAO.transferFunds()`, account numbers are sorted, ensuring the lower primary key is always locked first:
     ```java
     int firstAcc = Math.min(senderAccNo, receiverAccNo);
     int secondAcc = Math.max(senderAccNo, receiverAccNo);
     // Acquire lock on firstAcc, then on secondAcc
     ```
  3. **Verification**: Multithreaded integration tests in `ConcurrencyTest.java` simulate 20 concurrent threads attempting simultaneous withdrawals and reciprocal transfers without lost updates, overdraft breaches, or deadlocks.

### 2.7 Automated Suspicious Activity Monitoring & Fraud Heuristics
The platform features an active heuristic engine (`SuspiciousActivityService`):
1. **`RAPID_TRANSACTION_BURST`**: Flags $> 5$ financial transactions within 10 minutes on a single account.
2. **`HIGH_VALUE_TRANSACTION`**: Flags single transactions exceeding ₹50,000.00.
3. **`REJECTED_WITHDRAWAL_PROBE`**: Detects repeated withdrawal attempts rejected due to insufficient funds.
4. **`REPEATED_FAILED_LOGINS`**: Triggers immediate account lockout and alerts administrators.
5. **`UNAUTHORIZED_ACCESS_ATTEMPT`**: Detects and logs IDOR attacks with IP address and user identification.

### 2.8 Automated Ledger Reconciliation Auditor
The reconciliation engine (`ReconciliationService`) operates in read-only detection mode to independently recompute expected balances from the immutable transactions ledger and compare them against stored account balances. Any variance is immediately flagged as a high-severity anomaly without silently altering balances.

### 2.9 Elimination of Demo-Only Authentication & Client Role Tampering
- **Problem**: Prior iterations displayed demonstration role buttons ("Login as Customer", "Login as Teller", "Login as Admin") on the webpage. This bypassed real password checks and allowed client-side manipulation of active roles.
- **Remediation**:
  1. Complete removal of demo role buttons, predefined credentials, and automatic demo authentication from `index.html` and `app.js`.
  2. The login interface requires manual entry of authentic database-backed credentials (`username` or `email` + `password`).
  3. Server-enforced role assignment: During customer registration, the backend strictly assigns the `CUSTOMER` role (`user.setRole(Role.CUSTOMER)`). Any client-submitted role fields are disregarded.
  4. Privileged roles (`TELLER`, `ADMIN`) cannot be registered publicly; they must be provisioned through protected administrative setup.

### 2.10 Cryptographic Session Management & In-Memory Isolation
- **Problem**: Storing session tokens or credentials in browser `localStorage` exposes them to XSS exfiltration and persistent local disk extraction.
- **Remediation**:
  1. **Token Entropy**: Tokens are 32 random bytes generated by `java.security.SecureRandom`, encoded in URL-safe Base64 without padding (256 bits of cryptographic entropy).
  2. **Server-Side In-Memory Storage**: Tokens are maintained exclusively within a thread-safe `ConcurrentHashMap` in `SessionService`. Tokens are never stored on disk or logged to console/files.
  3. **Sliding Window Inactivity Timeout**: Sessions automatically expire after 15 minutes of inactivity (`15 * 60 * 1000L`). Valid API requests advance the `lastAccessTime`.
  4. **Client In-Memory Storage**: In `app.js`, tokens are stored only in a JavaScript closure variable (`sessionState.token`), ensuring that closing the tab or clearing the runtime destroys the client token.
  5. **Explicit Invalidation**: Calling `/api/auth/logout` explicitly deletes the token from server memory and cleans the client session state.

---

## 3. Production Deployment Considerations & Residual Limitations

While SecureBank is hardened to enterprise standards for an academic capstone, the following enhancements should be implemented prior to live production deployment:
1. **Transport Layer Security (TLS/HTTPS)**: In a public production deployment, all HTTP traffic must be encrypted using TLS 1.3 with valid CA-signed certificates.
2. **Hardware Security Modules (HSM)**: Master encryption keys and PIN blocks in Tier-1 commercial institutions are processed within dedicated HSMs (FIPS 140-2 Level 3).
3. **Database Connection Pooling**: High-traffic deployments should introduce a managed connection pool (e.g., HikariCP) configured with max connection pools and leak detection.
4. **Multi-Factor Authentication (MFA)**: Addition of TOTP (Time-Based One-Time Passwords) or SMS OTP gateways for high-value transactions.
