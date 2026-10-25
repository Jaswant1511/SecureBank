package com.securebank.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: DatabaseConfig.java
 *
 * Centralized JDBC Configuration and Connection Management.
 * Features:
 *  - Externalized credentials via System Environment Variables
 *  - Seamless support for MySQL 8.0+ and embedded H2 (MySQL Mode) for offline/testing
 *  - Automated schema migration runner
 */
public final class DatabaseConfig {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;
    private static String dbDriver;

    private static boolean isH2Fallback = false;
    private static Boolean productionModeOverride = null;

    static {
        loadConfiguration();
    }

    private DatabaseConfig() {
        // Utility class
    }

    /**
     * Determines whether the application is running in production mode.
     * Checks SECUREBANK_ENV, APP_ENV, or system properties.
     */
    public static boolean isProduction() {
        if (productionModeOverride != null) {
            return productionModeOverride;
        }
        String env = System.getenv("SECUREBANK_ENV");
        if (env == null || env.trim().isEmpty()) {
            env = System.getenv("APP_ENV");
        }
        if (env == null || env.trim().isEmpty()) {
            env = System.getProperty("securebank.env");
        }
        if (env == null || env.trim().isEmpty()) {
            env = System.getProperty("app.env");
        }
        return env != null && ("production".equalsIgnoreCase(env.trim()) || "prod".equalsIgnoreCase(env.trim()));
    }

    /**
     * Programmatically override production mode (for testing purposes).
     */
    public static synchronized void setProductionModeOverride(Boolean override) {
        productionModeOverride = override;
    }

