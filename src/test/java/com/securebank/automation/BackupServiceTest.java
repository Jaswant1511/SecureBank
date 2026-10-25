package com.securebank.automation;

import com.securebank.service.BackupService;
import com.securebank.util.DatabaseConfig;
import java.io.File;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BackupServiceTest {

    private BackupService backupService;

    @BeforeEach
    public void setUp() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        backupService = new BackupService("target/test-backups", 3);
    }

    @Test
    public void testAutomatedBackupCreationAndVerification() {
        Map<String, Object> result = backupService.createBackup();
        assertNotNull(result, "Backup result should not be null");
        assertEquals("SUCCESS", result.get("status"));
        assertTrue((Boolean) result.get("verified"), "Backup archive must pass integrity verification");

        String path = (String) result.get("filePath");
        assertNotNull(path);
        File f = new File(path);
        assertTrue(f.exists(), "Backup file must exist on disk");
        assertTrue(f.length() > 0, "Backup file must not be empty");
    }

    @Test
    public void testBackupRetentionPolicy() {
        // Create 4 backups with retention limit of 3
        backupService.createBackup();
        backupService.createBackup();
        backupService.createBackup();
        backupService.createBackup();

        File dir = new File("target/test-backups");
        File[] files = dir.listFiles((d, name) -> name.startsWith("securebank_backup_") && name.endsWith(".sql"));
        assertNotNull(files);
        assertTrue(files.length <= 3, "Retention policy must prune archives exceeding threshold");
    }
}
