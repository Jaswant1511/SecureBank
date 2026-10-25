# SecureBank: Complete Demonstration & Evaluation Script

This guide outlines the complete 20-step reproducible demonstration sequence for academic evaluators, software architects, and examination panels.

---

## Pre-Requisites & Application Startup

1. **Build & Verify Test Suite**:
   ```cmd
   build.bat
   test.bat
   ```
   *(Expected output: 89/89 tests passed with 100% success).*

2. **Start the Banking Application**:
   ```cmd
   run.bat
   ```
   *(Server starts at `http://localhost:8080/index.html` or `http://localhost:8080/login.html`).*

---

## Demonstration Credentials Matrix

| Role | Username | Password | Account / Target | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Customer** | `aarav` | `Aarav@Secure2026!` | Acc `#1001` (Savings) | Primary retail banking customer |
| **Customer** | `priya` | `Priya@Secure2026!` | Acc `#1002` (Savings) | Secondary customer (Transfer recipient) |
| **Teller** | `teller_suresh` | `Teller@Secure2026!` | Counter Operations | Branch cash counter officer |
| **Administrator**| `admin` | `Admin@Secure2026!` | System Management | SIEM, Ledger audit & Reconciliation |

---

## Reproducible Demonstration Sequence

### Step 1: Customer Authentication & Session Security
- Navigate to `http://localhost:8080/login.html`.
- Enter Username: `aarav`, Password: `Aarav@Secure2026!`.
- Click **Sign In**.
- **Inspect**:
  - Secure session token generated (64-byte cryptographic hex string).
  - Browser automatically directs to Customer Dashboard.
  - Profile dropdown displays KYC Status (`✓ Verified`), 2FA indicator, and masked token identifier.

### Step 2: Customer Account Overview & Wealth Portfolio
- **Inspect**:
  - Available Wealth: Savings Account `#1001` with balance.
  - Virtual Platinum Debit Card: Displays masked account `**** **** **** 1001`, chip graphics, and 3D card tilt.
  - Balance privacy toggle (`👁️` eye icon): Hides/unhides live monetary balances.

### Step 3: Executing an Atomic Fund Transfer
- Click **Send Money** (or Fund Transfer in sidebar).
- Source Account: `#1001`.
- Destination Account: `1002` (`Priya Sharma`).
- Amount: `₹ 2,500.00`.
- Security PIN: `1234`.
- Remarks: `Dinner reimbursement`.
- Click **Execute Transfer**.
- **Inspect**:
  - Real-time modal receipt generated with unique reference (`TXN-...`).
  - Account `#1001` balance decremented by exactly ₹2,500.00.
  - Transaction ledger updated with timestamp, counterparty, and debit badge.
  - Double-entry ledger atomically posts debit to Account `#1001` and credit to Account `#1002`.

### Step 4: Proving Transfer Invariants (Overdraft & Balance Protection)
- Open **Fund Transfer** modal again.
- Source: `#1001`.
- Destination: `1002`.
- Amount: `₹ 50,00,000.00` (exceeds balance).
- PIN: `1234`.
- Click **Execute Transfer**.
- **Inspect**:
  - Server immediately rejects with `Insufficient funds: Requested transfer exceeds available balance`.
  - Transaction rolls back atomically; balance remains unchanged.

### Step 5: Proving Idempotency & Duplicate Request Protection
- Network retries or rapid repeated submissions with the same idempotency key are rejected by `idempotency_keys` table constraints, preventing duplicate charges.

### Step 6: Smart Spending Insights & Budgets (Innovation 3)
- On the Customer Dashboard, inspect the **Monthly Inflow & Spending** card:
  - Total Inflow vs. Total Outflow updated in real-time.
  - Net Savings Rate percentage dynamically calculated.
  - Category breakdown displays automatic classification of `FOOD` (from dinner reimbursement).

### Step 7: Managing Payees / Beneficiaries (Section 10)
- In the Customer sidebar, click **Manage Beneficiaries**.
- Fill in:
  - Full Name: `Vikram Patel`
  - Account Number: `1003`
  - Bank Name: `SecureBank Commercial`
  - IFSC Code: `SCBK0001001`
  - Limit: `₹ 50,000`
- Click **Add Beneficiary**.
- **Inspect**: Beneficiary record persists in `beneficiaries` database table and renders in the active payees register.

### Step 8: Virtual Debit Card Controls
- Click **Card Controls** on the Customer Dashboard.
- Adjust ATM daily limit to `₹ 35,000.00` and POS limit to `₹ 75,000.00`.
- Click **Save Settings**.
- Click **Freeze Card** button:
  - Card visual turns grayscale (frozen).
  - Server updates card status to `BLOCKED`.
