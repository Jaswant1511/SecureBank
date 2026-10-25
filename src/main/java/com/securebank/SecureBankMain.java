package com.securebank;

import com.securebank.ui.console.BankApp;
import com.securebank.ui.gui.BankGuiApp;
import java.awt.GraphicsEnvironment;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: SecureBankMain.java
 *
 * Universal Application Launcher:
 *  - Automatically launches the Modern Swing GUI on desktop displays
 *  - Seamlessly falls back to the Authenticated CLI Console on headless terminals
 *  - Accepts '--cli' or '--console' command-line flags to force terminal mode
 */
public class SecureBankMain {

    public static void main(String[] args) {
        boolean forceCli = false;
        boolean forceGui = false;
        boolean openBrowser = true;

        for (String arg : args) {
            if ("--cli".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg) || "--console".equalsIgnoreCase(arg)) {
                forceCli = true;
            } else if ("--gui".equalsIgnoreCase(arg) || "-g".equalsIgnoreCase(arg) || "--swing".equalsIgnoreCase(arg) || "--desktop".equalsIgnoreCase(arg)) {
                forceGui = true;
            } else if ("--no-browser".equalsIgnoreCase(arg)) {
                openBrowser = false;
            } else if ("--reconcile".equalsIgnoreCase(arg)) {
                runReconcileCli();
                System.exit(0);
            } else if ("--report".equalsIgnoreCase(arg)) {
                runReportCli();
                System.exit(0);
            } else if ("--backup".equalsIgnoreCase(arg)) {
                runBackupCli();
                System.exit(0);
            }
        }

        if (forceCli) {
            BankApp.main(args);
            return;
        }

