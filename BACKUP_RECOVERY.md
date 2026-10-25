# SECUREBANK — DATABASE BACKUP & DISASTER RECOVERY RUNBOOK

## 1. Overview & Strategy

SecureBank provides an automated database backup and disaster recovery subsystem implemented in `com.securebank.service.BackupService` and controlled via CLI tools (`backup.bat` / `backup.ps1`) and the Administrator Web Interface (`POST /api/backup/create`).

### Key Strategy Principles
1. **Zero Hardcoded Secrets**: Backup processes use externalized credentials from environment variables (`DB_URL`, `DB_USER`, `DB_PASSWORD`) or system security contexts.
2. **Deterministic SQL Archives**: Backups are written as portable SQL dump scripts with DDL table definitions and DML INSERT statements.
3. **Automated Verification**: Every backup archive is automatically inspected upon creation to verify that all core tables (`users`, `accounts`, `transactions`, `audit_logs`) and expected records are intact.
4. **Automated Retention Enforcement**: Backups older than the retention threshold (default: 7 days) are automatically pruned to prevent unbounded disk usage.
5. **Non-Destructive Operations**: Backups and restores never overwrite production databases without explicit human authorization.

---

## 2. Backup Execution Procedures

### 2.1 One-Command Standalone Backup
To execute a verified database backup from the terminal:

**Windows Command Prompt:**
```cmd
backup.bat
```

**PowerShell:**
```powershell
.\backup.ps1
```

### 2.2 Output Artifacts
Archives are created in the project's root `backups/` directory:
```
backups/securebank_backup_YYYYMMDD_HHMMSS.sql
```

Sample Terminal Output:
```
================================================================================
        SECUREBANK AUTOMATED DATABASE BACKUP & RECOVERY ENGINE                  
================================================================================
INFO: Database backup created successfully: securebank_backup_20261017_161015.sql (11984 bytes, 23 ms, verified: true)
Backup Status          : SUCCESS
Archive File           : securebank_backup_20261017_161015.sql
Full Path              : E:\SecureBank_Capstone_Source\backups\securebank_backup_20261017_161015.sql
Size in Bytes          : 11984 bytes
Integrity Verified     : true
Duration               : 23 ms
================================================================================
```

### 2.3 Automated Scheduled Backups
The background `BankingSchedulerService` executes automated database backups once every 24 hours. Each execution is logged in the `scheduled_job_logs` table.

### 2.4 Administrative Web Trigger
Administrators can trigger an immediate backup by clicking **"Run Verified Backup Now"** on the Administrator Dashboard, which invokes `POST /api/backup/create` and returns the verified archive filename and size.

---

## 3. Archive Structure & Contents

Each `.sql` backup file contains:
1. **Header Metadata**: Generation timestamp, system version, and schema mode.
2. **Schema DDL**: `CREATE TABLE IF NOT EXISTS` statements for:
   * `roles`
   * `users`
   * `customers`
   * `accounts`
   * `transactions`
   * `transfer_details`
   * `audit_logs`
   * `notifications`
   * `suspicious_alerts`
   * `reconciliation_logs`
   * `scheduled_job_logs`
3. **Ledger & Master Data DML**: Parameterized `INSERT` statements preserving all accounts, balances, passwords, PIN hashes, and transaction history.
4. **Verification Trailer**: Integrity checksum confirming successful serialization.

---

## 4. Disaster Recovery & Restoration Procedures

### 4.1 Prerequisites
1. Ensure the target MySQL or H2 instance is operational.
2. Verify you have administrative credentials with schema creation privileges.

### 4.2 Restoration on MySQL
To restore a backup archive into a MySQL server:

```bash
# 1. (Optional) Create clean database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS securebank_db;"

# 2. Execute restoration script
mysql -u root -p securebank_db < backups/securebank_backup_YYYYMMDD_HHMMSS.sql
```

### 4.3 Restoration on Embedded H2
When running in offline/testing mode with H2, the application automatically bootstraps from the embedded resource `schema_bootstrap.sql`. To manually load a specific backup archive:

```java
try (Connection conn = DatabaseConfig.getConnection()) {
    String sqlDump = Files.readString(Path.of("backups/securebank_backup_YYYYMMDD_HHMMSS.sql"));
    DatabaseConfig.executeSqlScript(conn, sqlDump);
}
```

### 4.4 Post-Restoration Verification Checklist
Immediately after restoring a database, execute the verification pipeline:
```cmd
verify.bat
```
This ensures:
1. All table schemas and constraints are active.
2. The 35 automated integration tests pass.
3. The automated reconciliation auditor reports `BALANCED` with 0 discrepancies.

---

## 5. Retention Policy & Maintenance

* **Default Retention Window**: 7 Days (`RETENTION_DAYS = 7`).
* **Pruning Logic**: During every backup run, `BackupService.pruneOldBackups()` scans the `backups/` directory for files matching `securebank_backup_*.sql` and deletes files with a last modified date exceeding 7 days.
* **Audit Trail**: Every retention pruning event logs the number of deleted files in the system audit trail.
