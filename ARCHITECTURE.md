# SecureBank: Software Architecture & Technical Design Document

This document provides a comprehensive architectural specification of the **SecureBank** core banking management system for university capstone evaluation.

---

## 1. High-Level Architecture & Layered Pattern

SecureBank adopts a classical **4-Tier Layered Architecture** with an embedded asynchronous automation subsystem ensuring strict separation of concerns, high cohesion, loose coupling, and defensive security.

```mermaid
graph TD
    subgraph Tier 1: Presentation Layer
        UI_WEB["Modern Neo-Bank Web App (Port 8080 - HTML5/CSS/JS)"]
        UI_GUI["Desktop Swing Application (BankGuiApp)"]
        UI_CLI["Authenticated Terminal Console (BankApp)"]
    end

    subgraph Tier 2: Service Layer (Business Rules & Automation)
        S_AUTH["AuthService (BCrypt verification, Lockout, Session)"]
        S_ACC["AccountService (Creation, Validation, Soft Closure)"]
        S_BANK["BankingService (ACID Transfers, Deposits, Withdrawals)"]
        S_RECON["ReconciliationService (Automated Ledger Auditing)"]
        S_FRAUD["SuspiciousActivityService (Velocity & Fraud Engine)"]
        S_REPORT["ReportService (Turnover Summaries & RFC 4180 CSV)"]
        S_BACKUP["BackupService (Verified SQL Dumps & Retention)"]
        S_NOTIF["NotificationService (Event-Driven Customer Queue)"]
        S_SCHED["BankingSchedulerService (Periodic Background Tasks)"]
    end

    subgraph Tier 3: Data Access Object (DAO) Layer
        DAO_USER["UserDAO (Credentials & Lockout States)"]
        DAO_CUST["CustomerDAO (Demographics & Profiles)"]
        DAO_BANK["BankDAO (Account Balances, Status & Row Locks)"]
        DAO_TXN["TransactionDAO (Financial Ledger Entries)"]
        DAO_AUDIT["AuditDAO (Security Event Log Records)"]
        DAO_NOTIF["NotificationDAO (Customer Notification Records)"]
        DAO_FRAUD["SuspiciousAlertDAO (Security Incident Records)"]
        DAO_RECON["ReconciliationDAO (Ledger Audit History)"]
        DAO_SCHED["SchedulerDAO (Job Telemetry Records)"]
    end

    subgraph Tier 4: Persistence Layer
        DB_MYSQL[("Production MySQL 8.0+ Database")]
        DB_H2[("Zero-Setup Embedded H2 (MySQL Mode)")]
        DBCFG["DatabaseConfig (Dynamic Fallback & Connection Management)"]
    end

    UI_WEB --> S_AUTH
    UI_WEB --> S_ACC
    UI_WEB --> S_BANK
    UI_WEB --> S_RECON
    UI_WEB --> S_FRAUD
    UI_WEB --> S_REPORT
    UI_WEB --> S_BACKUP
    UI_WEB --> S_NOTIF

    UI_GUI --> S_AUTH
    UI_GUI --> S_ACC
    UI_GUI --> S_BANK
    UI_CLI --> S_AUTH
    UI_CLI --> S_ACC
    UI_CLI --> S_BANK

    S_BANK --> S_FRAUD
    S_BANK --> S_NOTIF
    S_AUTH --> S_FRAUD
    S_AUTH --> S_NOTIF
    S_ACC --> S_NOTIF
    S_SCHED --> S_RECON
    S_SCHED --> S_BACKUP

    S_AUTH --> DAO_USER
    S_AUTH --> DAO_AUDIT
    S_ACC --> DAO_BANK
    S_ACC --> DAO_CUST
    S_ACC --> DAO_AUDIT
    S_BANK --> DAO_BANK
    S_BANK --> DAO_TXN
    S_BANK --> DAO_AUDIT
    S_RECON --> DAO_RECON
    S_RECON --> DAO_BANK
    S_RECON --> DAO_TXN
    S_FRAUD --> DAO_FRAUD
    S_NOTIF --> DAO_NOTIF
    S_SCHED --> DAO_SCHED

    DAO_USER --> DBCFG
    DAO_CUST --> DBCFG
    DAO_BANK --> DBCFG
    DAO_TXN --> DBCFG
    DAO_AUDIT --> DBCFG
    DAO_NOTIF --> DBCFG
    DAO_FRAUD --> DBCFG
    DAO_RECON --> DBCFG
    DAO_SCHED --> DBCFG

    DBCFG --> DB_MYSQL
    DBCFG -.->|Offline Fallback| DB_H2
```

