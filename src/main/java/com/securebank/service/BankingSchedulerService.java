package com.securebank.service;

import com.securebank.dao.SchedulerDAO;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureBank: Complete Banking Automation System
 * File: BankingSchedulerService.java
 *
 * Lightweight, in-process automated scheduler for recurring banking tasks.
 * Manages periodic transaction reconciliation, automated backups, and fraud monitoring.
 */
public class BankingSchedulerService {

    private static final Logger LOGGER = Logger.getLogger(BankingSchedulerService.class.getName());

    private final ScheduledExecutorService scheduler;
    private final ReconciliationService reconciliationService;
    private final BackupService backupService;
    private final ReportService reportService;
    private final SchedulerDAO schedulerDAO;

    private final AtomicBoolean reconciliationRunning = new AtomicBoolean(false);
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);

    private boolean isRunning = false;

    public BankingSchedulerService() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "SecureBank-Scheduler-Worker");
            t.setDaemon(true);
            return t;
        });
        this.reconciliationService = new ReconciliationService();
        this.backupService = new BackupService();
        this.reportService = new ReportService();
        this.schedulerDAO = new SchedulerDAO();
    }

    /**
     * Starts background automated recurring tasks with standard intervals.
     */
    public synchronized void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        LOGGER.info("Starting BankingSchedulerService background jobs...");

        // Scheduled Reconciliation: initial delay 2 mins, then every 30 mins
        scheduler.scheduleWithFixedDelay(this::runReconciliationTask, 2, 30, TimeUnit.MINUTES);

        // Scheduled Backup: initial delay 5 mins, then every 60 mins
        scheduler.scheduleWithFixedDelay(this::runBackupTask, 5, 60, TimeUnit.MINUTES);
    }

    /**
     * Gracefully stops all scheduled executions.
     */
    public synchronized void shutdown() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        LOGGER.info("Shutting down BankingSchedulerService...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Triggers manual or automated reconciliation job.
     */
    public String triggerReconciliationNow() {
        if (!reconciliationRunning.compareAndSet(false, true)) {
            return "ALREADY_RUNNING";
        }
        try {
            com.securebank.model.ReconciliationReport rep = reconciliationService.runReconciliation();
            String details = String.format("Audited %d accounts. %d matched, %d discrepancies.",
                rep.getTotalAccountsAudited(), rep.getMatchedAccounts(), rep.getDiscrepancyCount());
            schedulerDAO.recordJobExecution("TRANSACTION_RECONCILIATION", rep.getStatus(), details);
            return rep.getStatus();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Reconciliation task failed: " + e.getMessage(), e);
            try {
                schedulerDAO.recordJobExecution("TRANSACTION_RECONCILIATION", "FAILED", e.getMessage());
            } catch (Exception ignored) {}
            return "FAILED";
        } finally {
            reconciliationRunning.set(false);
        }
    }

    /**
     * Triggers manual or automated backup job.
     */
    public String triggerBackupNow() {
        if (!backupRunning.compareAndSet(false, true)) {
            return "ALREADY_RUNNING";
        }
        try {
            java.util.Map<String, Object> res = backupService.createBackup();
            String status = (String) res.getOrDefault("status", "FAILED");
            String details = String.format("File: %s, Size: %s bytes", res.get("fileName"), res.get("sizeBytes"));
            schedulerDAO.recordJobExecution("DATABASE_BACKUP", status, details);
            return status;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Scheduled backup task failed: " + e.getMessage(), e);
            try {
                schedulerDAO.recordJobExecution("DATABASE_BACKUP", "FAILED", e.getMessage());
            } catch (Exception ignored) {}
            return "FAILED";
        } finally {
            backupRunning.set(false);
        }
    }

    private void runReconciliationTask() {
        try {
            LOGGER.info("[SCHEDULER] Triggering scheduled ledger reconciliation...");
            triggerReconciliationNow();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Unexpected error in scheduled reconciliation: " + t.getMessage(), t);
        }
    }

    private void runBackupTask() {
        try {
            LOGGER.info("[SCHEDULER] Triggering scheduled database backup...");
            triggerBackupNow();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Unexpected error in scheduled backup: " + t.getMessage(), t);
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public ReportService getReportService() {
        return reportService;
    }
}