        if (forceGui) {
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("[GUI NOTICE] Headless environment detected. Falling back to Console Banking Terminal...");
                BankApp.main(args);
            } else {
                try {
                    BankGuiApp.main(args);
                } catch (Throwable t) {
                    System.err.println("[GUI NOTICE] Swing GUI display not available (" + t.getMessage() + "). Running Console Banking Terminal...");
                    BankApp.main(args);
                }
            }
            return;
        }

        // DEFAULT: Enterprise Web Banking Platform (Single-Window Execution)
        int port = com.securebank.ui.web.BankWebServer.PORT;
        if (com.securebank.ui.web.BankWebServer.isServerRunning(port)) {
            System.out.println("================================================================================");
            System.out.println("   [WEB PORTAL] SECUREBANK IS ALREADY ACTIVE & HEALTHY AT http://localhost:" + port);
            System.out.println("================================================================================");
            if (openBrowser) {
                try {
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:" + port));
                    }
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        com.securebank.ui.web.BankWebServer webServer = null;
        try {
            webServer = new com.securebank.ui.web.BankWebServer(port);
            webServer.start();
            System.out.println("================================================================================");
            System.out.println("   [WEB PORTAL] SECUREBANK ENTERPRISE CORE BANKING PLATFORM ONLINE");
            System.out.println("   Access via Browser: http://localhost:" + port);
            System.out.println("================================================================================");

            if (openBrowser) {
                try {
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI("http://localhost:" + port));
                    }
                } catch (Throwable ignored) {
                }
            }

            // Keep main thread alive while the web server is operating
            Thread.currentThread().join();
        } catch (Throwable t) {
            System.err.println("[FATAL] Could not start SecureBank Web Server: " + t.getMessage());
        }
    }

    private static void runReconcileCli() {
        System.out.println("================================================================================");
        System.out.println("       SECUREBANK AUTOMATED TRANSACTION RECONCILIATION AUDITOR                  ");
        System.out.println("================================================================================");
        try {
            com.securebank.util.DatabaseConfig.initializeSchema();
            com.securebank.service.ReconciliationService svc = new com.securebank.service.ReconciliationService();
            com.securebank.model.ReconciliationReport rep = svc.runReconciliation();
            System.out.printf("Total Accounts Audited : %d%n", rep.getTotalAccountsAudited());
            System.out.printf("Balanced Ledgers       : %d%n", rep.getMatchedAccounts());
            System.out.printf("Discrepancies Flagged  : %d%n", rep.getDiscrepancyCount());
            System.out.printf("Audit Outcome Status   : %s%n", rep.getStatus());
            System.out.println("Summary                : " + rep.getSummary());
            if (rep.getDiscrepancyCount() > 0) {
                System.out.println("\nDiscrepancy Breakdown:");
                for (com.securebank.model.DiscrepancyItem item : rep.getDiscrepancies()) {
                    System.out.printf(" - Account #%d (%s): Stored=₹%s, Ledger=₹%s, Diff=₹%s (%s)%n",
                        item.getAccountNumber(), item.getHolderName(), item.getStoredBalance(),
                        item.getCalculatedBalance(), item.getDifference(), item.getNotes());
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Reconciliation failed: " + e.getMessage());
        }
        System.out.println("================================================================================");
    }

    private static void runReportCli() {
        System.out.println("================================================================================");
        System.out.println("          SECUREBANK AUTOMATED FINANCIAL REPORT GENERATOR                       ");
        System.out.println("================================================================================");
        try {
            com.securebank.util.DatabaseConfig.initializeSchema();
            com.securebank.service.ReportService repSvc = new com.securebank.service.ReportService();
            java.util.Map<String, Object> summary = repSvc.generateSummaryReport("ALL");
            System.out.println("Report Period          : " + summary.get("period"));
            System.out.println("Total Deposits         : ₹" + summary.get("depositTotal") + " (" + summary.get("depositCount") + " txns)");
            System.out.println("Total Withdrawals      : ₹" + summary.get("withdrawTotal") + " (" + summary.get("withdrawCount") + " txns)");
            System.out.println("Total Transfers        : ₹" + summary.get("transferTotal") + " (" + summary.get("transferCount") + " txns)");
            System.out.println("Total Financial Volume : ₹" + summary.get("netVolume"));
            System.out.println("Active Accounts Count  : " + summary.get("activeAccounts"));

            // Export CSV files
            new java.io.File("target/reports").mkdirs();
            String txnCsv = repSvc.exportTransactionsToCSV(null, null, null);
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/reports/transactions.csv"), txnCsv);
            String auditCsv = repSvc.exportAuditLogsToCSV(null, null);
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/reports/audit_logs.csv"), auditCsv);
            System.out.println("\n[EXPORT] Generated RFC 4180 CSV reports in target/reports/");
            System.out.println(" - target/reports/transactions.csv");
            System.out.println(" - target/reports/audit_logs.csv");
        } catch (Exception e) {
            System.err.println("[ERROR] Report generation failed: " + e.getMessage());
        }
        System.out.println("================================================================================");
    }

    private static void runBackupCli() {
        System.out.println("================================================================================");
        System.out.println("        SECUREBANK AUTOMATED DATABASE BACKUP & RECOVERY ENGINE                  ");
        System.out.println("================================================================================");
        try {
            com.securebank.util.DatabaseConfig.initializeSchema();
            com.securebank.service.BackupService backupSvc = new com.securebank.service.BackupService();
            java.util.Map<String, Object> res = backupSvc.createBackup();
            System.out.println("Backup Status          : " + res.get("status"));
            System.out.println("Archive File           : " + res.get("fileName"));
            System.out.println("Full Path              : " + res.get("filePath"));
            System.out.println("Size in Bytes          : " + res.get("sizeBytes") + " bytes");
            System.out.println("Integrity Verified     : " + res.get("verified"));
            System.out.println("Duration               : " + res.get("durationMs") + " ms");
        } catch (Exception e) {
            System.err.println("[ERROR] Backup failed: " + e.getMessage());
        }
        System.out.println("================================================================================");
    }
}