---

## 2. Object-Oriented Domain Hierarchy

```mermaid
classDiagram
    direction TB

    class Account {
        <<abstract>>
        -int accountNumber
        -String holderName
        -AccountType accountType
        -BigDecimal balance
        -String pinHash
        -int failedAttempts
        -AccountStatus status
        -LocalDateTime createdAt
        -String[5] recentTransactions
        +verifyPin(String rawPin) boolean
        +deposit(BigDecimal amount) void
        +recordLocalTransaction(String type, BigDecimal amount, BigDecimal balanceAfter, String remarks) void
        +setRecentTransactions(String[] dbTransactions) void
        +getRecentTransactions() String[]
        +withdraw(BigDecimal amount)* void
        +displayDetails()* void
        +getAccountType() AccountType
        +getBalance() BigDecimal
        +isActive() boolean
        +isLocked() boolean
        +unlockAccount() void
    }

    class SavingsAccount {
        +BigDecimal MIN_BALANCE = 1000.00$
        +BigDecimal INTEREST_RATE = 0.04$
        +SavingsAccount(String holderName, BigDecimal initialBalance, String rawPin)
        +withdraw(BigDecimal amount) void
        +applyYearlyInterest() BigDecimal
        +displayDetails() void
    }

    class CurrentAccount {
        +BigDecimal OVERDRAFT_LIMIT = 10000.00$
        +CurrentAccount(String holderName, BigDecimal initialBalance, String rawPin)
        +withdraw(BigDecimal amount) void
        +getOverdraftUsed() BigDecimal
        +displayDetails() void
    }

    class User {
        -int userId
        -String username
        -String passwordHash
        -Role role
        -int failedLogins
        -boolean isLocked
        -boolean isActive
        -LocalDateTime lastLogin
        +hasPermission(Role requiredRole) boolean
    }

    class Customer {
        -int customerId
        -int userId
        -String fullName
        -String email
        -String phone
        -String address
    }

    class Transaction {
        -long transactionId
        -String transactionRef
        -int accountNumber
        -TransactionType transactionType
        -BigDecimal amount
        -BigDecimal balanceAfter
        -String remarks
        -LocalDateTime timestamp
    }

    class Notification {
        -int notificationId
        -int userId
        -String title
        -String message
        -String type
        -boolean isRead
        -LocalDateTime createdAt
    }

    class SuspiciousActivityAlert {
        -int alertId
        -LocalDateTime timestamp
        -int userId
        -int accountNumber
        -String ruleTriggered
        -String severity
        -String evidence
        -String status
    }

    Account <|-- SavingsAccount : extends
    Account <|-- CurrentAccount : extends
    User "1" -- "0..1" Customer : owns
    Customer "1" -- "0..*" Account : holds
    Account "1" -- "0..*" Transaction : logs
    User "1" -- "0..*" Notification : receives
```

---

## 3. Financial Transaction Workflows & Concurrency Control

### 3.1 Inter-Account Fund Transfer (Deadlock-Free ACID Sequence)

When transferring funds from Account A to Account B, if another concurrent thread simultaneously transfers funds from Account B to Account A, acquiring locks in arbitrary order causes a classic **Distributed Dining Philosophers Deadlock**.

