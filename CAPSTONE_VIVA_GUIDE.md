# SecureBank: Capstone Project Viva-Voce Defense & Technical Guide

This guide is designed for the project team to excel during the university capstone defense and viva-voce examination. It provides concise, technically rigorous answers covering software engineering, Java OOP, database design, cybersecurity, and concurrency.

---

## 1. Project Overview & Elevator Pitch

> **Q: What is SecureBank and what problem does it solve?**
> **A:** SecureBank is a secure, enterprise-grade core banking and financial transaction management system developed in Java and MySQL. It solves the critical integrity and security flaws common in student banking projects: floating-point roundoff errors (eliminated using `BigDecimal`), non-atomic fund transfers (remediated using JDBC ACID transaction boundaries), plaintext credential storage (remediated using salted BCrypt hashing with work factor 12/10), concurrent double-spending (prevented via row-level locking with `SELECT ... FOR UPDATE`), and destructive account deletion (replaced with non-destructive soft closure). It supports three distinct roles (`CUSTOMER`, `TELLER`, `ADMIN`) across both a modern Java Swing graphical desktop application and an authenticated terminal CLI.

---

## 2. Java & Object-Oriented Programming (OOP) Concepts

### Q1: How does SecureBank demonstrate Encapsulation?
**A:** Encapsulation bundles state and behaviors within a class while restricting arbitrary access:
- In `Account`, fields like `balance`, `pinHash`, `status`, and `failedAttempts` are declared `private`.
- Direct mutation of `balance` is prohibited. Balance modifications occur strictly through domain methods (`deposit()`, `withdraw()`) that validate positive amounts, account status, and minimum balance/overdraft boundaries.
- Cryptographic PINs are never stored or exposed as plaintext; only salted hashes are held internally.

---

### Q2: How is Inheritance implemented in the account hierarchy?
**A:** 
- The abstract base class `Account` defines shared state (`accountNumber`, `holderName`, `balance`, `status`) and universal behaviors (`deposit()`, `verifyPin()`, `recordLocalTransaction()`).
- `SavingsAccount` extends `Account` and implements specific rules: mandatory opening balance (`Rs. 1,000.00`), minimum balance protection during withdrawals, and annual interest calculation (`4.0% p.a.`).
- `CurrentAccount` extends `Account` and implements commercial overdraft rules: withdrawal permitted below zero down to `-Rs. 5,000.00`, dynamic tracking of `overdraftUsed`, and strict overdraft limits.

---

### Q3: How is Polymorphism utilized?
**A:** Through **dynamic method overriding (runtime polymorphism)**:
- The base class `Account` declares the abstract method `public abstract void withdraw(BigDecimal amount) throws InsufficientFundsException`.
- When `BankingService` invokes `account.withdraw(amount)`, the JVM dynamically invokes either `SavingsAccount.withdraw()` (enforcing minimum balance) or `CurrentAccount.withdraw()` (enforcing overdraft ceiling) depending on the concrete object instance at runtime, without needing `if-else` or `instanceof` checks.

---

### Q4: Why are custom checked exceptions used instead of generic exceptions?
**A:** Checked exceptions force callers to explicitly handle expected banking business conditions at compile-time:
- `InsufficientFundsException`: Carries the exact shortfall (`shortfall = amount - availableFunds`) so the UI can present an actionable error message to the customer.
- `AuthenticationException`: Conceals whether the username or password failed to prevent credential enumeration attacks.
- `AuthorizationException`: Enforces security boundaries when a user attempts an action unauthorized for their role.
- `InactiveAccountException`: Blocks transactions on closed or frozen accounts.

---

## 3. Financial Precision & Data Integrity

