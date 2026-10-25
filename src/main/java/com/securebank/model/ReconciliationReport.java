package com.securebank.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SecureBank: Complete Banking Automation System
 * File: ReconciliationReport.java
 *
 * Encapsulates the complete outcome of an automated ledger reconciliation run.
 */
public class ReconciliationReport {

    private long reconId;
    private final LocalDateTime runTimestamp;
    private final int totalAccountsAudited;
    private final int matchedAccounts;
    private final int discrepancyCount;
    private final List<DiscrepancyItem> discrepancies;
    private final String status; // BALANCED, DISCREPANCIES_FOUND
    private final String summary;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ReconciliationReport(
        long reconId,
        LocalDateTime runTimestamp,
        int totalAccountsAudited,
        int matchedAccounts,
        int discrepancyCount,
        List<DiscrepancyItem> discrepancies,
        String status,
        String summary
    ) {
        this.reconId = reconId;
        this.runTimestamp = runTimestamp != null ? runTimestamp : LocalDateTime.now();
        this.totalAccountsAudited = totalAccountsAudited;
        this.matchedAccounts = matchedAccounts;
        this.discrepancyCount = discrepancyCount;
        this.discrepancies = discrepancies != null ? new ArrayList<>(discrepancies) : new ArrayList<>();
        this.status = status;
        this.summary = summary;
    }

    public long getReconId() {
        return reconId;
    }

    public void setReconId(long reconId) {
        this.reconId = reconId;
    }

    public LocalDateTime getRunTimestamp() {
        return runTimestamp;
    }

    public int getTotalAccountsAudited() {
        return totalAccountsAudited;
    }

    public int getMatchedAccounts() {
        return matchedAccounts;
    }

    public int getDiscrepancyCount() {
        return discrepancyCount;
    }

    public List<DiscrepancyItem> getDiscrepancies() {
        return Collections.unmodifiableList(discrepancies);
    }

    public String getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getFormattedTimestamp() {
        return runTimestamp.format(TS_FMT);
    }

    public boolean isBalanced() {
        return "BALANCED".equalsIgnoreCase(status) && discrepancyCount == 0;
    }
}