**Deadlock-Safe Ordered Locking Algorithm:**
```java
// SecureBank BankDAO.java ordered locking:
int firstAcc = Math.min(senderAccNo, receiverAccNo);
int secondAcc = Math.max(senderAccNo, receiverAccNo);

Account lock1 = getAccountForUpdate(conn, firstAcc);
Account lock2 = getAccountForUpdate(conn, secondAcc);
```

```mermaid
sequenceDiagram
    autonumber
    actor User as Authenticated Actor
    participant Web as Web / GUI / CLI UI
    participant Svc as BankingService
    participant DAO as BankDAO / TxnDAO
    participant DB as Relational Database (InnoDB)
    participant Notif as NotificationService
    participant Fraud as SuspiciousActivityService

    User->>Web: Initiate Transfer(fromAcc: 1002, toAcc: 1001, amt: ₹5000.00, pin: ****)
    Web->>Svc: transfer(fromAcc, toAcc, amt, pin, actorUser)
    
    Note over Svc: Service validates input: fromAcc != toAcc, amt > 0, actor permission
    Svc->>DB: conn = DatabaseConfig.getConnection()
    Svc->>DB: conn.setAutoCommit(false) [BEGIN TRANSACTION]
    
    Note over Svc,DAO: DEADLOCK PREVENTION: Lock lowest account number first (1001 then 1002)
    Svc->>DAO: getAccountForUpdate(conn, 1001)
    DAO->>DB: SELECT * FROM accounts WHERE account_number = 1001 FOR UPDATE
    DB-->>DAO: Row 1001 Locked Exclusively
    
    Svc->>DAO: getAccountForUpdate(conn, 1002)
    DAO->>DB: SELECT * FROM accounts WHERE account_number = 1002 FOR UPDATE
    DB-->>DAO: Row 1002 Locked Exclusively
    
    Note over Svc: Verify fromAccount is ACTIVE and not LOCKED
    Note over Svc: Verify PIN using PasswordUtil.verifyPin(pin, fromAcc.getPinHash())
    Note over Svc: Polymorphic check: fromAcc.withdraw(amt)
    
    alt Insufficient Balance / Overdraft Limit Exceeded
        Svc->>DB: conn.rollback()
        Svc->>Fraud: recordAlert(REJECTED_WITHDRAWAL_PROBE)
        Svc-->>Web: throw InsufficientFundsException(shortfall)
        Web-->>User: Display Error Alert with calculated shortfall
    else Balance & Policies Valid
        Note over Svc: Debit Source: newBalFrom = fromAcc.getBalance() - amt
        Svc->>DAO: updateBalance(conn, 1002, newBalFrom)
        DAO->>DB: UPDATE accounts SET balance = ? WHERE account_number = 1002
        
        Note over Svc: Credit Destination: newBalTo = toAcc.getBalance() + amt
        Svc->>DAO: updateBalance(conn, 1001, newBalTo)
        DAO->>DB: UPDATE accounts SET balance = ? WHERE account_number = 1001
        
        Note over Svc: Insert immutable double-entry ledger records
        Svc->>DAO: recordTransaction(conn, debitTxn: TRANSFER_OUT)
        Svc->>DAO: recordTransaction(conn, creditTxn: TRANSFER_IN)
        Svc->>DAO: recordTransferDetails(conn, txnRef, 1002, 1001, amt)
        Svc->>DAO: recordAuditLog(conn, "TRANSFER", "SUCCESS")
        
        Svc->>DB: conn.commit() [COMMIT TRANSACTION]
        Svc->>Notif: dispatchNotification(sender, "Transfer Sent", "₹5000.00 to #1001")
        Svc->>Notif: dispatchNotification(receiver, "Transfer Received", "₹5000.00 from #1002")
        Svc-->>Web: return TransactionReceipt
        Web-->>User: Display Transaction Confirmation & Printable Receipt
    end
```

---

