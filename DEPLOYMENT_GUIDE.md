# SecureBank: Production Deployment & Operations Guide

This guide provides end-to-end instructions for deploying, configuring, monitoring, and operating the **SecureBank Core Banking Platform** in both local development environments and mission-critical production environments.

---

## 1. System Requirements & Architecture Overview

| Component | Minimum Specification | Recommended Production Specification |
| :--- | :--- | :--- |
| **Operating System** | Windows 10/11, macOS 12+, Ubuntu 20.04+ | Ubuntu Server 22.04 LTS / Windows Server 2022 |
| **Java Runtime** | OpenJDK 17 LTS | Eclipse Temurin 17 or 21 LTS |
| **Database** | Embedded H2 (`MODE=MySQL`) | MySQL 8.0+ Enterprise / AWS Aurora MySQL |
| **Memory (RAM)** | 2 GB Available | 4 GB+ (Heap configured with `-Xms1g -Xmx2g`) |
| **Storage** | 500 MB Free Disk Space | 20 GB+ NVMe SSD (Encrypted for DB and Backups) |
| **Network** | TCP Port 8080 (Configurable) | Reverse Proxy on Ports 80/443 with TLS 1.3 |

---

## 2. Configuration & Externalized Secrets Management

SecureBank strictly adheres to Twelve-Factor App principles. Passwords, cryptographic keys, and database connection strings are never stored permanently in source code.

### 2.1 Environment Configuration File (`.env`)

Copy the template from `.env.example`:

```bash
cp .env.example .env
```

Configure environment properties:

```ini
# ==========================================
# SECUREBANK RUNTIME CONFIGURATION
# ==========================================
PORT=8080
APP_ENV=production

# Database Connection (Leave empty to use zero-config embedded H2 in MySQL mode)
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=securebank_db
DB_USER=securebank_app
DB_PASSWORD=SuperSecretProdPassword!2026

# Session Security Parameters
SESSION_TIMEOUT_MINUTES=30
MAX_LOGIN_FAILURES=3

# Background Automation Scheduling
AUTOMATION_ENABLED=true
RECONCILIATION_INTERVAL_SECONDS=60
BACKUP_INTERVAL_HOURS=1
BACKUP_RETENTION_DAYS=7
BACKUP_STORAGE_DIR=./backups/
```

### 2.2 Dual Database Strategy (Zero-Config Fallback)

SecureBank incorporates an automated connection resilience engine (`DatabaseConfig.java`):
1. **Primary Target**: Connects to the designated MySQL 8.0 server using connection pooling.
2. **Resilience Fallback**: If MySQL is unreachable, the system automatically transitions to an isolated embedded H2 database running in strict MySQL compatibility mode (`MODE=MySQL;DATABASE_TO_LOWER=TRUE`).
3. **Audit Log Notice**: Any failover event is logged with timestamped system alerts.

---

## 3. Windows Native One-Click Deployment

For workstation evaluation, demonstration, and college capstone vivas, SecureBank includes Windows orchestration scripts:

### 3.1 Initial Setup (`Setup-SecureBank.bat`)
Run once to verify environment prerequisites, compile dependencies, execute 48 automated test suites, and bootstrap database tables:
```cmd
Setup-SecureBank.bat
```

### 3.2 Background Startup Engine (`SecureBank.bat`)
Launches SecureBank completely detached in the background without keeping a persistent command window open:
```cmd
SecureBank.bat
```
**Startup Lifecycle:**
1. Discovers `javaw.exe` (Windows GUI Java Launcher).
2. Verifies TCP Port 8080 is available. If an unrelated application holds the port, it reports the conflict safely without killing foreign processes.
3. Spawns `BankWebServer` in the background and writes the verified Process ID to `securebank.pid`.
4. Actively polls `http://localhost:8080/api/health` until the ACID engine confirms `status: UP`.
5. Automatically opens Google Chrome directly to the application dashboard.

### 3.3 Status & Diagnostic Monitoring (`SecureBank-Status.bat`)
Inspects active process health, database status, port listeners, and active session count:
```cmd
SecureBank-Status.bat
```

### 3.4 Safe Process Termination (`Stop-SecureBank.bat`)
Gracefully shuts down the background server:
```cmd
Stop-SecureBank.bat
```
*Security Guardrail:* It reads `securebank.pid`, verifies that the process command line matches the SecureBank signature (`BankWebServer`), and terminates only the intended banking process.

---

## 4. Production Linux Deployment (Systemd Service)

In production enterprise Linux environments, manage SecureBank as a supervised systemd service:

### 4.1 Service Definition (`/etc/systemd/system/securebank.service`)

```ini
[Unit]
Description=SecureBank Core Banking Platform
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=securebank
Group=securebank
WorkingDirectory=/opt/securebank
EnvironmentFile=/opt/securebank/.env
ExecStart=/usr/bin/java -Xms1024m -Xmx2048m -cp "target/classes:lib/*" com.securebank.ui.web.BankWebServer
Restart=always
RestartSec=10
LimitNOFILE=65536

# Security Hardening Directives
ProtectSystem=full
ProtectHome=true
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

### 4.2 Activation Commands

```bash
sudo systemctl daemon-reload
sudo systemctl enable securebank
sudo systemctl start securebank
sudo systemctl status securebank
```

---

## 5. Nginx Reverse Proxy with TLS/SSL Hardening

Deploy Nginx in front of SecureBank to manage SSL termination, HTTP/2 multiplexing, and security headers:

```nginx
server {
    listen 80;
    server_name banking.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name banking.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/banking.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/banking.yourdomain.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Security Headers
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com;" always;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

---

## 6. Automated Backup, Verification & Disaster Recovery

### 6.1 Scheduled Automated Backups
- The background `ScheduledAutomationEngine` executes hourly database snapshots.
- Every archive is written to `./backups/securebank_backup_YYYYMMDD_HHMMSS.sql`.
- **Automated Verification:** The `BackupService` immediately executes syntax analysis, schema completeness checks, and byte length verification. Corrupted backups are rejected.
- **Automated Pruning:** Snapshots exceeding the 7-day retention window are automatically removed.

### 6.2 Manual Backup Trigger
```cmd
backup.bat
```
*(or via PowerShell: `.\backup.ps1`)*

### 6.3 Disaster Recovery Procedure
To restore the database from a backup snapshot:

1. Stop the application:
   ```cmd
   Stop-SecureBank.bat
   ```
2. Locate the most recent verified backup file in `./backups/`.
3. If using MySQL, restore via client:
   ```bash
   mysql -u securebank_app -p securebank_db < backups/securebank_backup_20261017_190000.sql
   ```
4. If using H2, replace the local database file or restart the server with the backup script.
5. Restart the server and run status verification:
   ```cmd
   SecureBank.bat
   SecureBank-Status.bat
   ```
6. Run an automated ledger reconciliation audit:
   ```cmd
   reconcile.bat
   ```

---

## 7. Operational Health Checks & Telemetry

### 7.1 Health Endpoint (`GET /api/health`)
Returns real-time engine vitals:
```json
{
  "status": "UP",
  "service": "SecureBank Web Engine",
  "database": "CONNECTED",
  "activeSessions": 2,
  "port": 8080,
  "timestamp": "2026-10-17T19:12:32"
}
```

### 7.2 Core Banking Runbook Commands

| Task | Command Line | Description |
| :--- | :--- | :--- |
| **Compile & Build** | `build.bat` *(or `mvn clean compile`)* | Compiles Java classes to `target/classes` |
| **Run All 48 Tests** | `test.bat` *(or `.\test.ps1`)* | Runs the complete unit and integration test suite |
| **Verify Everything** | `verify.bat` | End-to-end certification of all components |
| **Manual Reconciliation**| `reconcile.bat` | Runs automated ledger audit across all accounts |
| **Financial Report** | `report.bat` | Generates turnover and transaction summary |
| **Database Backup** | `backup.bat` | Creates a verified database SQL snapshot |
| **Start Server** | `SecureBank.bat` | Starts hidden background server & opens Chrome |
| **Check Server Status** | `SecureBank-Status.bat` | Checks process, port, and health |
| **Stop Server** | `Stop-SecureBank.bat` | Terminates only the SecureBank process |

---

## 8. Incident Response & Troubleshooting

| Symptom | Probable Cause | Remediation |
| :--- | :--- | :--- |
| **Port 8080 conflict** | Another process is using port 8080 | Run `SecureBank-Status.bat` to identify the process; configure a different `PORT` in `.env` if necessary. |
| **Login rejected (400)** | Empty password or 3 failed attempts | Check that credentials are supplied; if locked, an administrator must unlock the account via `/api/admin/unlock`. |
| **Access Denied (403)** | Missing Bearer token or IDOR violation | Ensure client includes `Authorization: Bearer <token>`; verify customer is only querying their own account number. |
| **Database disconnected** | MySQL service stopped | Start MySQL (`net start MySQL80` or `systemctl start mysql`); SecureBank will automatically fall back to embedded H2 if offline. |