    public static synchronized void loadConfiguration() {
        String envUrl = System.getenv("SECUREBANK_DB_URL");
        String envUser = System.getenv("SECUREBANK_DB_USER");
        String envPass = System.getenv("SECUREBANK_DB_PASS");

        if (envUrl != null && !envUrl.trim().isEmpty()) {
            dbUrl = envUrl.trim();
            dbUser = envUser != null ? envUser.trim() : "root";
            dbPassword = envPass != null ? envPass : "";
        } else {
            // Default connection string with 2-second connection timeout for fast offline detection
            dbUrl = "jdbc:mysql://localhost:3306/securebank_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata&connectTimeout=2000&socketTimeout=3000";
            dbUser = (envUser != null && !envUser.isEmpty()) ? envUser : "root";
            dbPassword = (envPass != null) ? envPass : "";
        }

        if (dbUrl.contains("h2:")) {
            dbDriver = "org.h2.Driver";
            isH2Fallback = true;
        } else {
            dbDriver = "com.mysql.cj.jdbc.Driver";
            isH2Fallback = false;
        }

        try {
            Class.forName(dbDriver);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, "Primary JDBC Driver not found on classpath: " + dbDriver, e);
        }
    }

    /**
     * Programmatically configure database parameters (useful for unit testing).
     */
    public static synchronized void setConfig(String url, String user, String password) {
        dbUrl = Objects.requireNonNull(url);
        dbUser = user != null ? user : "";
        dbPassword = password != null ? password : "";
        if (dbUrl.contains("h2:")) {
            dbDriver = "org.h2.Driver";
            isH2Fallback = true;
        } else {
            dbDriver = "com.mysql.cj.jdbc.Driver";
            isH2Fallback = false;
        }
        try {
            Class.forName(dbDriver);
        } catch (ClassNotFoundException ignored) {
        }
    }

    /**
     * Obtains an active JDBC connection.
     * In production, connection failure throws immediately without silent fallback.
     * In development/test, explicit warning is logged upon fallback to embedded H2.
     */
    public static synchronized Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        } catch (SQLException ex) {
            if (isProduction()) {
                LOGGER.log(Level.SEVERE, "CRITICAL: Primary database connection failed in PRODUCTION mode. Automatic H2 fallback is strictly disabled.");
                throw new SQLException("Primary database connection unavailable in PRODUCTION environment. Fallback is disabled.", ex);
            }
            // If MySQL is not running and we are using default MySQL URL, fallback to in-memory H2 in dev/test
            if (!isH2Fallback && dbUrl.contains("mysql")) {
                LOGGER.warning("[DATABASE FALLBACK ACTIVE] MySQL connection unavailable (" + ex.getMessage() + "). Running in DEVELOPMENT mode fallback using embedded H2 (MySQL mode).");
                switchToEmbeddedH2();
                return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            }
            throw ex;
        }
    }

    /**
     * Switches the configuration to an embedded in-memory H2 database with MySQL syntax compatibility.
     */
    public static synchronized void switchToEmbeddedH2() {
        if (isH2Fallback) {
            return;
        }
        dbUrl = "jdbc:h2:mem:securebank_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        dbUser = "sa";
        dbPassword = "";
        dbDriver = "org.h2.Driver";
        isH2Fallback = true;
        try {
            Class.forName(dbDriver);
        } catch (ClassNotFoundException e) {
            LOGGER.severe("H2 driver missing from classpath.");
        }
        // Initialize schema in memory on H2
        initializeSchema();
        seedTestData();

        // Start embedded H2 Web Console on port 8082 for local inspection
        try {
            boolean allowOthers = "true".equalsIgnoreCase(System.getenv("SECUREBANK_H2_ALLOW_OTHERS"));
            if (allowOthers && !isProduction()) {
                org.h2.tools.Server.createWebServer("-webPort", "8082", "-webAllowOthers", "-ifNotExists").start();
                LOGGER.info("[H2 CONSOLE] H2 Database Web Console accessible at http://localhost:8082 (allowOthers=true)");
            } else {
                org.h2.tools.Server.createWebServer("-webPort", "8082", "-ifNotExists").start();
                LOGGER.info("[H2 CONSOLE] H2 Database Web Console accessible locally at http://localhost:8082");
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Returns true if currently running against H2 database.
     */
    public static boolean isH2() {
        return isH2Fallback || (dbUrl != null && dbUrl.contains("h2"));
    }

    public static String getDbUrl() {
        return dbUrl;
    }

    public static String getDbUser() {
        return dbUser;
    }

    /**
     * Reads and executes a SQL script file from the classpath or filesystem.
     */
    public static void executeSqlScript(Connection conn, String scriptContent) throws SQLException {
        StringBuilder cleanSql = new StringBuilder();
        String[] lines = scriptContent.split("\\r?\\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("--") || t.startsWith("//")) {
                continue;
            }
            cleanSql.append(line).append("\n");
        }

        String[] statements = cleanSql.toString().split(";");
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (isH2() && (trimmed.toUpperCase().startsWith("CREATE DATABASE")
                        || trimmed.toUpperCase().startsWith("USE ")
                        || trimmed.toUpperCase().startsWith("DROP DATABASE"))) {
                    continue;
                }
                try {
                    stmt.execute(trimmed);
                } catch (SQLException e) {
                    LOGGER.fine("SQL Notice: " + e.getMessage() + " on " + trimmed);
                }
            }
        }
    }

    /**
     * Initializes default schema tables without seed data if not present.
     */
    public static synchronized void initializeSchema() {
        try (Connection conn = getConnection()) {
            String schema = loadResourceScript("/schema_bootstrap.sql");
            if (schema != null && !schema.isEmpty()) {
                executeSqlScript(conn, schema);
            }
            try (Statement st = conn.createStatement()) {
                if (isH2()) {
                    st.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user ON user_sessions(user_id)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_sessions_status ON user_sessions(session_status)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_sessions_login ON user_sessions(login_time)");
                }
            } catch (SQLException ignored) {
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Schema initialization warning: " + e.getMessage());
        }
    }

    /**
     * Seeds initial test data exclusively for automated test suites.
     */
    public static synchronized void seedTestData() {
        try (Connection conn = getConnection()) {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE username = 'admin_rajesh'")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            } catch (SQLException ignored) {
            }
            String seed = loadResourceScript("/schema_test_seed.sql");
            if (seed != null && !seed.isEmpty()) {
                executeSqlScript(conn, seed);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Test seed initialization warning: " + e.getMessage());
        }
    }

    private static String loadResourceScript(String resourcePath) {
        InputStream is = DatabaseConfig.class.getResourceAsStream(resourcePath);
        if (is == null && !resourcePath.startsWith("/")) {
            is = DatabaseConfig.class.getResourceAsStream("/" + resourcePath);
        }
        if (is == null) {
            try {
                java.io.File file = new java.io.File("src/main/resources" + (resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath));
                if (file.exists()) {
                    is = new java.io.FileInputStream(file);
                } else {
                    file = new java.io.File("target/classes" + (resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath));
                    if (file.exists()) {
                        is = new java.io.FileInputStream(file);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (is == null) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
