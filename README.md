# SecureBank: Complete Banking Management & Automation System
**Enterprise Capstone Project in Advanced Object-Oriented Software Engineering, Cybersecurity & Full-Stack Automation**

[![Java](https://img.shields.io/badge/Java-25%20%2F%2017%2B-blue.svg)](https://www.oracle.com/java/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-orange.svg)](https://www.mysql.com/)
[![Database Compatibility](https://img.shields.io/badge/Fallback-H2%20MySQL%20Mode-green.svg)](https://www.h2database.com/)
[![Security](https://img.shields.io/badge/Security-BCrypt%20WorkFactor%2012-red.svg)](https://en.wikipedia.org/wiki/Bcrypt)
[![Tests](https://img.shields.io/badge/Test%20Suite-48%2F48%20PASS%20(100%25)-brightgreen.svg)]()
[![Automation](https://img.shields.io/badge/Automation-Fully%20Integrated-blueviolet.svg)]()
[![Platform](https://img.shields.io/badge/Windows-One--Click%20Startup-success.svg)]()

---

## 1. Executive Summary & Objective

**SecureBank** is an enterprise-grade automated banking web application engineered to solve the critical vulnerabilities, concurrency flaws, and lack of real authentication commonly found in academic banking prototypes.

The system replaces all demonstration-only logins with **real database-backed authentication, self-service customer registration, in-memory cryptographic session tokens, server-enforced role-based access control (RBAC), and true ACID banking operations**.

Furthermore, SecureBank delivers a **One-Click Windows Startup** mechanism (`SecureBank.bat`) that launches the Java backend silently in the background (with no persistent console window), monitors the operational health endpoint, and automatically opens Google Chrome to the login page.

---

## 2. Key Capabilities & Architecture

```
+---------------------------------------------------------------------------------------+
|                                PRESENTATION LAYER                                     |
|  Neo-Bank Web Application (Port 8080)  |  BankGuiApp (Swing)  |  BankApp (Auth CLI)   |
|  - Real DB Login (Dual Username/Email) |  - In-Memory Session Tokens (32-byte Base64)  |
|  - Real Customer Registration & KYC    |  - Real-Time Password Strength Verification   |
+---------------------------------------------------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
|                                   SERVICE LAYER                                       |
|  AuthService      | AccountService    | BankingService        | ReconciliationService |
|  (BCrypt/Lockouts)| (Onboarding/Close)| (ACID/BigDecimal/Lock)| (Ledger Audit/Formulas|
|  -----------------------------------------------------------------------------------  |
|  SessionService   | SuspiciousActivityService | ReportService | BackupService         |
|  (32-Byte Tokens) | (Velocity & Fraud Engine) | (CSV Exports) | (Verified SQL Dumps)  |
+---------------------------------------------------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
|                           DATA ACCESS OBJECT (DAO) LAYER                              |
|  UserDAO   | CustomerDAO | BankDAO | TransactionDAO | AuditDAO | NotificationDAO      |
|  SuspiciousAlertDAO      | ReconciliationDAO        | SchedulerDAO                    |
+---------------------------------------------------------------------------------------+
                                            |
                                            v
+---------------------------------------------------------------------------------------+
|                                 PERSISTENCE LAYER                                     |
|  MySQL 8.0+ (Production InnoDB Engine) <--> Embedded H2 (Zero-Setup Auto Fallback)    |
+---------------------------------------------------------------------------------------+
```

### 2.1 Real Database Authentication & Registration
- **No Demo Roles on Login Page**: All demonstration logins, automatic bypasses, and hardcoded identity buttons were completely eliminated from the client interface.
- **Dual-Identifier Sign In**: Users can authenticate using either their registered **username** or their **email address**.
- **BCrypt Salting & Hashing**: Passwords use BCrypt with cost factor 12 ($2^{12}$ iterations); transaction PINs use cost factor 10.
- **Real Customer Registration**: Public registration dynamically validates input, verifies username and email uniqueness, checks password complexity, and atomically commits:
  1. `User` record assigned strictly the `CUSTOMER` role.
  2. `Customer` profile linked to the User record.
  3. Primary `SavingsAccount` with initial deposit verification and initial `OPEN` ledger entry.
- **Cryptographic Session Management (`SessionService`)**: Issues cryptographically secure 32-byte Base64URL session tokens (`SecureRandom`). Tokens are held strictly in server memory, expire after 15 minutes of idle time, and are invalidated immediately upon logout. No authentication credentials are ever stored in browser `localStorage`.

### 2.2 Complete Financial Operations & ACID Integrity
- **Exact Monetary Precision**: Uses Java `BigDecimal` (scale 2, `ROUND_HALF_EVEN`) and MySQL `DECIMAL(15,2)` everywhere. Floating-point types (`double`/`float`) are prohibited.
- **Row-Level Locking**: Concurrent operations use `SELECT ... FOR UPDATE` with deterministic primary key ordering (`Math.min(acc1, acc2)` -> `Math.max(acc1, acc2)`) to prevent deadlocks and double-spending.
- **Atomic Double-Entry Ledger**: Deposits, withdrawals, and inter-account transfers are recorded as immutable ledger entries. If any step fails, the entire transaction rolls back cleanly.

### 2.3 Automated Banking Intelligence
- **Reconciliation Engine (`ReconciliationService`)**: Independently re-computes authoritative balances by replaying transaction histories and highlights anomalies without silent data corruption.
- **Suspicious Activity Monitoring (`SuspiciousActivityService`)**: Automated heuristic detection of brute-force lockouts, rapid velocity bursts, repeated withdrawal probes, and unauthorized IDOR account access.
- **Automated SQL Backups (`BackupService`)**: Automated portable SQL database dumps with rolling 7-day retention pruning and verification checks.
- **Automated CSV Reports (`ReportService`)**: Streaming RFC 4180 CSV export of transaction journals and security audit trails.

---

## 3. One-Click Windows Startup & Control Scripts

SecureBank features dedicated Windows control scripts designed for seamless background operation:

| Windows Script | PowerShell Script | Functionality |
| :--- | :--- | :--- |
| **`SecureBank.bat`** | `SecureBank.ps1` | **One-Click Launch**: Starts Java backend in the background using `javaw.exe` (no console window!), saves PID to `securebank.pid`, polls `/api/health`, and automatically opens Google Chrome to `http://localhost:8080`. |
| **`Stop-SecureBank.bat`** | `Stop-SecureBank.ps1` | **Graceful Shutdown**: Reads PID file, verifies that the process command-line signature belongs to SecureBank, and terminates only the bank server without touching unrelated Java processes. |
| **`SecureBank-Status.bat`** | `SecureBank-Status.ps1` | **Operational Health Card**: Displays running PID, port 8080 status, REST health endpoint state, database engine connectivity, active sessions, and log paths. |
| **`Setup-SecureBank.bat`** | `Setup-SecureBank.ps1` | **Automated Setup**: Verifies JDK compiler and runtime, compiles all classes, runs the 48-test verification suite, and certifies system readiness. |

---

## 4. Getting Started

### Prerequisites
- **Java Development Kit (JDK)**: Java 17 LTS, 21 LTS, or 25 (e.g. `E:\java file\jdk-25.0.2` or configured on `PATH`).
- **Web Browser**: Google Chrome (or default system browser).
- **Database**: MySQL 8.0+ *(Optional: If MySQL is not present, SecureBank automatically initializes the embedded in-memory H2 database in MySQL compatibility mode)*.

### Quick Start (One Double-Click)
1. Double-click **`SecureBank.bat`** in the project root folder.
2. The launcher will start the backend invisibly in the background.
3. Google Chrome will automatically open to `http://localhost:8080`.
4. Log in with an existing account or click **Open Customer Account** to register a new user!

### Checking Server Health
Double-click **`SecureBank-Status.bat`** at any time to inspect:
```
===============================================================================
           SECUREBANK -- OPERATIONAL HEALTH AND STATUS MONITOR                 
===============================================================================

[COMPONENT STATUS REPORT]
-------------------------------------------------------------------------------
  Application URL     : http://localhost:8080
  Background Process  : RUNNING (Active background process)
  Process Identifier  : 17548
  TCP Port 8080       : LISTENING (Bound to PID 17548)
  Health REST Endpoint: ONLINE (UP - Service responding normally)
  Database Engine     : CONNECTED (ACID Engine Active)
  Active Sessions     : 1
  Primary Log File    : E:\SecureBank_Capstone_Source\logs\securebank.log
  Error Diagnostics   : E:\SecureBank_Capstone_Source\logs\securebank_err.log
-------------------------------------------------------------------------------

[SUMMARY] Status: HEALTHY -- All services operating within normal parameters.
===============================================================================
```

### Stopping the Server
Double-click **`Stop-SecureBank.bat`**. It will safely stop the verified background process and free port 8080.

---

## 5. Development Seed Accounts

For development and evaluation, the database contains pre-seeded accounts across all three roles (passwords and PINs hashed with BCrypt):

| Role | Username / Identifier | Password | Account # | Type | Initial Balance | Default PIN |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Customer** | `aarav` | `Aarav@123` | 1001 | Savings | ₹14,500.00 | `1234` |
| **Customer** | `priya` | `Priya@123` | 1002 | Savings | ₹8,200.00 | `2345` |
| **Customer** | `rohan` | `Rohan@123` | 1003 | Savings (Locked) | ₹2,500.00 | `3456` |
| **Customer** | `apex_corp` | `Apex@123` | 1004 | Current (Overdraft)| -₹1,500.00 | `4567` |
| **Customer** | `meera` | `Meera@123` | 1005 | Current | ₹32,000.00 | `5678` |
| **Teller** | `teller_suresh` | `Teller@123` | N/A | Counter Ops | N/A | N/A |
| **Admin** | `admin_rajesh` | `Admin@123` | N/A | Security & Audit | N/A | N/A |

> **Security Note**: These credentials are not exposed on the website. Users must type their credentials into the real login form.

---

## 6. Automated Verification Suite (48/48 PASS)

To run the complete automated test suite:
```cmd
.\test.bat
```
*(or run `.\test.ps1` in PowerShell)*

### Test Suite Execution Summary
- **Total Test Suites**: 12
- **Total Tests Executed**: 48
- **Tests Passed**: 48 (100.0%)
- **Tests Failed**: 0

Suites include:
1. `AccountHierarchyTest` (7 tests): OOP polymorphism, minimum opening balance, overdraft boundaries, interest calculation, circular transaction buffer.
2. `ValidationTest` (3 tests): Monetary boundaries, PIN format validation, username/password constraints.
3. `PasswordUtilTest` (2 tests): Salted BCrypt hashing and verification for passwords and PINs.
4. `AuthServiceTest` (3 tests): Valid dual login, brute-force lockout threshold, RBAC hierarchy.
5. `CustomerRegistrationTest` (9 tests): Registration atomic creation, duplicate username rejection, duplicate email rejection, password complexity, savings minimum opening policy, password change verification.
6. `SessionSecurityTest` (4 tests): 32-byte token generation, 15-minute sliding idle timeout, explicit logout invalidation, web security endpoint gates.
7. `BankingServiceIntegrationTest` (6 tests): Atomic deposits, withdrawals, fund transfers, transactional rollbacks, account closure.
8. `ConcurrencyTest` (3 tests): Thread-safe simultaneous deposits, overdraft protection on concurrent withdrawals, bidirectional transfer deadlock avoidance.
9. `ReconciliationTest` (2 tests): Balanced ledger verification, induced anomaly detection.
10. `SuspiciousActivityTest` (4 tests): High-value transactions, brute-force lockouts, unauthorized IDOR probes, alert lifecycle state updates.
11. `ReportServiceTest` (3 tests): Turnover summaries, transaction CSV export, audit trail CSV export.
12. `BackupServiceTest` (2 tests): Automated verified SQL backup creation, rolling retention pruning policy.

---

## 7. Documentation Directory

For panel review and full capstone examination, consult the comprehensive documentation suite:

1. [ARCHITECTURE.md](ARCHITECTURE.md): Multi-tier layered architecture, design patterns, and sequence diagrams.
2. [DATABASE_DESIGN.md](DATABASE_DESIGN.md): 3NF schema, entity relationships, constraints, indexes, and ACID transaction rules.
3. [SECURITY_REVIEW.md](SECURITY_REVIEW.md): STRIDE threat analysis, vulnerability mitigations, cryptographic standards, and RBAC defense.
4. [TEST_REPORT.md](TEST_REPORT.md): Complete verification matrix of all 48 automated tests.
5. [AUTOMATION_GUIDE.md](AUTOMATION_GUIDE.md): Reconciliation formulas, fraud heuristics, and scheduler specifications.
6. [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md): Backup formats, retention policies, and recovery verification procedures.
7. [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md): Production deployment, systemd configuration, Nginx TLS proxy, and operational runbooks.
8. [CAPSTONE_VIVA_GUIDE.md](CAPSTONE_VIVA_GUIDE.md): In-depth oral examination questions and answers on OOP, JDBC, MySQL, ACID, and security.
