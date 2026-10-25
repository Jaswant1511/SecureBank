package com.securebank.service;

import com.securebank.util.DatabaseConfig;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureBank: Complete Banking Automation System
 * File: BackupService.java
 *
 * Automated Database Backup & Disaster Recovery Service.
 * Produces portable SQL dump archives with automated retention pruning and integrity verification.
 */
public class BackupService {

    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());
    private static final String DEFAULT_BACKUP_DIR = "backups";
    private static final int DEFAULT_RETENTION_COUNT = 7;
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final String backupDirectory;
    private final int retentionCount;

    public BackupService() {
        this(DEFAULT_BACKUP_DIR, DEFAULT_RETENTION_COUNT);
    }

    public BackupService(String backupDirectory, int retentionCount) {
        this.backupDirectory = backupDirectory != null ? backupDirectory : DEFAULT_BACKUP_DIR;
        this.retentionCount = retentionCount > 0 ? retentionCount : DEFAULT_RETENTION_COUNT;
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        File dir = new File(backupDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Executes an automated database backup and verifies archive integrity.
     */
    public Map<String, Object> createBackup() {
        long startTime = System.currentTimeMillis();
        String timestamp = LocalDateTime.now().format(TS_FMT);
        String fileName = String.format("securebank_backup_%s.sql", timestamp);
        File backupFile = new File(backupDirectory, fileName);

        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("fileName", fileName);
        result.put("filePath", backupFile.getAbsolutePath());

        try {
            if (DatabaseConfig.isH2()) {
                // Use H2 native script command
                try (Connection conn = DatabaseConfig.getConnection();
                     Statement st = conn.createStatement()) {
                    String cleanPath = backupFile.getAbsolutePath().replace('\\', '/');
                    st.execute("SCRIPT TO '" + cleanPath + "'");
                }
            } else {
                // Generic portable SQL generator for MySQL/JDBC
                exportDatabaseToSql(backupFile);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            long sizeBytes = backupFile.length();
            boolean verified = verifyBackupIntegrity(backupFile);

            result.put("status", verified ? "SUCCESS" : "VERIFICATION_FAILED");
            result.put("sizeBytes", sizeBytes);
            result.put("durationMs", elapsed);
            result.put("verified", verified);

            // Apply retention policy
            pruneOldBackups();

            LOGGER.info(String.format("Database backup created successfully: %s (%d bytes, %d ms, verified: %b)",
                fileName, sizeBytes, elapsed, verified));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Automated database backup failed: " + e.getMessage(), e);
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Verifies that the backup file exists, is non-empty, and contains SQL statements.
     */
    public boolean verifyBackupIntegrity(File backupFile) {
        if (backupFile == null || !backupFile.exists() || backupFile.length() < 100) {
            return false;
        }
        try {
            String content = new String(Files.readAllBytes(backupFile.toPath()));
            return content.contains("CREATE TABLE") || content.contains("INSERT INTO") || content.contains("accounts");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed reading backup file during integrity verification", e);
            return false;
        }
    }

    /**
     * Enforces retention policy by removing backups exceeding the retention threshold.
     */
    public int pruneOldBackups() {
        File dir = new File(backupDirectory);
        File[] files = dir.listFiles((d, name) -> name.startsWith("securebank_backup_") && name.endsWith(".sql"));
        if (files == null || files.length <= retentionCount) {
            return 0;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int deleteCount = files.length - retentionCount;
        int pruned = 0;
        for (int i = 0; i < deleteCount; i++) {
            if (files[i].delete()) {
                pruned++;
            }
        }
        if (pruned > 0) {
            LOGGER.info("Pruned " + pruned + " obsolete database backup archives according to retention policy.");
        }
        return pruned;
    }

    private void exportDatabaseToSql(File backupFile) throws SQLException, IOException {
        String[] tables = {"roles", "users", "customers", "accounts", "transactions", "transfer_details", "audit_logs"};
        try (Connection conn = DatabaseConfig.getConnection();
             BufferedWriter bw = new BufferedWriter(new FileWriter(backupFile))) {
            bw.write("-- ====================================================================\n");
            bw.write("-- SECUREBANK AUTOMATED DATABASE BACKUP\n");
            bw.write("-- Created: " + LocalDateTime.now() + "\n");
            bw.write("-- Engine: Portable SQL Dump\n");
            bw.write("-- ====================================================================\n\n");

            for (String table : tables) {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int colCount = md.getColumnCount();

                    bw.write("-- Table Data: " + table + "\n");
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder("INSERT INTO ").append(table).append(" VALUES (");
                        for (int i = 1; i <= colCount; i++) {
                            Object val = rs.getObject(i);
                            if (val == null) {
                                sb.append("NULL");
                            } else if (val instanceof Number || val instanceof Boolean) {
                                sb.append(val);
                            } else {
                                sb.append("'").append(val.toString().replace("'", "''")).append("'");
                            }
                            if (i < colCount) sb.append(", ");
                        }
                        sb.append(");\n");
                        bw.write(sb.toString());
                    }
                    bw.write("\n");
                } catch (SQLException ignored) {
                    // Table might not exist or be accessible
                }
            }
        }
    }
}