### Q5: Why is `BigDecimal` mandatory for financial calculations instead of `double` or `float`?
**A:** `double` and `float` are binary floating-point types governed by IEEE 754. They represent fractional values as powers of 2 ($1/2, 1/4, 1/8$), which cannot accurately represent common decimal fractions like $0.10$ or $0.05$. For example, `0.1 + 0.2` in `double` yields `0.30000000000000004`. Over thousands of banking transactions, roundoff drift leads to financial reconciliation errors.
`BigDecimal` performs arbitrary-precision base-10 arithmetic. SecureBank configures all monetary calculations with scale 2 and `RoundingMode.HALF_EVEN` (Banker's Rounding), perfectly mapping to SQL `DECIMAL(15,2)`.

---

## 4. Database Architecture, JDBC & ACID Properties

### Q6: What are the ACID properties and how does SecureBank enforce them?
**A:**
- **Atomicity (All-or-Nothing)**: In fund transfers, debiting Account A and crediting Account B are executed within `conn.setAutoCommit(false)`. If any statement fails, `conn.rollback()` undoes all partial updates.
- **Consistency**: Database foreign keys, check constraints, and service-layer validation ensure an account can never violate overdraft or minimum balance boundaries.
- **Isolation**: Prevents concurrent transactions from seeing uncommitted or conflicting data. SecureBank uses row-level locking (`SELECT ... FOR UPDATE`) to isolate account balances during updates.
- **Durability**: The MySQL InnoDB engine writes committed transactions to non-volatile disk storage (Write-Ahead Log / redo log) ensuring transactions persist through power loss or system failure.

---

### Q7: Why do we use `PreparedStatement` instead of `Statement`?
**A:**
1. **Security**: `PreparedStatement` completely eliminates SQL Injection (SQLi) vulnerabilities. User input is treated strictly as data literals by the database driver, never as executable SQL tokens.
2. **Performance**: Prepared statements are pre-compiled and cached by the database server, allowing faster execution on repeated queries with different parameter values.

---

### Q8: How does SecureBank handle multi-database fallback?
**A:** `DatabaseConfig.java` implements automated resilience:
- It first attempts to connect to production MySQL (`jdbc:mysql://localhost:3306/securebank_db`) with a fast 2-second timeout (`connectTimeout=2000`).
- If MySQL is stopped or unavailable, it automatically switches to an embedded in-memory H2 database running in MySQL syntax compatibility mode (`MODE=MySQL;DATABASE_TO_LOWER=TRUE`) and bootstraps the schema and seed data dynamically from `schema_bootstrap.sql`.

---

## 5. Concurrency Control & Deadlock Prevention

### Q9: What is the "Lost Update" problem and how is it prevented?
**A:** A Lost Update occurs when two concurrent threads read the same account balance simultaneously, both compute a new balance in memory, and both write back, with the second write overwriting the first.
- **Solution**: SecureBank uses pessimistic row-level locking (`SELECT balance FROM accounts WHERE account_number = ? FOR UPDATE`). The database places an exclusive lock on the account row, forcing subsequent concurrent transactions to wait until the first transaction commits.

---

### Q10: How does SecureBank prevent database deadlocks during concurrent transfers?
**A:** If Thread 1 transfers from Account 1001 to 1002, while Thread 2 simultaneously transfers from Account 1002 to 1001, acquiring locks in the order requested causes a circular wait deadlock.
- **Solution (Ordered Locking)**: In `BankingService.java`, the system always acquires locks in deterministic primary key order:
  ```java
  int firstAcc = Math.min(fromAcc, toAcc);
  int secondAcc = Math.max(fromAcc, toAcc);
  bankDAO.getAccountForUpdate(conn, firstAcc);
  bankDAO.getAccountForUpdate(conn, secondAcc);
  ```
  Both threads will attempt to lock Account 1001 first. Thread 2 will safely wait for Thread 1 to finish, completely eliminating circular wait conditions.

---

## 6. Cybersecurity & Cryptography

### Q11: Why is BCrypt superior to SHA-256 for password and PIN hashing?
**A:** SHA-256 is a general-purpose cryptographic hash function engineered for speed (gigabytes per second). Modern GPUs can compute billions of SHA-256 hashes per second, making dictionary and rainbow table attacks trivial against 4-digit PINs or short passwords.
BCrypt is a dedicated password hashing algorithm based on the Blowfish cipher:
1. **Built-in Cryptographic Salting**: BCrypt generates a unique 128-bit random salt for each hash, rendering precomputed rainbow tables useless.
2. **Configurable Work Factor**: SecureBank uses work factor **12** for passwords ($2^{12} = 4096$ rounds) and **10** for PINs. This deliberately slows down computation to ~250ms per check, making brute-force attacks computationally infeasible.

---

### Q12: How does the brute-force lockout mechanism work?
**A:** Both `User` and `Account` maintain consecutive failure counters (`failed_logins` and `failed_attempts`):
- Each incorrect password or PIN attempt increments the failure counter.
- A correct attempt resets the counter to 0.
- Reaching **3 consecutive failures** triggers the security state machine: `is_locked` is set to `TRUE`, immediately freezing transactions.
- Once locked, the account cannot transact even if the correct PIN is provided. Only an authorized **Administrator** can unlock the account.

---

### Q13: What is the difference between an Audit Log and a Transaction Ledger?
**A:**
- **Transaction Ledger (`transactions`)**: Double-entry financial records tracking the movement of money (credits, debits, balances, monetary amounts, transfer links).
- **Audit Log (`audit_logs`)**: Security and compliance log tracking who did what, when, and from where (logins, failed attempts, account creation, status changes, administrative unlocks). Audit logs never record raw passwords or PINs.

---

## 7. Account Lifecycle & Regulatory Compliance

### Q14: Why does SecureBank prohibit physical `DELETE FROM accounts`?
**A:** Under financial regulations (RBI, FINRA, Basel III), bank records must be retained for at least 7 to 10 years for forensic accounting and anti-money laundering (AML) compliance. Physically deleting an account would either orphan or delete (`ON DELETE CASCADE`) all historical financial ledger records.
SecureBank implements **Soft Deletion**:
1. An account can only be closed if its balance is strictly zero (`balance == 0.00`).
2. Its status is changed to `AccountStatus.CLOSED`.
3. Closed accounts are blocked from any further transactions (`InactiveAccountException`).
4. All past transaction history, receipts, and audit logs remain queryable forever.

---

### Q15: How does SecureBank prevent Session Hijacking and Token Theft without LocalStorage?
**A:**
1. **Cryptographic Entropy**: Session tokens are 32 random bytes from `SecureRandom`, encoded in URL-safe Base64 without padding (256-bit entropy), eliminating guessability.
2. **Server-Side In-Memory Storage**: Tokens are kept strictly in server memory (`ConcurrentHashMap`), never written to disk or the database.
3. **Zero LocalStorage**: The client JavaScript keeps the active token strictly in closure memory (`sessionState.token`). Closing the tab or restarting the browser eliminates the client token. Malicious scripts or disk forensics cannot harvest tokens from `localStorage`.
4. **Sliding Inactivity Window**: Sessions automatically expire after 15 minutes of inactivity (`15 * 60 * 1000L`).
5. **Instant Invalidation**: Logging out immediately purges the session from server memory.

---

### Q16: How does the customer registration workflow ensure atomic onboarding across multiple tables?
**A:** Customer registration requires creating records in three interdependent tables: `users` (credentials & role), `customers` (KYC profile), and `accounts` (primary account), plus an initial `OPEN` entry in `transactions`.
This is executed within a single database transaction boundary:
1. `conn.setAutoCommit(false)`
2. `UserDAO.createUser(conn, ...)` inserts the user with role `CUSTOMER`.
3. `CustomerDAO.createCustomer(conn, ...)` links the profile using the generated `user_id`.
4. `BankDAO.createAccount(conn, ...)` creates the account using the generated `customer_id`.
5. `TransactionDAO.insertTransaction(conn, ...)` logs the initial opening deposit.
6. `conn.commit()`
If any step fails (e.g. duplicate username, email conflict, or invalid deposit), `conn.rollback()` restores the database to its pristine state.

---

### Q17: How does the One-Click Windows Startup (`SecureBank.bat`) launch the server without a console window?
**A:**
1. Standard Java applications launched with `java.exe` open a persistent Windows Command Prompt console.
2. SecureBank uses **`javaw.exe`** (Java Windows launcher), which is built specifically for GUI and background services that run without allocating a console window.
3. `SecureBank.bat` calls `SecureBank.ps1`, which launches `javaw.exe` in detached mode with `-WindowStyle Hidden`, logs diagnostics directly to `logs\securebank.log`, writes the process ID to `securebank.pid`, and polls `http://localhost:8080/api/health`.
4. Once the health endpoint responds with `status: "UP"`, Google Chrome is automatically launched to `http://localhost:8080`, and the launcher exits cleanly while `javaw.exe` continues running seamlessly in the background.

---

### Q18: What is the testing strategy that ensures 100% verification across all 48 test cases?
**A:**
SecureBank features 12 dedicated automated test suites covering all layers of the architecture:
- **Unit & Domain Tests**: OOP polymorphism, boundary rules, monetary validation, and password/PIN BCrypt hashing.
- **Security & Registration Tests**: Real customer registration, unique constraint enforcement, weak password rejection, and session timeout/invalidation.
- **Integration Tests**: ACID multi-statement deposits, withdrawals, transfers, and rollback behavior.
- **Concurrency Tests**: Multi-threaded deposits, simultaneous overdraft withdrawals, and reciprocal transfers stress-tested without deadlocks.
- **Automation Tests**: Ledger reconciliation discrepancy detection, suspicious activity rule triggers, and automated database backups with retention pruning.
All 48 tests pass 100.0% through `test.bat` with zero external dependencies.

---

## 8. Summary Viva Cheat Sheet for the Team

| Topic | Key Terminology to Mention |
| :--- | :--- |
| **Language & Architecture** | Java 17+/25, 4-Tier Layered Architecture, Clean Separation of Concerns |
| **OOP Pillars** | Encapsulation (private fields), Inheritance (`Account` -> `Savings`/`Current`), Polymorphism (`withdraw()`), Abstraction (`DAO`/`Service`) |
| **Financial Integrity** | `BigDecimal`, Scale 2, `ROUND_HALF_EVEN`, SQL `DECIMAL(15,2)` |
| **ACID & Concurrency** | `setAutoCommit(false)`, `commit()`, `rollback()`, `SELECT ... FOR UPDATE`, Ordered Account Locking |
| **Security & Auth** | Real DB Auth (Dual Username/Email), Salted BCrypt (Cost 12/10), 3-Strike Lockout, RBAC (`CUSTOMER`, `TELLER`, `ADMIN`), 32-Byte In-Memory Session Tokens |
| **Database Resilience** | MySQL 8.0+ InnoDB, Automated fallback to embedded H2 (MySQL Mode) |
| **Compliance & Auditing** | Non-destructive soft closure (`AccountStatus.CLOSED`), Immutable `audit_logs` |
| **Windows Startup Automation** | `SecureBank.bat` (silent `javaw.exe`, PID tracking, health polling, auto Chrome launch), `Stop-SecureBank.bat`, `SecureBank-Status.bat` |
| **Testing Verification** | 48/48 Tests Passed (100.0%) across 12 suites, Concurrency & Deadlock Stress Testing |