- Click **Unfreeze Card**: Card returns to active colored state.

### Step 9: Utility Bill Payment Simulation
- Under Banking Services on the Dashboard, click **Pay Utility Bills**.
- Select Biller: `BESCOM Electricity`.
- Consumer Number: `BES1089234`.
- Amount: `₹ 1,450.00`.
- PIN: `1234`.
- Click **Pay Utility Bill**.
- **Inspect**: Payment reference generated, account balance debited, and record journaled to `bill_payments` table.

### Step 10: High-Yield Fixed Deposit Booking
- Click **High-Yield Fixed Deposit**.
- Amount: `₹ 10,000.00`.
- Tenure: `2 Years @ 7.50% p.a.`
- Guaranteed Maturity Value automatically calculated via compound interest formula.
- Enter PIN `1234` and click **Confirm & Book FD**.
- **Inspect**: Principal debited from savings, fixed deposit created in `fixed_deposits` table.

### Step 11: Instant Personal Loan Application
- Click **Instant Personal Loan**.
- Adjust range slider to `₹ 1,50,000.00` with 24 months tenure.
- Monthly EMI dynamically calculated: `₹ 6,887.00`.
- Click **Disburse Loan to Account**.
- **Inspect**: Application created in `loans` table; receipt generated.

### Step 12: Branch Teller Operations
- Click **Secure Logout** from profile menu.
- Navigate to `http://localhost:8080/teller-login.html`.
- Sign in as `teller_suresh` / `Teller@Secure2026!`.
- **Inspect Teller Terminal**:
  - Summary metrics: Customers served, transaction counts, ledger total.
  - Customer lookup: Search `aarav` or account `1001` to view customer balance.

### Step 13: Teller Counter Cash Deposit
- Click **Cash Deposit** tile.
- Target Account: `1001`.
- Deposit Amount: `₹ 15,000.00`.
- Depositor Name: `Aarav Sharma (Self)`.
- Cash Denominations Verified: Checkbox enabled.
- Click **Process Cash Deposit**.
- **Inspect**: Counter deposit credited, receipt generated, and double-entry ledger debits Vault Account `#0` and credits Customer Account `#1001`.

### Step 14: Synthesizing a Suspicious Activity Event (Innovation 1)
- Attempt an unauthorized transaction or high-value anomaly:
  - Execute a high-value transfer or simulated rapid burst.
- The `FraudEngineService` triggers rule `HIGH_VALUE_TRANSACTION` (+40 risk points) or `RAPID_TRANSACTION_BURST` (+25 risk points).
- An alert is immediately recorded in the `suspicious_activity_alerts` table.

### Step 15: Administrator Portal & SIEM Investigation
- Log out and log in as `admin` / `Admin@Secure2026!` at `http://localhost:8080/admin-login.html`.
- In Administrator Sidebar, click **SIEM Security Alerts**.
- **Inspect**:
  - Open alerts table displaying timestamp, rule triggered, target account/user, severity badge, risk score (e.g. `HIGH (85/100)`), and human-readable explanation.
- Click **Resolve** on an alert:
  - Administrator decision recorded.
  - Audit log captures admin resolution action.

### Step 16: Double-Entry General Ledger Integrity Audit (Innovation 2)
- In Administrator Sidebar, click **Double-Entry General Ledger**.
- **Inspect**:
  - Equations audited: $\sum \text{Debits} = \sum \text{Credits}$.
  - Net System Variance: `₹ 0.00`.
  - Accounts Audited: All accounts verified.
  - Status: `100% BALANCED`.

### Step 17: Automated Reconciliation Engine
- Click **Ledger Reconciliation**.
- System executes `ReconciliationService.runReconciliation()`.
- Calculates expected balances from ledger transactions and verifies matching current balances.
- Toast notification displays: `Reconciliation PASSED: All accounts verified with zero ledger discrepancies!`.

### Step 18: Security Audit Trail CSV Export
- Click **Security Audit Logs**.
- Browser downloads `audit_logs.csv` containing cryptographically logged actions with timestamps, actors, IP addresses, and outcomes.
- Formula injection protection (`=`, `+`, `-`, `@`) is verified.

### Step 19: Encrypted Database Backup Creation
- Click **Database Backup**.
- Server generates a SQL backup dump in the backups directory with SHA-256 integrity hash and logs execution time.

### Step 20: Hardened Destructive Operation Protection
- Verify that destructive database resets (`POST /api/admin/clear-all-data`) require `Role.ADMIN` session tokens and confirmation payload `"CONFIRM_CLEAR_ALL_DATA"`, with audit records preserved. Ordinary customers and tellers receive HTTP 403 Forbidden.