## 4. Automated Transaction Reconciliation Sequence

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Administrator / Scheduler
    participant Recon as ReconciliationService
    participant DAO as ReconciliationDAO / TxnDAO
    participant DB as Database
    participant Alert as SuspiciousActivityService

    Admin->>Recon: runReconciliation()
    Recon->>DB: SELECT account_number, balance FROM accounts
    DB-->>Recon: List of all accounts and stored balances

    loop For each account
        Recon->>DB: SELECT txn_type, amount FROM transactions WHERE account_number = ?
        DB-->>Recon: List of historical transaction rows
        Note over Recon: Recompute balance = Sum(OPEN + DEPOSIT + TRANSFER_IN) - Sum(WITHDRAW + TRANSFER_OUT)
        Note over Recon: Compare Stored Balance with Recomputed Balance
        alt Variance != 0.00
            Note over Recon: Flag Discrepancy (Account #, Stored, Calculated, Variance)
            Recon->>Alert: Log HIGH Anomaly Alert
        end
    end

    Recon->>DAO: recordReconciliationRun(total, matched, discrepancies, summary)
    DAO->>DB: INSERT INTO reconciliation_logs ...
    Recon-->>Admin: Return ReconciliationReport (BALANCED / ANOMALIES_DETECTED)
```

---

## 5. User Interface Architecture

SecureBank provides three user interfaces:

1. **Modern Neo-Bank Web Application (`http://localhost:8080`)**:
   - Modern glassmorphism UI styled with CSS variables and gradients.
   - Real-time notification drawer with live unread badge.
   - Dedicated role-based views for Customer, Teller, and Administrator.
   - Live reconciliation triggers, RFC 4180 CSV report downloads, and one-click database backups.
2. **Java Swing Desktop GUI (`BankGuiApp`)**:
   - Native Look-and-Feel desktop graphical interface.
   - Role-specific tabbed panels, PIN masking, and printable receipt preview modals.
3. **Authenticated Terminal CLI (`BankApp`)**:
   - Standalone text-based terminal interface for headless servers or automated scripts.
   - Formatted monetary tables and interactive menus.

---

## 6. Real Authentication, Registration & Session Architecture

### 6.1 Real Database Authentication Flow
```mermaid
sequenceDiagram
    autonumber
    actor User as User / Browser
    participant Web as BankWebServer (/api/auth/login)
    participant Auth as AuthService
    participant DAO as UserDAO
    participant Sess as SessionService
    participant DB as Database (users table)

    User->>Web: POST /api/auth/login { identifier, password }
    Web->>Auth: login(identifier, password, clientIp)
    Auth->>DAO: findByUsernameOrEmail(identifier)
    DAO->>DB: SELECT * FROM users WHERE username = ? OR email = ?
    DB-->>DAO: User entity (with BCrypt hash, lockout status)
    DAO-->>Auth: User entity
    
    alt User Not Found or Account Disabled/Locked
        Auth-->>Web: AuthenticationException ("Invalid username/email or password.")
        Web-->>User: HTTP 401 Unauthorized (Generic Error)
    else Password Mismatch
        Auth->>DAO: incrementFailedLogins(userId)
        alt failed_logins >= 3
            Auth->>DAO: lockUser(userId)
            Auth->>Alert: recordAlert(REPEATED_FAILED_LOGINS)
        end
        Auth-->>Web: AuthenticationException ("Invalid username/email or password.")
        Web-->>User: HTTP 401 Unauthorized
    else Valid Credentials
        Auth->>Auth: BCrypt.checkpw(password, user.getPasswordHash())
        Auth->>DAO: resetFailedLogins(userId)
        Auth->>DAO: updateLastLogin(userId)
        Auth-->>Web: User entity
        Web->>Sess: createSession(user)
        Note over Sess: Generate 32-byte Base64URL token (SecureRandom)
        Note over Sess: Store SessionState in ConcurrentHashMap (In-Memory Only)
        Sess-->>Web: sessionToken
        Web-->>User: HTTP 200 OK { token, user: { username, role } }
    end
```

### 6.2 Atomic Customer Registration Flow
```mermaid
sequenceDiagram
    autonumber
    actor Customer as Public User / Browser
    participant Web as BankWebServer (/api/auth/register)
    participant Auth as AuthService
    participant DAO as UserDAO / CustomerDAO / BankDAO
    participant DB as Database (ACID Transaction)

    Customer->>Web: POST /api/auth/register { fullName, username, email, password, confirmPassword, accountType, initialDeposit }
    Web->>Auth: registerCustomer(...)
    Auth->>Auth: Validate password complexity & match
    Auth->>DAO: checkUniqueness(username, email)
    
    alt Duplicate Username or Email
        Auth-->>Web: ValidationException ("Username/Email already registered")
        Web-->>Customer: HTTP 400 Bad Request
    else Valid Registration
        Auth->>DB: conn.setAutoCommit(false)
        Auth->>DAO: createUser(conn, username, BCrypt(password), Role.CUSTOMER)
        Auth->>DAO: createCustomer(conn, userId, fullName, email, phone)
        Auth->>DAO: createAccount(conn, customerId, accountType, initialDeposit)
        Auth->>DAO: insertTransaction(conn, accountNum, "OPEN", initialDeposit)
        Auth->>DB: conn.commit()
        Auth-->>Web: RegistrationResult (userId, customerId, accountNumber)
        Web-->>Customer: HTTP 201 Created { message: "Account created successfully", accountNumber }
    end
```

### 6.3 In-Memory Session Security Specification
- **Token Generation**: 32 bytes of cryptographic entropy sourced from `java.security.SecureRandom`, encoded using URL-safe Base64 without padding.
- **Storage Policy**: Stored exclusively in server-side `ConcurrentHashMap<String, SessionState>`. Never persisted to database or disk.
- **Sliding Timeout**: 15 minutes of inactivity (`IDLE_TIMEOUT_MILLIS = 15 * 60 * 1000L`). Valid requests update `lastAccessTime`.
- **Zero LocalStorage Policy**: Client web application retains token strictly in JavaScript runtime memory (`sessionState.token`), preventing persistent token theft via XSS or disk inspection.
- **Explicit Invalidation**: Calling `/api/auth/logout` completely deletes the session entry from server memory.

---

## 7. One-Click Windows Background Startup Architecture

```mermaid
graph TD
    User["User Double-Clicks SecureBank.bat"] --> Bat["SecureBank.bat (Batch Wrapper)"]
    Bat --> PS["SecureBank.ps1 (PowerShell Launch Engine)"]
    PS --> EnvCheck["1. Check Prerequisites: JDK & javaw.exe"]
    PS --> RunCheck{"2. Is Port 8080 or PID Active?"}
    RunCheck -- "Yes (Running)" --> OpenBrowser["Launch Google Chrome to http://localhost:8080"]
    RunCheck -- "No" --> StartJavaw["3. Spawn javaw.exe (WindowStyle: Hidden, Detached)"]
    StartJavaw --> SavePID["4. Write Process ID to securebank.pid"]
    SavePID --> PollHealth{"5. Poll http://localhost:8080/api/health (Max 25s)"}
    PollHealth -- "Status: UP" --> OpenBrowser
    PollHealth -- "Timeout" --> ErrorExit["Log Diagnostic Error to logs/securebank.log"]
    OpenBrowser --> ExitLauncher["6. Launcher Exits Cleanly (javaw remains running in background)"]
```

### Startup Architecture Safeguards
1. **Console Suppression**: Using `javaw.exe` rather than `java.exe` prevents any black terminal console from displaying on the user's desktop.
2. **Deterministic PID Verification**: `securebank.pid` stores the background process ID. `Stop-SecureBank.bat` verifies the process command line signature before termination to avoid touching unrelated Java workloads.
3. **Health-Gated Browser Launch**: Chrome is launched only after `/api/health` returns HTTP 200 with `status: "UP"`, guaranteeing that the user never encounters a "Site Cannot Be Reached" error.

