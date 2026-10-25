package com.securebank.ui.web;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.dao.UserDAO;
import com.securebank.exception.AccountNotFoundException;
import com.securebank.exception.AuthenticationException;
import com.securebank.exception.AuthorizationException;
import com.securebank.exception.InactiveAccountException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.Account;
import com.securebank.model.AccountType;
import com.securebank.model.AuditLog;
import com.securebank.model.Customer;
import com.securebank.model.DiscrepancyItem;
import com.securebank.model.Notification;
import com.securebank.model.ReconciliationReport;
import com.securebank.model.Role;
import com.securebank.model.SuspiciousActivityAlert;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionReceipt;
import com.securebank.model.User;
import com.securebank.model.UserSession;
import java.time.LocalDate;
import com.securebank.dao.BeneficiaryDAO;
import com.securebank.dao.BudgetDAO;
import com.securebank.dao.SuspiciousAlertDAO;
import com.securebank.model.Beneficiary;
import com.securebank.model.Biller;
import com.securebank.model.BillPayment;
import com.securebank.model.Budget;
import com.securebank.model.DebitCard;
import com.securebank.model.ExternalPaymentRecord;
import com.securebank.model.FixedDeposit;
import com.securebank.model.Loan;
import com.securebank.model.SpendingSummary;
import com.securebank.service.payment.ExternalPaymentService;
import com.securebank.service.payment.PaymentResponse;
import com.securebank.service.payment.PaymentStatus;
import com.securebank.service.AccountService;
import com.securebank.service.AuthService;
import com.securebank.service.BackupService;
import com.securebank.service.BankingModulesService;
import com.securebank.service.BankingSchedulerService;
import com.securebank.service.BankingService;
import com.securebank.service.BeneficiaryService;
import com.securebank.service.FraudEngineService;
import com.securebank.service.LedgerService;
import com.securebank.service.NotificationService;
import com.securebank.service.ReconciliationService;
import com.securebank.service.ReportService;
import com.securebank.service.SessionService;
import com.securebank.service.SpendingAnalyticsService;
import com.securebank.service.SuspiciousActivityService;
import com.securebank.util.DatabaseConfig;
import com.securebank.util.PasswordUtil;
import com.securebank.util.ValidationUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: BankWebServer.java
 *
 * Embedded HTTP & REST API Server hosting the state-of-the-art
 * Neo-Bank Web Application. Powered natively by JDK com.sun.net.httpserver.
 *
 * Security Architecture:
 *  - Bearer token authentication (SessionService) on all protected endpoints.
 *  - RBAC enforcement via Role enum and AuthService.enforceRole.
 *  - Strict IDOR protection preventing customers from accessing unowned accounts.
 *  - Full security audit logging for logins, logouts, token rejections, and access violations.
 */
public class BankWebServer {

    private static final Logger LOGGER = Logger.getLogger(BankWebServer.class.getName());
    public static final int PORT = 8080;

    static {
        try {
            File logDir = new File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            FileHandler fileHandler = new FileHandler("logs/securebank.log", 5 * 1024 * 1024, 3, true);
            fileHandler.setFormatter(new SimpleFormatter());
            Logger packageLogger = Logger.getLogger("com.securebank");
            packageLogger.addHandler(fileHandler);
            packageLogger.setLevel(Level.INFO);
        } catch (Throwable ignored) {
        }
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final AuthService authService;
    private final AccountService accountService;
    private final BankingService bankingService;
    private final BankDAO bankDAO;
    private final UserDAO userDAO;
    private final CustomerDAO customerDAO;
    private final TransactionDAO transactionDAO;
    private final AuditDAO auditDAO;
    private final NotificationService notificationService;
    private final SuspiciousActivityService suspiciousActivityService;
    private final ReconciliationService reconciliationService;
    private final ReportService reportService;
    private final BackupService backupService;
    private final BankingSchedulerService schedulerService;
    private final SessionService sessionService;
    private final BeneficiaryService beneficiaryService;
    private final SpendingAnalyticsService spendingAnalyticsService;
    private final BankingModulesService bankingModulesService;
    private final LedgerService ledgerService;
    private final FraudEngineService fraudEngineService;
    private final SuspiciousAlertDAO suspiciousAlertDAO;
    private final ExternalPaymentService externalPaymentService;

    public BankWebServer() throws IOException {
        this(PORT);
    }

    public BankWebServer(int port) throws IOException {
        DatabaseConfig.initializeSchema();
        this.bankDAO = new BankDAO();
        this.userDAO = new UserDAO();
        this.customerDAO = new CustomerDAO();
        this.transactionDAO = new TransactionDAO();
        this.auditDAO = new AuditDAO();

        this.notificationService = new NotificationService();
        this.suspiciousActivityService = new SuspiciousActivityService();
        this.reconciliationService = new ReconciliationService(new com.securebank.dao.ReconciliationDAO(), this.suspiciousActivityService);
        this.reportService = new ReportService();
        this.backupService = new BackupService();
        this.schedulerService = new BankingSchedulerService();
        this.sessionService = new SessionService();

        this.authService = new AuthService(userDAO, auditDAO);
        this.accountService = new AccountService(bankDAO, customerDAO, auditDAO);
        this.bankingService = new BankingService(bankDAO, transactionDAO, customerDAO, auditDAO, notificationService, suspiciousActivityService);

        this.beneficiaryService = new BeneficiaryService(new BeneficiaryDAO(), bankDAO, customerDAO, auditDAO);
        this.spendingAnalyticsService = new SpendingAnalyticsService(bankDAO, transactionDAO, new BudgetDAO(), customerDAO, auditDAO);
        this.bankingModulesService = new BankingModulesService();
        this.ledgerService = new LedgerService();
        this.fraudEngineService = new FraudEngineService();
        this.suspiciousAlertDAO = new SuspiciousAlertDAO();
        this.externalPaymentService = new ExternalPaymentService();

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.executor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "SecureBank-Http-Worker");
            t.setDaemon(true);
            return t;
        });
        this.server.setExecutor(this.executor);
        registerRoutes();
    }

    public static boolean isServerRunning(int port) {
        try {
            java.net.URL url = new java.net.URI("http://localhost:" + port + "/api/health").toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);
            return conn.getResponseCode() == 200;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void main(String[] args) {
        boolean openBrowser = true;
        for (String arg : args) {
            if ("--no-browser".equalsIgnoreCase(arg)) {
                openBrowser = false;
            }
        }

        if (isServerRunning(PORT)) {
            System.out.println("================================================================================");
            System.out.println("   [INFO] SecureBank Web Server is already active and healthy at http://localhost:" + PORT);
            System.out.println("================================================================================");
            if (openBrowser) {
                try {
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(new URI("http://localhost:" + PORT));
                    }
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        try {
            BankWebServer webServer = new BankWebServer();
            webServer.start();
            System.out.println("================================================================================");
            System.out.println("   SECUREBANK — MODERN DIGITAL BANKING WEB PLATFORM");
            System.out.println("   Live Web Interface: http://localhost:" + PORT);
            System.out.println("================================================================================");

            if (openBrowser) {
                try {
                    if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                        java.awt.Desktop.getDesktop().browse(new URI("http://localhost:" + PORT));
                    }
                } catch (Throwable ignored) {
                }
            }

            // Keep main thread alive while the web server is operating
            Thread.currentThread().join();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start SecureBank Web Server", e);
        }
    }

    public void start() {
        server.start();
        schedulerService.start();
        LOGGER.info("SecureBank Web Server online at port " + getPort());
    }

    public void stop() {
        schedulerService.shutdown();
        server.stop(0);
        if (executor != null) {
            executor.shutdownNow();
        }
        LOGGER.info("SecureBank Web Server stopped.");
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public BankingService getBankingService() {
        return bankingService;
    }

    public FraudEngineService getFraudEngineService() {
        return fraudEngineService;
    }

    public LedgerService getLedgerService() {
        return ledgerService;
    }

    public BankingModulesService getBankingModulesService() {
        return bankingModulesService;
    }

    public int getPort() {
        return server != null ? server.getAddress().getPort() : PORT;
    }

    private void registerRoutes() {
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/health", new HealthCheckHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/logout", new LogoutHandler());
        server.createContext("/api/auth/logout", new LogoutHandler());
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/auth/change-password", new ChangePasswordHandler());
        server.createContext("/api/customer/dashboard", new CustomerDashboardHandler());
        server.createContext("/api/transactions", new TransactionsHandler());
        server.createContext("/api/banking/deposit", new DepositHandler());
        server.createContext("/api/banking/withdraw", new WithdrawHandler());
        server.createContext("/api/banking/transfer", new TransferHandler());
        server.createContext("/api/teller/accounts", new TellerAccountsHandler());
        server.createContext("/api/teller/create-account", new CreateAccountHandler());
        server.createContext("/api/admin/overview", new AdminOverviewHandler());
        server.createContext("/api/admin/unlock", new AdminUnlockHandler());

        // Automation endpoints
        server.createContext("/api/notifications", new NotificationsHandler());
        server.createContext("/api/notifications/read", new MarkNotificationReadHandler());
        server.createContext("/api/notifications/read-all", new MarkAllNotificationsReadHandler());
        server.createContext("/api/admin/reconcile", new AdminReconcileHandler());
        server.createContext("/api/admin/reconcile/history", new AdminReconcileHistoryHandler());
        server.createContext("/api/admin/suspicious-alerts", new AdminSuspiciousAlertsHandler());
        server.createContext("/api/admin/suspicious-alerts/update", new AdminUpdateAlertStatusHandler());
        server.createContext("/api/admin/reports/summary", new AdminReportsSummaryHandler());
        server.createContext("/api/admin/reports/export-transactions", new ExportTransactionsCsvHandler());
        server.createContext("/api/admin/reports/export-audit", new ExportAuditCsvHandler());
        server.createContext("/api/admin/backup/create", new AdminBackupCreateHandler());

        // First-Time Setup & Staff Management
        server.createContext("/api/auth/system-status", new SystemStatusHandler());
        server.createContext("/api/auth/setup-admin", new SetupAdminHandler());
        server.createContext("/api/admin/create-staff", new CreateStaffHandler());
        server.createContext("/api/admin/clear-all-data", new ClearAllDataHandler());

        // Beneficiary Routes
        server.createContext("/api/customer/beneficiaries", new BeneficiariesHandler());

        // Smart Spending & Budget Routes
        server.createContext("/api/customer/spending-insights", new SpendingInsightsHandler());
        server.createContext("/api/customer/budgets", new BudgetsHandler());

        // Extended Banking Modules Routes
        server.createContext("/api/banking/billers", new BillersHandler());
        server.createContext("/api/banking/pay-bill", new PayBillHandler());
        server.createContext("/api/customer/bill-payments", new CustomerBillPaymentsHandler());

        server.createContext("/api/banking/fixed-deposit/create", new CreateFixedDepositHandler());
        server.createContext("/api/customer/fixed-deposits", new CustomerFixedDepositsHandler());
        server.createContext("/api/banking/fixed-deposit/close", new CloseFixedDepositHandler());

        server.createContext("/api/banking/loan/apply", new ApplyLoanHandler());
        server.createContext("/api/customer/loans", new CustomerLoansHandler());
        server.createContext("/api/admin/loans", new AdminLoansHandler());
        server.createContext("/api/admin/loan/approve", new ReviewLoanHandler());
        server.createContext("/api/banking/loan/repay", new RepayLoanHandler());

        server.createContext("/api/customer/cards", new CustomerCardsHandler());
        server.createContext("/api/banking/card/toggle-block", new CardToggleBlockHandler());
        server.createContext("/api/banking/card/update-limit", new CardUpdateLimitHandler());
        server.createContext("/api/banking/card/toggle-contactless", new CardToggleContactlessHandler());

        // Innovation 1 & 2: Double-Entry Ledger & Fraud Alert Actions
        server.createContext("/api/admin/ledger/audit", new AdminLedgerAuditHandler());
        server.createContext("/api/admin/fraud-alerts/resolve", new AdminResolveFraudAlertHandler());

        // Phase 11: Financial Integration & External Payment Gateway Handlers
        server.createContext("/api/payments/initiate", new ExternalPaymentInitiateHandler());
        server.createContext("/api/payments/inquire", new ExternalPaymentInquireHandler());
        server.createContext("/api/payments/history", new ExternalPaymentHistoryHandler());
        server.createContext("/api/webhooks/payments", new PaymentWebhookCallbackHandler());
        server.createContext("/api/admin/payments/reconcile", new AdminPaymentReconcileHandler());

        // Session Monitoring Routes
        server.createContext("/api/admin/sessions/stats", new AdminSessionStatsHandler());
        server.createContext("/api/admin/sessions/active", new AdminActiveSessionsHandler());
        server.createContext("/api/admin/sessions/history", new AdminSessionHistoryHandler());
        server.createContext("/api/admin/sessions/terminate", new AdminTerminateSessionHandler());
        server.createContext("/api/admin/sessions/recent-activity", new AdminSessionRecentActivityHandler());
        server.createContext("/api/user/recent-sessions", new UserRecentSessionsHandler());
    }

    // ==========================================
    // AUTHENTICATION & ACCESS CONTROL HELPERS
    // ==========================================

    private void handleOptions(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Idempotency-Key");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("X-XSS-Protection", "1; mode=block");
        exchange.sendResponseHeaders(204, -1);
    }

    private String getClientIp(HttpExchange exchange) {
        if (exchange != null && exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        return "127.0.0.1";
    }

    private void recordAuditQuietly(String actor, Role role, String action, String entityType, String entityId, String details, String status, String ip) {
        try {
            AuditLog log = new AuditLog(0, actor, role, action, entityType, entityId, details, status, ip, null);
            auditDAO.recordAuditLog(log);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to record audit log: " + action, e);
        }
    }

    private SessionService.SessionRecord authenticate(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String ip = getClientIp(exchange);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            recordAuditQuietly("ANONYMOUS", null, "TOKEN_REJECTION", "AUTH", null,
                    "Missing or invalid Authorization header", "REJECTED", ip);
            sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Missing or invalid Authorization header. Please log in.\"}");
            return null;
        }

        String token = authHeader.substring(7).trim();
        SessionService.SessionRecord session = sessionService.getSession(token);
        if (session == null) {
            recordAuditQuietly("ANONYMOUS", null, "TOKEN_REJECTION", "AUTH", null,
                    "Expired, terminated, or non-existent session token", "REJECTED", ip);
            sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Session expired or invalid. Please log in again.\"}");
            return null;
        }
        return session;
    }

    private SessionService.SessionRecord authenticateAndAuthorize(HttpExchange exchange, Role requiredRole) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return null;
        }
        SessionService.SessionRecord session = authenticate(exchange);
        if (session == null) {
            return null;
        }
        String ip = getClientIp(exchange);
        try {
            AuthService.enforceRole(session.getUser(), requiredRole);
            return session;
        } catch (AuthorizationException ae) {
            recordAuditQuietly(session.getUser().getUsername(), session.getRole(), "RBAC_REJECTION", "AUTH",
                    String.valueOf(session.getUser().getUserId()),
                    "Access denied: required role " + requiredRole + " but session held " + session.getRole(), "REJECTED", ip);
            sendResponse(exchange, 403, "{\"success\":false,\"message\":\"" + escapeJson(ae.getMessage()) + "\"}");
            return null;
        }
    }

    private boolean verifyCustomerAccountOwnership(HttpExchange exchange, SessionService.SessionRecord session, int accountNumber) throws IOException {
        if (session.getRole() == Role.CUSTOMER) {
            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                if (cust == null) {
                    sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Customer profile not found.\"}");
                    return false;
                }
                Account acc = bankDAO.getAccountByNumber(accountNumber);
                if (acc == null || acc.getCustomerId() != cust.getCustomerId()) {
                    String ip = getClientIp(exchange);
                    recordAuditQuietly(session.getUser().getUsername(), session.getRole(), "IDOR_ATTEMPT", "ACCOUNT",
                            String.valueOf(accountNumber), "Unauthorized attempt to access account #" + accountNumber, "REJECTED", ip);
                    suspiciousActivityService.checkUnauthorizedAccessAttempt(session.getUser().getUserId(),
                            session.getUser().getUsername(), accountNumber);
                    sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Access denied: You can only access your own accounts.\"}");
                    return false;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Account verification failure", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal error occurred during account verification.\"}");
                return false;
            }
        }
        return true;
    }

    // ==========================================
    // HTTP ENDPOINT HANDLERS
    // ==========================================

    private class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            boolean dbConnected = false;
            try (Connection conn = DatabaseConfig.getConnection()) {
                dbConnected = (conn != null && conn.isValid(2));
            } catch (Exception ignored) {
            }
            int activeSessions = sessionService.getActiveSessionCount();
            String json = String.format(
                "{\"status\":\"UP\",\"service\":\"SecureBank Web Engine\",\"database\":\"%s\",\"activeSessions\":%d,\"port\":%d,\"timestamp\":\"%s\"}",
                dbConnected ? "CONNECTED" : "DISCONNECTED",
                activeSessions,
                getPort(),
                LocalDateTime.now().toString()
            );
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            sendResponse(exchange, dbConnected ? 200 : 503, json);
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String identifier = body.getOrDefault("username", body.getOrDefault("identifier", "")).trim();
            String password = body.getOrDefault("password", "").trim();

            if (identifier.isEmpty() || password.isEmpty()) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Username/email and password are required\"}");
                return;
            }

            String ip = getClientIp(exchange);
            try {
                User user = authService.login(identifier, password, ip);
                String token = sessionService.createSession(user, ip);
                recordAuditQuietly(user.getUsername(), user.getRole(), "USER_LOGIN", "AUTH",
                        String.valueOf(user.getUserId()), "Web session authenticated; Bearer token issued", "SUCCESS", ip);

                Account acc = null;
                Customer cust = null;
                if (user.getRole() == Role.CUSTOMER) {
                    cust = customerDAO.getCustomerByUserId(user.getUserId());
                    if (cust != null) {
                        List<Account> accounts = bankDAO.getAccountsByCustomerId(cust.getCustomerId());
                        if (!accounts.isEmpty()) {
                            acc = accounts.get(0);
                        }
                    }
                }

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"token\":\"").append(token).append("\",\"user\":{");
                json.append("\"userId\":").append(user.getUserId()).append(",");
                json.append("\"username\":\"").append(escapeJson(user.getUsername())).append("\",");
                json.append("\"role\":\"").append(user.getRole()).append("\"");
                json.append("}");

                if (cust != null) {
                    json.append(",\"customer\":{");
                    json.append("\"customerId\":").append(cust.getCustomerId()).append(",");
                    json.append("\"fullName\":\"").append(escapeJson(cust.getFullName())).append("\",");
                    json.append("\"email\":\"").append(escapeJson(cust.getEmail())).append("\"");
                    json.append("}");
                }

                if (acc != null) {
                    json.append(",\"account\":").append(accountToJson(acc));
                }
                json.append("}");

                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                recordAuditQuietly(identifier, null, "LOGIN_FAILED", "AUTH", null,
                        "Failed login attempt: " + e.getMessage(), "FAILURE", ip);
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String fullName = body.getOrDefault("fullName", "").trim();
            String email = body.getOrDefault("email", "").trim();
            String username = body.getOrDefault("username", "").trim();
            String phone = body.getOrDefault("phone", "").trim();
            String password = body.getOrDefault("password", "").trim();
            String confirmPassword = body.getOrDefault("confirmPassword", "").trim();
            if (confirmPassword.isEmpty() && !password.isEmpty()) {
                confirmPassword = password;
            }
            String pin = body.getOrDefault("pin", "1234").trim();
            String accountType = body.getOrDefault("accountType", "SAVINGS").trim();
            String initialDepositStr = body.getOrDefault("initialDeposit", "1000.00").trim();
            BigDecimal initialDeposit = new BigDecimal("1000.00");
            try {
                if (!initialDepositStr.isEmpty()) {
                    initialDeposit = new BigDecimal(initialDepositStr);
                }
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid initial deposit monetary format.\"}");
                return;
            }

            String ip = getClientIp(exchange);
            try {
                AuthService.RegistrationResult result = authService.registerCustomer(
                        fullName, email, username, phone, password, confirmPassword, pin, accountType, initialDeposit, ip
                );
                String token = sessionService.createSession(result.getUser());
                recordAuditQuietly(result.getUser().getUsername(), Role.CUSTOMER, "CUSTOMER_REGISTERED", "AUTH",
                        String.valueOf(result.getUser().getUserId()), "Self-service registration completed", "SUCCESS", ip);

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"message\":\"Customer account created successfully!\",");
                json.append("\"token\":\"").append(token).append("\",");
                json.append("\"user\":{\"userId\":").append(result.getUser().getUserId()).append(",");
                json.append("\"username\":\"").append(escapeJson(result.getUser().getUsername())).append("\",");
                json.append("\"role\":\"CUSTOMER\"},");
                json.append("\"customer\":{\"customerId\":").append(result.getCustomer().getCustomerId()).append(",");
                json.append("\"fullName\":\"").append(escapeJson(result.getCustomer().getFullName())).append("\",");
                json.append("\"email\":\"").append(escapeJson(result.getCustomer().getEmail())).append("\"},");
                json.append("\"accountNumber\":").append(result.getAccount().getAccountNumber()).append(",");
                json.append("\"account\":").append(accountToJson(result.getAccount()));
                json.append("}");

                sendResponse(exchange, 201, json.toString());
            } catch (IllegalArgumentException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Customer registration failed", ex);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Registration processing error: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class ChangePasswordHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) {
                return;
            }
            Map<String, String> body = parseJsonBody(exchange);
            String oldPassword = body.getOrDefault("oldPassword", "").trim();
            String newPassword = body.getOrDefault("newPassword", "").trim();
            String confirmPassword = body.getOrDefault("confirmPassword", "").trim();
            String ip = getClientIp(exchange);

            try {
                authService.changePassword(session.getUser().getUserId(), oldPassword, newPassword, confirmPassword, ip);
                // Rotate session token
                sessionService.invalidateSession(session.getToken(), "PASSWORD_CHANGE");
                String newToken = sessionService.createSession(session.getUser(), ip);
                sendResponse(exchange, 200, "{\"success\":true,\"token\":\"" + newToken + "\",\"message\":\"Password updated successfully! Please note your new credentials.\"}");
            } catch (IllegalArgumentException | AuthenticationException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Change password error", ex);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Password update failure: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) {
                return;
            }
            sessionService.invalidateSession(session.getToken(), "USER_LOGOUT");
            String ip = getClientIp(exchange);
            recordAuditQuietly(session.getUser().getUsername(), session.getRole(), "USER_LOGOUT", "AUTH",
                    String.valueOf(session.getUser().getUserId()), "Session invalidated successfully", "SUCCESS", ip);
            sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Logged out successfully\"}");
        }
    }

    private class CustomerDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            int userId;
            if (session.getRole() == Role.CUSTOMER) {
                userId = session.getUser().getUserId();
            } else {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                userId = parseInt(params.get("userId"), session.getUser().getUserId());
            }

            try {
                Customer cust = customerDAO.getCustomerByUserId(userId);
                if (cust == null) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Customer profile not found\"}");
                    return;
                }
                List<Account> accounts = bankDAO.getAccountsByCustomerId(cust.getCustomerId());
                if (accounts.isEmpty()) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"No active accounts found\"}");
                    return;
                }
                Account primary = accounts.get(0);
                List<Transaction> txns = transactionDAO.getRecentTransactions(primary.getAccountNumber(), 15);

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"account\":").append(accountToJson(primary));
                json.append(",\"accounts\":[");
                for (int i = 0; i < accounts.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(accountToJson(accounts.get(i)));
                }
                json.append("]");
                json.append(",\"transactions\":[");
                for (int i = 0; i < txns.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(transactionToJson(txns.get(i)));
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error generating customer dashboard", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while loading dashboard.\"}");
            }
        }
    }

    private class TransactionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            int accNum = parseInt(params.get("accountNumber"), 0);

            if (!verifyCustomerAccountOwnership(exchange, session, accNum)) {
                return;
            }

            try {
                List<Transaction> list = transactionDAO.getRecentTransactions(accNum, 30);
                StringBuilder json = new StringBuilder("{\"success\":true,\"transactions\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(transactionToJson(list.get(i)));
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading transactions", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while retrieving transactions.\"}");
            }
        }
    }

    private class DepositHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;

            if (session.getRole() == Role.ADMIN) {
                recordAuditQuietly(session.getUser().getUsername(), Role.ADMIN, "RBAC_REJECTION", "FINANCIAL",
                        null, "Administrators are not permitted to initiate customer financial operations", "REJECTED", getClientIp(exchange));
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Access Denied: Administrators are not permitted to initiate customer financial operations.\"}");
                return;
            }

            Map<String, String> body;
            try {
                body = parseJsonBody(exchange);
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid JSON request: " + escapeJson(e.getMessage()) + "\"}");
                return;
            }

            int accNum = parseInt(body.get("accountNumber"), 0);
            if (accNum <= 0) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Valid positive account number is required.\"}");
                return;
            }

            if (!verifyCustomerAccountOwnership(exchange, session, accNum)) {
                return;
            }

            BigDecimal amount;
            try {
                amount = parseBigDecimal(body.get("amount"));
                ValidationUtil.validateMonetaryAmount(amount);
            } catch (IllegalArgumentException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
                return;
            }

            String remarks = body.getOrDefault("remarks", "");

            try {
                TransactionReceipt receipt = bankingService.deposit(accNum, amount, remarks, session.getUser());
                Account updated = bankDAO.getAccountByNumber(accNum);

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"message\":\"Deposit of ₹").append(amount).append(" credited successfully!\",");
                json.append("\"updatedAccount\":").append(accountToJson(updated)).append(",");
                json.append("\"receipt\":").append(receiptToJson(receipt)).append("}");
                sendResponse(exchange, 200, json.toString());
            } catch (IllegalArgumentException | InactiveAccountException | AccountNotFoundException | AuthorizationException e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Internal error in DepositHandler", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while processing deposit.\"}");
            }
        }
    }

    private class WithdrawHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;

            if (session.getRole() == Role.ADMIN) {
                recordAuditQuietly(session.getUser().getUsername(), Role.ADMIN, "RBAC_REJECTION", "FINANCIAL",
                        null, "Administrators are not permitted to initiate customer financial operations", "REJECTED", getClientIp(exchange));
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Access Denied: Administrators are not permitted to initiate customer financial operations.\"}");
                return;
            }

            Map<String, String> body;
            try {
                body = parseJsonBody(exchange);
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid JSON request: " + escapeJson(e.getMessage()) + "\"}");
                return;
            }

            int accNum = parseInt(body.get("accountNumber"), 0);
            if (accNum <= 0) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Valid positive account number is required.\"}");
                return;
            }

            if (!verifyCustomerAccountOwnership(exchange, session, accNum)) {
                return;
            }

            BigDecimal amount;
            try {
                amount = parseBigDecimal(body.get("amount"));
                ValidationUtil.validateMonetaryAmount(amount);
            } catch (IllegalArgumentException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
                return;
            }

            String pin = body.getOrDefault("pin", "");
            String remarks = body.getOrDefault("remarks", "");

            try {
                TransactionReceipt receipt = bankingService.withdraw(accNum, pin, amount, remarks, session.getUser());
                Account updated = bankDAO.getAccountByNumber(accNum);

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"message\":\"Withdrawal of ₹").append(amount).append(" debited successfully!\",");
                json.append("\"updatedAccount\":").append(accountToJson(updated)).append(",");
                json.append("\"receipt\":").append(receiptToJson(receipt)).append("}");
                sendResponse(exchange, 200, json.toString());
            } catch (IllegalArgumentException | InsufficientFundsException | InactiveAccountException | AccountNotFoundException | AuthorizationException e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Internal error in WithdrawHandler", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while processing withdrawal.\"}");
            }
        }
    }

    private class TransferHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;

            if (session.getRole() == Role.ADMIN) {
                recordAuditQuietly(session.getUser().getUsername(), Role.ADMIN, "RBAC_REJECTION", "FINANCIAL",
                        null, "Administrators are not permitted to initiate customer financial operations", "REJECTED", getClientIp(exchange));
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Access Denied: Administrators are not permitted to initiate customer financial operations.\"}");
                return;
            }

            Map<String, String> body;
            try {
                body = parseJsonBody(exchange);
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid JSON request: " + escapeJson(e.getMessage()) + "\"}");
                return;
            }

            int fromAcc = parseInt(body.get("fromAccount"), 0);
            if (fromAcc <= 0) {
                fromAcc = parseInt(body.get("accountNumber"), 0);
            }
            int toAcc = parseInt(body.get("toAccount"), 0);
            if (toAcc <= 0) {
                toAcc = parseInt(body.get("targetAccount"), 0);
            }

            if (fromAcc <= 0 || toAcc <= 0) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Valid positive sender and receiver account numbers are required.\"}");
                return;
            }
            if (fromAcc == toAcc) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Source and destination accounts must be distinct.\"}");
                return;
            }

            if (!verifyCustomerAccountOwnership(exchange, session, fromAcc)) {
                return;
            }

            BigDecimal amount;
            try {
                amount = parseBigDecimal(body.get("amount"));
                ValidationUtil.validateMonetaryAmount(amount);
            } catch (IllegalArgumentException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
                return;
            }

            String pin = body.getOrDefault("pin", "");
            String remarks = body.getOrDefault("remarks", "");
            String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
                idempotencyKey = body.get("idempotencyKey");
            }

            try {
                TransactionReceipt receipt = bankingService.transfer(fromAcc, pin, toAcc, amount, remarks, session.getUser(), idempotencyKey);
                Account updated = bankDAO.getAccountByNumber(fromAcc);

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"message\":\"Transfer of ₹").append(amount).append(" to #").append(toAcc).append(" completed!\",");
                json.append("\"updatedAccount\":").append(accountToJson(updated)).append(",");
                json.append("\"receipt\":").append(receiptToJson(receipt)).append("}");
                sendResponse(exchange, 200, json.toString());
            } catch (IllegalArgumentException | InsufficientFundsException | InactiveAccountException | AccountNotFoundException | AuthorizationException e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Internal error in TransferHandler", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while processing transfer.\"}");
            }
        }
    }

    private class TellerAccountsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.TELLER);
            if (session == null) return;

            try {
                List<Account> list = bankDAO.getAllAccounts();
                int totalTxns = 0;
                try (Connection conn = DatabaseConfig.getConnection();
                     Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM transactions")) {
                    if (rs.next()) {
                        totalTxns = rs.getInt(1);
                    }
                } catch (Exception ignored) {
                }
                StringBuilder json = new StringBuilder("{\"success\":true,\"totalTransactions\":" + totalTxns + ",\"accounts\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(accountToJson(list.get(i)));
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading teller accounts register", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while retrieving accounts.\"}");
            }
        }
    }

    private class CreateAccountHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.TELLER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            String fullName = body.getOrDefault("fullName", "");
            String pin = body.getOrDefault("pin", "");
            String typeStr = body.getOrDefault("accountType", "SAVINGS");
            BigDecimal initialDeposit = parseBigDecimal(body.get("initialDeposit"));
            String email = body.getOrDefault("email", "");
            String phone = body.getOrDefault("phone", "");

            try {
                AccountType type = AccountType.fromString(typeStr);
                User teller = session.getUser();

                String username = body.getOrDefault("username", "").trim();
                if (username.isEmpty()) {
                    username = "cust_" + (System.currentTimeMillis() % 1000000);
                }
                String password = body.getOrDefault("password", "").trim();
                if (password.isEmpty()) {
                    password = "Password@123";
                }
                String passwordHash = com.securebank.util.PasswordUtil.hashPassword(password);
                User newUser = new User(0, username, passwordHash, Role.CUSTOMER, 0, false, true);
                int newUserId = userDAO.createUser(newUser, null);

                Customer customer = new Customer();
                customer.setUserId(newUserId);
                customer.setFullName(fullName);
                customer.setEmail(email);
                customer.setPhone(phone);
                customer.setAddress("Branch Registered");
                int custId = customerDAO.createCustomer(customer, null);

                Account created = accountService.createAccount(custId, fullName, type, initialDeposit, pin, teller);
                sendResponse(exchange, 200, "{\"success\":true,\"accountNumber\":" + created.getAccountNumber() + ",\"username\":\"" + escapeJson(username) + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminOverviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                List<User> users = userDAO.getAllUsers();
                List<AuditLog> audits = auditDAO.getRecentAuditLogs(30);

                StringBuilder json = new StringBuilder("{\"success\":true,\"users\":[");
                for (int i = 0; i < users.size(); i++) {
                    if (i > 0) json.append(",");
                    User u = users.get(i);
                    json.append("{\"userId\":").append(u.getUserId()).append(",");
                    json.append("\"username\":\"").append(escapeJson(u.getUsername())).append("\",");
                    json.append("\"role\":\"").append(u.getRole()).append("\",");
                    json.append("\"failedLogins\":").append(u.getFailedLogins()).append(",");
                    json.append("\"isLocked\":").append(u.isLocked()).append(",");
                    json.append("\"active\":").append(u.isActive()).append("}");
                }
                json.append("],\"auditLogs\":[");
                for (int i = 0; i < audits.size(); i++) {
                    if (i > 0) json.append(",");
                    AuditLog a = audits.get(i);
                    json.append("{\"logId\":").append(a.getLogId()).append(",");
                    json.append("\"actorUsername\":\"").append(escapeJson(a.getActorUsername())).append("\",");
                    json.append("\"role\":\"").append(a.getRole() != null ? a.getRole().name() : "").append("\",");
                    json.append("\"action\":\"").append(escapeJson(a.getAction())).append("\",");
                    json.append("\"status\":\"").append(escapeJson(a.getStatus())).append("\",");
                    json.append("\"details\":\"").append(escapeJson(a.getDetails())).append("\",");
                    json.append("\"ipAddress\":\"").append(escapeJson(a.getIpAddress())).append("\",");
                    json.append("\"timestamp\":\"").append(a.getFormattedTimestamp()).append("\"}");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading admin overview", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while loading admin overview.\"}");
            }
        }
    }

    private class AdminUnlockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int userId = parseInt(body.get("userId"), 0);

            try {
                authService.unlockUser(userId, session.getUser());
                sendResponse(exchange, 200, "{\"success\":true}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class NotificationsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            int userId = session.getUser().getUserId();
            boolean unreadOnly = "true".equalsIgnoreCase(params.get("unreadOnly"));

            List<Notification> list = notificationService.getUserNotifications(userId, unreadOnly);
            int unreadCount = notificationService.getUnreadCount(userId);

            StringBuilder json = new StringBuilder("{\"success\":true,\"unreadCount\":").append(unreadCount).append(",\"notifications\":[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) json.append(",");
                Notification n = list.get(i);
                json.append("{\"notificationId\":").append(n.getNotificationId()).append(",");
                json.append("\"userId\":").append(n.getUserId()).append(",");
                json.append("\"title\":\"").append(escapeJson(n.getTitle())).append("\",");
                json.append("\"message\":\"").append(escapeJson(n.getMessage())).append("\",");
                json.append("\"type\":\"").append(escapeJson(n.getType())).append("\",");
                json.append("\"isRead\":").append(n.isRead()).append(",");
                json.append("\"timestamp\":\"").append(n.getFormattedTimestamp()).append("\"}");
            }
            json.append("]}");
            sendResponse(exchange, 200, json.toString());
        }
    }

    private class MarkNotificationReadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            long notifId = parseLong(body.get("notificationId"), 0);
            int userId = session.getUser().getUserId();
            boolean ok = notificationService.markAsRead(notifId, userId);
            sendResponse(exchange, 200, "{\"success\":" + ok + "}");
        }
    }

    private class MarkAllNotificationsReadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            int userId = session.getUser().getUserId();
            boolean ok = notificationService.markAllAsRead(userId);
            sendResponse(exchange, 200, "{\"success\":" + ok + "}");
        }
    }

    private class AdminReconcileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                ReconciliationReport rep = reconciliationService.runReconciliation();
                StringBuilder json = new StringBuilder("{\"success\":true,\"report\":{");
                json.append("\"reconId\":").append(rep.getReconId()).append(",");
                json.append("\"runTimestamp\":\"").append(rep.getFormattedTimestamp()).append("\",");
                json.append("\"totalAccounts\":").append(rep.getTotalAccountsAudited()).append(",");
                json.append("\"matchedAccounts\":").append(rep.getMatchedAccounts()).append(",");
                json.append("\"discrepancies\":").append(rep.getDiscrepancyCount()).append(",");
                json.append("\"status\":\"").append(rep.getStatus()).append("\",");
                json.append("\"summary\":\"").append(escapeJson(rep.getSummary())).append("\",");
                json.append("\"discrepancyList\":[");
                List<DiscrepancyItem> items = rep.getDiscrepancies();
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) json.append(",");
                    DiscrepancyItem d = items.get(i);
                    json.append("{\"accountNumber\":").append(d.getAccountNumber()).append(",");
                    json.append("\"accountHolder\":\"").append(escapeJson(d.getHolderName())).append("\",");
                    json.append("\"storedBalance\":").append(d.getStoredBalance()).append(",");
                    json.append("\"calculatedBalance\":").append(d.getCalculatedBalance()).append(",");
                    json.append("\"difference\":").append(d.getDifference()).append(",");
                    json.append("\"reason\":\"").append(escapeJson(d.getNotes())).append("\"}");
                }
                json.append("]}}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing reconciliation", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred during ledger reconciliation.\"}");
            }
        }
    }

    private class AdminReconcileHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                List<ReconciliationReport> list = reconciliationService.getRecentReports(10);
                StringBuilder json = new StringBuilder("{\"success\":true,\"reports\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    ReconciliationReport r = list.get(i);
                    json.append("{\"reconId\":").append(r.getReconId()).append(",");
                    json.append("\"runTimestamp\":\"").append(r.getFormattedTimestamp()).append("\",");
                    json.append("\"totalAccounts\":").append(r.getTotalAccountsAudited()).append(",");
                    json.append("\"matchedAccounts\":").append(r.getMatchedAccounts()).append(",");
                    json.append("\"discrepancies\":").append(r.getDiscrepancyCount()).append(",");
                    json.append("\"status\":\"").append(r.getStatus()).append("\",");
                    json.append("\"summary\":\"").append(escapeJson(r.getSummary())).append("\"}");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error retrieving reconciliation history", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while retrieving reconciliation history.\"}");
            }
        }
    }

    private class AdminSuspiciousAlertsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                List<SuspiciousActivityAlert> list = suspiciousActivityService.getAllAlerts(50);
                StringBuilder json = new StringBuilder("{\"success\":true,\"alerts\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    SuspiciousActivityAlert a = list.get(i);
                    json.append("{\"alertId\":").append(a.getAlertId()).append(",");
                    json.append("\"timestamp\":\"").append(a.getFormattedTimestamp()).append("\",");
                    json.append("\"userId\":").append(a.getUserId() != null ? a.getUserId() : "null").append(",");
                    json.append("\"accountNumber\":").append(a.getAccountNumber() != null ? a.getAccountNumber() : "null").append(",");
                    json.append("\"ruleTriggered\":\"").append(escapeJson(a.getRuleTriggered())).append("\",");
                    json.append("\"severity\":\"").append(escapeJson(a.getSeverity())).append("\",");
                    json.append("\"riskScore\":").append(a.getRiskScore()).append(",");
                    json.append("\"reviewDecision\":\"").append(escapeJson(a.getReviewDecision())).append("\",");
                    json.append("\"reviewedBy\":\"").append(escapeJson(a.getReviewerUsername())).append("\",");
                    json.append("\"evidence\":\"").append(escapeJson(a.getEvidence())).append("\",");
                    json.append("\"status\":\"").append(escapeJson(a.getStatus())).append("\"}");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error retrieving suspicious alerts", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while retrieving alerts.\"}");
            }
        }
    }

    private class AdminUpdateAlertStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            long alertId = parseLong(body.get("alertId"), 0);
            String status = body.getOrDefault("status", "RESOLVED");
            boolean ok = suspiciousActivityService.updateAlertStatus(alertId, status);
            sendResponse(exchange, 200, "{\"success\":" + ok + "}");
        }
    }

    private class AdminReportsSummaryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.TELLER);
            if (session == null) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            String period = params.getOrDefault("period", "ALL");
            Map<String, Object> summary = reportService.generateSummaryReport(period);
            StringBuilder json = new StringBuilder("{\"success\":true,\"report\":{");
            int count = 0;
            for (Map.Entry<String, Object> entry : summary.entrySet()) {
                if (count++ > 0) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                    json.append(entry.getValue());
                } else {
                    json.append("\"").append(escapeJson(String.valueOf(entry.getValue()))).append("\"");
                }
            }
            json.append("}}");
            sendResponse(exchange, 200, json.toString());
        }
    }

    private class ExportTransactionsCsvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
            Integer acc = params.containsKey("accountNumber") ? parseInt(params.get("accountNumber"), 0) : null;
            String csv = reportService.exportTransactionsToCSV(acc, null, null);

            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"transactions.csv\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class ExportAuditCsvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            String csv = reportService.exportAuditLogsToCSV(null, null);

            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"audit_logs.csv\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class AdminBackupCreateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            Map<String, Object> res = backupService.createBackup();
            StringBuilder json = new StringBuilder("{\"success\":true,\"backup\":{");
            int count = 0;
            for (Map.Entry<String, Object> entry : res.entrySet()) {
                if (count++ > 0) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                    json.append(entry.getValue());
                } else {
                    json.append("\"").append(escapeJson(String.valueOf(entry.getValue()))).append("\"");
                }
            }
            json.append("}}");
            sendResponse(exchange, 200, json.toString());
        }
    }

    private static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            } else if (path.equals("/login") || path.equals("/signin") || path.equals("/sign-in")) {
                path = "/login.html";
            } else if (path.equals("/teller-login") || path.equals("/teller") || path.equals("/teller/login")) {
                path = "/teller-login.html";
            } else if (path.equals("/admin-login") || path.equals("/admin") || path.equals("/admin/login")) {
                path = "/admin-login.html";
            }

            byte[] content = loadResourceBytes("/web" + path);
            if (content == null) {
                content = loadResourceBytes(path);
            }

            if (content != null) {
                String contentType = "text/html; charset=UTF-8";
                if (path.endsWith(".css")) {
                    contentType = "text/css; charset=UTF-8";
                } else if (path.endsWith(".js")) {
                    contentType = "application/javascript; charset=UTF-8";
                } else if (path.endsWith(".svg")) {
                    contentType = "image/svg+xml";
                } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (path.endsWith(".png")) {
                    contentType = "image/png";
                } else if (path.endsWith(".webp")) {
                    contentType = "image/webp";
                } else if (path.endsWith(".ico")) {
                    contentType = "image/x-icon";
                } else if (path.endsWith(".json")) {
                    contentType = "application/json";
                }

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
                exchange.getResponseHeaders().set("X-XSS-Protection", "1; mode=block");
                exchange.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } else {
                String notFound = "404 Not Found: " + path;
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
                exchange.sendResponseHeaders(404, notFound.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(notFound.getBytes(StandardCharsets.UTF_8));
                }
            }
        }

        private byte[] loadResourceBytes(String resPath) {
            try (InputStream is = BankWebServer.class.getResourceAsStream(resPath)) {
                if (is != null) {
                    return is.readAllBytes();
                }
            } catch (Exception ignored) {
            }

            File f = new File("src/main/resources" + (resPath.startsWith("/") ? resPath : "/" + resPath));
            if (f.exists() && f.isFile()) {
                try (InputStream fis = new FileInputStream(f)) {
                    return fis.readAllBytes();
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    private void sendResponse(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Idempotency-Key");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("X-Frame-Options", "DENY");
        ex.getResponseHeaders().set("X-XSS-Protection", "1; mode=block");
        ex.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final int MAX_REQUEST_BODY_BYTES = 65536;

    private Map<String, String> parseJsonBody(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int total = 0;
        int len;
        while ((len = is.read(buf)) != -1) {
            total += len;
            if (total > MAX_REQUEST_BODY_BYTES) {
                throw new IllegalArgumentException("Request payload exceeds maximum allowable size (" + MAX_REQUEST_BODY_BYTES + " bytes).");
            }
            baos.write(buf, 0, len);
        }
        String json = baos.toString(StandardCharsets.UTF_8).trim();
        if (json.isEmpty()) {
            return new HashMap<>();
        }
        return parseJsonObjectToMap(json);
    }

    private Map<String, String> parseJsonObjectToMap(String json) {
        Map<String, String> map = new HashMap<>();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON: root must be an object enclosed in braces.");
        }
        int i = 1;
        int n = json.length() - 1;
        while (i < n) {
            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= n) break;
            if (json.charAt(i) == ',') {
                i++;
                continue;
            }
            if (json.charAt(i) != '"') {
                throw new IllegalArgumentException("Invalid JSON: expected quoted key at position " + i);
            }
            i++; // skip opening quote
            StringBuilder keyBuilder = new StringBuilder();
            boolean escaped = false;
            while (i < n) {
                char c = json.charAt(i);
                if (escaped) {
                    if (c == '"' || c == '\\' || c == '/') keyBuilder.append(c);
                    else if (c == 'b') keyBuilder.append('\b');
                    else if (c == 'f') keyBuilder.append('\f');
                    else if (c == 'n') keyBuilder.append('\n');
                    else if (c == 'r') keyBuilder.append('\r');
                    else if (c == 't') keyBuilder.append('\t');
                    else keyBuilder.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    keyBuilder.append(c);
                }
                i++;
            }
            if (i >= n || json.charAt(i) != '"') {
                throw new IllegalArgumentException("Invalid JSON: unterminated key string.");
            }
            String key = keyBuilder.toString();
            i++; // skip closing quote

            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= n || json.charAt(i) != ':') {
                throw new IllegalArgumentException("Invalid JSON: expected ':' after key '" + key + "'");
            }
            i++; // skip colon

            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= n) {
                throw new IllegalArgumentException("Invalid JSON: missing value for key '" + key + "'");
            }

            char c = json.charAt(i);
            if (c == '"') {
                i++;
                StringBuilder valBuilder = new StringBuilder();
                escaped = false;
                while (i < n) {
                    char vc = json.charAt(i);
                    if (escaped) {
                        if (vc == '"' || vc == '\\' || vc == '/') valBuilder.append(vc);
                        else if (vc == 'b') valBuilder.append('\b');
                        else if (vc == 'f') valBuilder.append('\f');
                        else if (vc == 'n') valBuilder.append('\n');
                        else if (vc == 'r') valBuilder.append('\r');
                        else if (vc == 't') valBuilder.append('\t');
                        else valBuilder.append(vc);
                        escaped = false;
                    } else if (vc == '\\') {
                        escaped = true;
                    } else if (vc == '"') {
                        break;
                    } else {
                        valBuilder.append(vc);
                    }
                    i++;
                }
                if (i >= n || json.charAt(i) != '"') {
                    throw new IllegalArgumentException("Invalid JSON: unterminated string value for key '" + key + "'");
                }
                i++; // skip closing quote
                map.put(key, valBuilder.toString());
            } else if (c == '{' || c == '[') {
                char open = c;
                char close = (open == '{') ? '}' : ']';
                int depth = 0;
                int valStart = i;
                boolean inStr = false;
                boolean strEscaped = false;
                while (i < n) {
                    char sc = json.charAt(i);
                    if (inStr) {
                        if (strEscaped) strEscaped = false;
                        else if (sc == '\\') strEscaped = true;
                        else if (sc == '"') inStr = false;
                    } else {
                        if (sc == '"') inStr = true;
                        else if (sc == open) depth++;
                        else if (sc == close) {
                            depth--;
                            if (depth == 0) {
                                i++;
                                break;
                            }
                        }
                    }
                    i++;
                }
                map.put(key, json.substring(valStart, i).trim());
            } else {
                int valStart = i;
                while (i < n && json.charAt(i) != ',' && json.charAt(i) != '}') {
                    i++;
                }
                String literal = json.substring(valStart, i).trim();
                if ("null".equalsIgnoreCase(literal)) {
                    map.put(key, "");
                } else {
                    map.put(key, literal);
                }
            }

            while (i < n && Character.isWhitespace(json.charAt(i))) i++;
            if (i < n && json.charAt(i) == ',') {
                i++;
            }
        }
        return map;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }

    private String accountToJson(Account a) {
        return String.format(
            "{\"accountNumber\":%d,\"holderName\":\"%s\",\"accountType\":\"%s\",\"balance\":%.2f,\"status\":\"%s\"}",
            a.getAccountNumber(), escapeJson(a.getHolderName()), a.getAccountType(), a.getBalance(), a.getStatus()
        );
    }

    private String transactionToJson(Transaction t) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String timeStr = t.getTimestamp() != null ? t.getTimestamp().format(fmt) : "";
        return String.format(
            "{\"transactionId\":%d,\"transactionRef\":\"%s\",\"accountNumber\":%d,\"txnType\":\"%s\",\"amount\":%.2f,\"balanceAfter\":%.2f,\"remarks\":\"%s\",\"relatedAccountNumber\":%s,\"timestamp\":\"%s\"}",
            t.getTransactionId(), t.getTransactionRef() != null ? t.getTransactionRef() : "", t.getAccountNumber(),
            t.getTxnType(), t.getAmount(), t.getBalanceAfter(), escapeJson(t.getRemarks()),
            t.getRelatedAccountNumber() != null ? t.getRelatedAccountNumber().toString() : "null", timeStr
        );
    }

    private String receiptToJson(TransactionReceipt r) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timeStr = r.getTimestamp() != null ? r.getTimestamp().format(fmt) : "";
        return String.format(
            "{\"referenceNumber\":\"%s\",\"transactionType\":\"%s\",\"amount\":%.2f,\"balanceAfter\":%.2f,\"remarks\":\"%s\",\"timestamp\":\"%s\",\"relatedAccount\":%s}",
            r.getTransactionRef(), r.getTransactionType(), r.getAmount(), r.getBalanceAfter(),
            escapeJson(r.getRemarks()), timeStr,
            r.getBeneficiaryAccountNumber() != null ? r.getBeneficiaryAccountNumber().toString() : "null"
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    private int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private long parseLong(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid monetary amount format: " + s);
        }
    }

    private class SystemStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            try {
                int adminCount = userDAO.countAdmins();
                int totalUsers = userDAO.countUsers();
                String json = String.format(
                    "{\"success\":true,\"hasAdmin\":%b,\"adminCount\":%d,\"totalUsers\":%d}",
                    adminCount > 0, adminCount, totalUsers
                );
                sendResponse(exchange, 200, json);
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Failed to retrieve system status", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while retrieving system status.\"}");
            }
        }
    }

    private class SetupAdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            try {
                if (userDAO.countAdmins() > 0) {
                    sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Master administrator has already been initialized.\"}");
                    return;
                }
                Map<String, String> body = parseJsonBody(exchange);
                String username = body.getOrDefault("username", "").trim();
                String password = body.getOrDefault("password", "").trim();
                String confirmPassword = body.getOrDefault("confirmPassword", "").trim();
                String fullName = body.getOrDefault("fullName", "System Administrator").trim();
                String email = body.getOrDefault("email", "").trim();

                if (username.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Admin username is required.\"}");
                    return;
                }
                if (password.isEmpty()) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Admin password is required.\"}");
                    return;
                }
                if (!password.equals(confirmPassword)) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Passwords do not match.\"}");
                    return;
                }
                try {
                    ValidationUtil.validateStrongPassword(password);
                    if (!email.isEmpty()) {
                        ValidationUtil.validateEmail(email);
                    }
                } catch (IllegalArgumentException valEx) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(valEx.getMessage()) + "\"}");
                    return;
                }

                String hash = PasswordUtil.hashPassword(password);
                User admin = userDAO.createStaffUser(username, hash, Role.ADMIN);
                String token = sessionService.createSession(admin);
                String ip = getClientIp(exchange);
                String auditDesc = email.isEmpty()
                        ? "Initial master administrator account provisioned"
                        : "Initial master administrator account provisioned (" + email + ")";
                recordAuditQuietly(username, Role.ADMIN, "MASTER_ADMIN_INIT", "SYSTEM",
                        String.valueOf(admin.getUserId()), auditDesc, "SUCCESS", ip);

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"message\":\"Master administrator initialized successfully!\",");
                json.append("\"token\":\"").append(token).append("\",");
                json.append("\"user\":{\"userId\":").append(admin.getUserId()).append(",");
                json.append("\"username\":\"").append(escapeJson(admin.getUsername())).append("\",");
                json.append("\"fullName\":\"").append(escapeJson(fullName)).append("\",");
                json.append("\"email\":\"").append(escapeJson(email)).append("\",");
                json.append("\"role\":\"ADMIN\"}}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class CreateStaffHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            String username = body.getOrDefault("username", "").trim();
            String password = body.getOrDefault("password", "").trim();
            String confirmPassword = body.getOrDefault("confirmPassword", "").trim();
            String roleStr = body.getOrDefault("role", "TELLER").trim().toUpperCase();

            if (username.isEmpty()) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Username is required.\"}");
                return;
            }
            if (password.isEmpty() || !password.equals(confirmPassword)) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Passwords do not match.\"}");
                return;
            }
            Role targetRole;
            try {
                targetRole = Role.valueOf(roleStr);
                if (targetRole == Role.CUSTOMER) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Use customer onboarding for customer accounts.\"}");
                    return;
                }
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid role specified. Must be TELLER or ADMIN.\"}");
                return;
            }

            try {
                if (userDAO.getUserByUsername(username) != null) {
                    sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Username '" + username + "' is already taken.\"}");
                    return;
                }
                ValidationUtil.validateStrongPassword(password);
                String hash = PasswordUtil.hashPassword(password);
                User staff = userDAO.createStaffUser(username, hash, targetRole);
                String ip = getClientIp(exchange);
                recordAuditQuietly(session.getUser().getUsername(), Role.ADMIN, "STAFF_PROVISIONED", "USER",
                        String.valueOf(staff.getUserId()), "Created new staff member: " + username + " with role " + targetRole, "SUCCESS", ip);

                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Staff member '" + username + "' (" + targetRole + ") created successfully!\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class ClearAllDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            // 1. Enforce authentication and ADMIN role authorization
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            // 2. Disallow in production environments
            if (DatabaseConfig.isProduction()) {
                String ip = getClientIp(exchange);
                recordAuditQuietly(session.getUser().getUsername(), Role.ADMIN, "SECURITY_VIOLATION", "DATABASE",
                        "SYSTEM", "Attempted clear-all-data in PRODUCTION mode", "REJECTED", ip);
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Destructive data-reset is strictly disabled in production environments.\"}");
                return;
            }

            // 3. Enforce explicit confirmation token payload
            Map<String, String> body;
            try {
                body = parseJsonBody(exchange);
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid JSON request format.\"}");
                return;
            }

            String confirmation = body.getOrDefault("confirmation", "").trim();
            if (!"CONFIRM_CLEAR_ALL_DATA".equals(confirmation)) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Explicit confirmation token 'CONFIRM_CLEAR_ALL_DATA' is required to execute destructive reset.\"}");
                return;
            }

            // 4. Execute development reset while preserving audit logs
            try {
                String ip = getClientIp(exchange);
                userDAO.clearAllUserData();
                sessionService.clearAllSessions();
                recordAuditQuietly(session.getUser().getUsername(), Role.ADMIN, "CLEAR_ALL_DATA", "DATABASE",
                        "SYSTEM", "Authorized development database reset executed by admin", "SUCCESS", ip);
                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Development customer, teller, account, and transaction data cleared. Audit trail preserved.\"}");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to execute clear-all-data", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to reset development database.\"}");
            }
        }
    }

    // ==========================================
    // EXTENDED BANKING & INNOVATION HANDLERS
    // ==========================================

    private class BeneficiariesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                if (cust == null) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Customer profile not found.\"}");
                    return;
                }

                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    List<Beneficiary> list = beneficiaryService.getBeneficiaries(cust.getCustomerId(), session.getUser());
                    StringBuilder json = new StringBuilder("{\"success\":true,\"beneficiaries\":[");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) json.append(",");
                        Beneficiary b = list.get(i);
                        json.append("{\"beneficiaryId\":").append(b.getBeneficiaryId()).append(",");
                        json.append("\"beneficiaryAccount\":").append(b.getBeneficiaryAccount()).append(",");
                        json.append("\"beneficiaryName\":\"").append(escapeJson(b.getBeneficiaryName())).append("\",");
                        json.append("\"bankName\":\"").append(escapeJson(b.getBankName())).append("\",");
                        json.append("\"ifscCode\":\"").append(escapeJson(b.getIfscCode())).append("\",");
                        json.append("\"transferLimit\":").append(b.getTransferLimit().toPlainString()).append(",");
                        json.append("\"status\":\"").append(escapeJson(b.getStatus())).append("\"}");
                    }
                    json.append("]}");
                    sendResponse(exchange, 200, json.toString());
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> body = parseJsonBody(exchange);
                    int accNo = parseInt(body.get("beneficiaryAccount"), 0);
                    String name = body.getOrDefault("beneficiaryName", body.get("name"));
                    String bank = body.getOrDefault("bankName", "SecureBank Retail");
                    String ifsc = body.getOrDefault("ifscCode", body.get("ifsc"));
                    BigDecimal limit = parseBigDecimal(body.getOrDefault("transferLimit", body.get("limit")));

                    Beneficiary b = beneficiaryService.addBeneficiary(cust.getCustomerId(), accNo, name, bank, ifsc, limit, session.getUser());
                    sendResponse(exchange, 201, "{\"success\":true,\"message\":\"Beneficiary registered successfully.\",\"beneficiaryId\":" + b.getBeneficiaryId() + "}");
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    int benId = parseInt(params.get("id"), 0);
                    if (benId == 0) {
                        Map<String, String> body = parseJsonBody(exchange);
                        benId = parseInt(body.get("beneficiaryId"), 0);
                    }
                    boolean ok = beneficiaryService.deleteBeneficiary(benId, cust.getCustomerId(), session.getUser());
                    sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"" + (ok ? "Beneficiary deactivated." : "Failed to remove beneficiary.") + "\"}");
                } else {
                    sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                }
            } catch (IllegalArgumentException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Beneficiary operation error", ex);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Error processing beneficiary request: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class SpendingInsightsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                if (cust == null) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Customer profile not found.\"}");
                    return;
                }
                SpendingSummary summary = spendingAnalyticsService.calculateSpending(cust.getCustomerId(), session.getUser());

                StringBuilder json = new StringBuilder("{\"success\":true,");
                json.append("\"totalSpent\":").append(summary.getTotalSpent().toPlainString()).append(",");
                json.append("\"transactionCount\":").append(summary.getTransactionCount()).append(",");
                json.append("\"categoryDetails\":[");
                List<SpendingSummary.CategoryDetail> list = summary.getCategoryDetails();
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    SpendingSummary.CategoryDetail cd = list.get(i);
                    json.append("{\"category\":\"").append(escapeJson(cd.getCategory())).append("\",");
                    json.append("\"displayName\":\"").append(escapeJson(cd.getDisplayName())).append("\",");
                    json.append("\"spent\":").append(cd.getSpent().toPlainString()).append(",");
                    json.append("\"budgetLimit\":").append(cd.getBudgetLimit() != null ? cd.getBudgetLimit().toPlainString() : "null").append(",");
                    json.append("\"percentUtilized\":").append(cd.getPercentUtilized()).append("}");
                }
                json.append("],");
                json.append("\"largestExpense\":");
                if (summary.getLargestExpense() != null) {
                    json.append(transactionToJson(summary.getLargestExpense()));
                } else {
                    json.append("null");
                }
                json.append("}");

                sendResponse(exchange, 200, json.toString());
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error loading spending insights", ex);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to generate spending insights: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class BudgetsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                if (cust == null) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Customer profile not found.\"}");
                    return;
                }

                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    List<Budget> list = spendingAnalyticsService.getBudgets(cust.getCustomerId(), session.getUser());
                    StringBuilder json = new StringBuilder("{\"success\":true,\"budgets\":[");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) json.append(",");
                        Budget b = list.get(i);
                        json.append("{\"budgetId\":").append(b.getBudgetId()).append(",");
                        json.append("\"category\":\"").append(escapeJson(b.getCategory())).append("\",");
                        json.append("\"monthlyLimit\":").append(b.getMonthlyLimit().toPlainString()).append("}");
                    }
                    json.append("]}");
                    sendResponse(exchange, 200, json.toString());
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> body = parseJsonBody(exchange);
                    String category = body.getOrDefault("category", "OTHER");
                    BigDecimal limit = parseBigDecimal(body.getOrDefault("monthlyLimit", body.get("limit")));
                    int id = spendingAnalyticsService.setBudget(cust.getCustomerId(), category, limit, session.getUser());
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Budget limit updated successfully.\",\"budgetId\":" + id + "}");
                } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                    Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                    int id = parseInt(params.get("id"), 0);
                    if (id == 0) {
                        Map<String, String> body = parseJsonBody(exchange);
                        id = parseInt(body.get("budgetId"), 0);
                    }
                    boolean ok = spendingAnalyticsService.deleteBudget(id, cust.getCustomerId(), session.getUser());
                    sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"" + (ok ? "Budget removed." : "Failed to remove budget.") + "\"}");
                } else {
                    sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                }
            } catch (IllegalArgumentException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Budget handler error", ex);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Error processing budget: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class BillersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            try {
                List<Biller> list = bankingModulesService.getAllBillers();
                StringBuilder json = new StringBuilder("{\"success\":true,\"billers\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    Biller b = list.get(i);
                    json.append("{\"billerId\":").append(b.getBillerId()).append(",");
                    json.append("\"billerName\":\"").append(escapeJson(b.getBillerName())).append("\",");
                    json.append("\"category\":\"").append(escapeJson(b.getCategory())).append("\",");
                    json.append("\"billerCode\":\"").append(escapeJson(b.getBillerCode())).append("\"}");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception ex) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to fetch billers: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class PayBillHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int accNum = parseInt(body.get("accountNumber"), 0);
            String pin = body.getOrDefault("pin", "");
            int billerId = parseInt(body.get("billerId"), 0);
            String consumerNum = body.getOrDefault("consumerNumber", "");
            BigDecimal amount = parseBigDecimal(body.get("amount"));

            if (!verifyCustomerAccountOwnership(exchange, session, accNum)) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                BillPayment bp = bankingModulesService.payBill(cust.getCustomerId(), accNum, pin, billerId, consumerNum, amount, session.getUser());
                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Bill payment of ₹" + amount.toPlainString() + " processed successfully!\",\"paymentRef\":\"" + bp.getPaymentRef() + "\"}");
            } catch (IllegalArgumentException | SecurityException | InsufficientFundsException | InactiveAccountException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Bill payment error", ex);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to process bill payment: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class CustomerBillPaymentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                List<BillPayment> list = bankingModulesService.getCustomerBillPayments(cust.getCustomerId(), session.getUser());
                StringBuilder json = new StringBuilder("{\"success\":true,\"payments\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    BillPayment p = list.get(i);
                    json.append("{\"paymentId\":").append(p.getPaymentId()).append(",");
                    json.append("\"paymentRef\":\"").append(escapeJson(p.getPaymentRef())).append("\",");
                    json.append("\"accountNumber\":").append(p.getAccountNumber()).append(",");
                    json.append("\"billerName\":\"").append(escapeJson(p.getBillerName())).append("\",");
                    json.append("\"consumerNumber\":\"").append(escapeJson(p.getConsumerNumber())).append("\",");
                    json.append("\"amount\":").append(p.getAmount().toPlainString()).append(",");
                    json.append("\"status\":\"").append(escapeJson(p.getStatus())).append("\",");
                    json.append("\"paidAt\":\"").append(p.getPaidAt().toString()).append("\"}");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception ex) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load bill payments: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class CreateFixedDepositHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int accNum = parseInt(body.get("accountNumber"), 0);
            String pin = body.getOrDefault("pin", "");
            BigDecimal principal = parseBigDecimal(body.get("principal"));
            int tenure = parseInt(body.get("tenureMonths"), 12);

            if (!verifyCustomerAccountOwnership(exchange, session, accNum)) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                FixedDeposit fd = bankingModulesService.openFixedDeposit(cust.getCustomerId(), accNum, pin, principal, tenure, session.getUser());
                sendResponse(exchange, 201, "{\"success\":true,\"message\":\"Fixed Deposit " + fd.getFdRef() + " opened! Maturity: ₹" + fd.getMaturityAmount().toPlainString() + "\",\"fdRef\":\"" + fd.getFdRef() + "\"}");
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class CustomerFixedDepositsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                List<FixedDeposit> list = bankingModulesService.getCustomerFixedDeposits(cust.getCustomerId(), session.getUser());
                StringBuilder json = new StringBuilder("{\"success\":true,\"fixedDeposits\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    FixedDeposit fd = list.get(i);
                    json.append("{\"fdId\":").append(fd.getFdId()).append(",");
                    json.append("\"fdRef\":\"").append(escapeJson(fd.getFdRef())).append("\",");
                    json.append("\"accountNumber\":").append(fd.getAccountNumber()).append(",");
                    json.append("\"principalAmount\":").append(fd.getPrincipalAmount().toPlainString()).append(",");
                    json.append("\"tenureMonths\":").append(fd.getTenureMonths()).append(",");
                    json.append("\"interestRate\":").append(fd.getInterestRate().toPlainString()).append(",");
                    json.append("\"maturityAmount\":").append(fd.getMaturityAmount().toPlainString()).append(",");
                    json.append("\"status\":\"").append(escapeJson(fd.getStatus())).append("\",");
                    json.append("\"startDate\":\"").append(fd.getStartDate().toString()).append("\",");
                    json.append("\"maturityDate\":\"").append(fd.getMaturityDate().toString()).append("\"}");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception ex) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load fixed deposits: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class CloseFixedDepositHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int fdId = parseInt(body.get("fdId"), 0);

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                boolean ok = bankingModulesService.closeFixedDeposit(fdId, cust.getCustomerId(), session.getUser());
                sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"Fixed Deposit #" + fdId + " liquidated and balance returned to account.\"}");
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class ApplyLoanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            String loanType = body.getOrDefault("loanType", "PERSONAL");
            BigDecimal amount = parseBigDecimal(body.get("amount"));
            int tenure = parseInt(body.get("tenureMonths"), 24);
            int disbAcc = parseInt(body.get("disbursedAccount"), 0);

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                Loan loan = bankingModulesService.applyLoan(cust.getCustomerId(), loanType, amount, tenure, disbAcc > 0 ? disbAcc : null, session.getUser());
                sendResponse(exchange, 201, "{\"success\":true,\"message\":\"Loan application (" + loan.getLoanRef() + ") submitted. Monthly EMI: ₹" + loan.getEmiAmount().toPlainString() + "\",\"loanRef\":\"" + loan.getLoanRef() + "\"}");
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class CustomerLoansHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                List<Loan> list = bankingModulesService.getCustomerLoans(cust.getCustomerId(), session.getUser());
                sendResponse(exchange, 200, serializeLoanList(list));
            } catch (Exception ex) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load loans: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class AdminLoansHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;
            if (session.getRole() != Role.TELLER && session.getRole() != Role.ADMIN) {
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Access Denied: Staff review authorization required.\"}");
                return;
            }

            try {
                List<Loan> list = bankingModulesService.getAllLoansForStaff(session.getUser());
                sendResponse(exchange, 200, serializeLoanList(list));
            } catch (Exception ex) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load staff loan register: " + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class ReviewLoanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;
            if (session.getRole() != Role.TELLER && session.getRole() != Role.ADMIN) {
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Access Denied: Staff authorization required.\"}");
                return;
            }

            Map<String, String> body = parseJsonBody(exchange);
            int loanId = parseInt(body.get("loanId"), 0);
            String decision = body.getOrDefault("decision", "APPROVED");

            try {
                boolean ok = bankingModulesService.reviewLoanApplication(loanId, decision, session.getUser());
                sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"Loan #" + loanId + " " + decision.toLowerCase() + " successfully.\"}");
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class RepayLoanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int loanId = parseInt(body.get("loanId"), 0);
            int fromAccount = parseInt(body.get("accountNumber"), 0);
            String pin = body.getOrDefault("pin", "");
            BigDecimal amount = parseBigDecimal(body.get("amount"));

            if (!verifyCustomerAccountOwnership(exchange, session, fromAccount)) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                boolean ok = bankingModulesService.repayLoanEmi(loanId, cust.getCustomerId(), fromAccount, pin, amount, session.getUser());
                sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"EMI repayment of ₹" + amount.toPlainString() + " processed!\"}");
            } catch (Exception ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
            }
        }
    }

    private class CustomerCardsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                List<DebitCard> list = bankingModulesService.getCustomerCards(cust.getCustomerId(), session.getUser());
                StringBuilder json = new StringBuilder("{\"success\":true,\"cards\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    DebitCard c = list.get(i);
                    json.append("{\"cardId\":").append(c.getCardId()).append(",");
                    json.append("\"accountNumber\":").append(c.getAccountNumber()).append(",");
                    json.append("\"cardNumberMasked\":\"").append(escapeJson(c.getCardNumberMasked())).append("\",");
                    json.append("\"cardType\":\"").append(escapeJson(c.getCardType())).append("\",");
                    json.append("\"dailyLimit\":").append(c.getDailyLimit().toPlainString()).append(",");
                    json.append("\"isBlocked\":").append(c.isBlocked()).append(",");
                    json.append("\"contactlessEnabled\":").append(c.isContactlessEnabled()).append(",");
                    json.append("\"expiryDate\":\"").append(escapeJson(c.getExpiryDate())).append("\"}");
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load cards: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class CardToggleBlockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int cardId = parseInt(body.get("cardId"), 0);
            boolean blocked = Boolean.parseBoolean(body.getOrDefault("blocked", body.get("isBlocked")));

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                boolean ok = bankingModulesService.toggleCardBlock(cardId, cust.getCustomerId(), blocked, session.getUser());
                sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"Card status updated successfully.\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class CardUpdateLimitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int cardId = parseInt(body.get("cardId"), 0);
            BigDecimal limit = parseBigDecimal(body.getOrDefault("dailyLimit", body.get("limit")));

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                boolean ok = bankingModulesService.updateCardDailyLimit(cardId, cust.getCustomerId(), limit, session.getUser());
                sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"Card limit updated successfully to ₹" + limit.toPlainString() + "\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class CardToggleContactlessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.CUSTOMER);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int cardId = parseInt(body.get("cardId"), 0);
            boolean enabled = Boolean.parseBoolean(body.getOrDefault("enabled", body.get("contactlessEnabled")));

            try {
                Customer cust = customerDAO.getCustomerByUserId(session.getUser().getUserId());
                boolean ok = bankingModulesService.toggleContactless(cardId, cust.getCustomerId(), enabled, session.getUser());
                sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"Contactless payment preference updated.\"}");
            } catch (Exception e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminLedgerAuditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                Map<String, Object> audit = ledgerService.auditLedgerIntegrity();
                StringBuilder json = new StringBuilder("{\"success\":true,");
                json.append("\"unbalancedTransactions\":").append(audit.get("unbalancedTransactions")).append(",");
                json.append("\"isGeneralLedgerBalanced\":").append(audit.get("isGeneralLedgerBalanced")).append(",");
                json.append("\"totalAccountsAudited\":").append(audit.get("totalAccountsAudited")).append(",");
                json.append("\"matchedAccounts\":").append(audit.get("matchedAccounts")).append(",");
                json.append("\"discrepancyCount\":").append(audit.get("discrepancyCount")).append(",");
                json.append("\"status\":\"").append(audit.get("status")).append("\",");
                json.append("\"discrepancies\":[");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> discrepancies = (List<Map<String, Object>>) audit.get("discrepancies");
                if (discrepancies != null) {
                    for (int i = 0; i < discrepancies.size(); i++) {
                        if (i > 0) json.append(",");
                        Map<String, Object> d = discrepancies.get(i);
                        json.append("{\"accountNumber\":").append(d.get("accountNumber")).append(",");
                        json.append("\"holderName\":\"").append(escapeJson(String.valueOf(d.get("holderName")))).append("\",");
                        json.append("\"storedBalance\":").append(d.get("storedBalance")).append(",");
                        json.append("\"ledgerBalance\":").append(d.get("ledgerBalance")).append(",");
                        json.append("\"difference\":").append(d.get("difference")).append("}");
                    }
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Ledger audit error", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to audit general ledger: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminResolveFraudAlertHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed.\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            int alertId = parseInt(body.get("alertId"), 0);
            String decision = body.getOrDefault("decision", "RESOLVED");

            try {
                boolean ok = suspiciousAlertDAO.resolveAlert(alertId, decision, session.getUser().getUsername());
                String ip = getClientIp(exchange);
                recordAuditQuietly(session.getUser().getUsername(), Role.ADMIN, "ALERT_RESOLVED", "ALERT",
                        String.valueOf(alertId), "Resolved alert #" + alertId + " with decision: " + decision, "SUCCESS", ip);
                sendResponse(exchange, ok ? 200 : 400, "{\"success\":" + ok + ",\"message\":\"Alert #" + alertId + " marked as " + decision + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error resolving alert", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to resolve alert: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private String serializeLoanList(List<Loan> list) {
        StringBuilder json = new StringBuilder("{\"success\":true,\"loans\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) json.append(",");
            Loan l = list.get(i);
            json.append("{\"loanId\":").append(l.getLoanId()).append(",");
            json.append("\"loanRef\":\"").append(escapeJson(l.getLoanRef())).append("\",");
            json.append("\"customerId\":").append(l.getCustomerId()).append(",");
            json.append("\"loanType\":\"").append(escapeJson(l.getLoanType())).append("\",");
            json.append("\"principalAmount\":").append(l.getPrincipalAmount().toPlainString()).append(",");
            json.append("\"interestRate\":").append(l.getInterestRate().toPlainString()).append(",");
            json.append("\"tenureMonths\":").append(l.getTenureMonths()).append(",");
            json.append("\"emiAmount\":").append(l.getEmiAmount().toPlainString()).append(",");
            json.append("\"outstandingBalance\":").append(l.getOutstandingBalance().toPlainString()).append(",");
            json.append("\"disbursedAccount\":").append(l.getDisbursedAccount() != null ? l.getDisbursedAccount() : "null").append(",");
            json.append("\"status\":\"").append(escapeJson(l.getStatus())).append("\",");
            json.append("\"createdAt\":\"").append(l.getCreatedAt().toString()).append("\"}");
        }
        json.append("]}");
        return json.toString();
    }

    private String readRawRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        int total = 0;
        while ((len = is.read(buf)) != -1) {
            total += len;
            if (total > MAX_REQUEST_BODY_BYTES) {
                throw new IllegalArgumentException("Request payload exceeds maximum allowable size (" + MAX_REQUEST_BODY_BYTES + " bytes).");
            }
            baos.write(buf, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8).trim();
    }

    private String serializeExternalPayment(ExternalPaymentRecord p) {
        if (p == null) return "null";
        StringBuilder json = new StringBuilder("{");
        json.append("\"paymentId\":").append(p.getPaymentId()).append(",");
        json.append("\"clientReference\":\"").append(escapeJson(p.getClientReference())).append("\",");
        json.append("\"idempotencyKey\":\"").append(escapeJson(p.getIdempotencyKey())).append("\",");
        json.append("\"accountNumber\":").append(p.getAccountNumber()).append(",");
        json.append("\"amount\":").append(p.getAmount().toPlainString()).append(",");
        json.append("\"destinationAccount\":\"").append(escapeJson(p.getDestinationAccount())).append("\",");
        json.append("\"destinationBank\":\"").append(escapeJson(p.getDestinationBank())).append("\",");
        json.append("\"providerName\":\"").append(escapeJson(p.getProviderName())).append("\",");
        json.append("\"providerReference\":").append(p.getProviderReference() != null ? "\"" + escapeJson(p.getProviderReference()) + "\"" : "null").append(",");
        json.append("\"status\":\"").append(p.getStatus().name()).append("\",");
        json.append("\"isSimulated\":").append(p.isSimulated()).append(",");
        json.append("\"purpose\":\"").append(escapeJson(p.getPurpose() != null ? p.getPurpose() : "")).append("\",");
        json.append("\"failureReason\":").append(p.getFailureReason() != null ? "\"" + escapeJson(p.getFailureReason()) + "\"" : "null").append(",");
        json.append("\"createdAt\":\"").append(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "").append("\"");
        json.append("}");
        return json.toString();
    }

    private class ExternalPaymentInitiateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;

            if (session.getRole() == Role.ADMIN) {
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Access Denied: Administrators cannot initiate customer payment operations.\"}");
                return;
            }

            Map<String, String> body;
            try {
                body = parseJsonBody(exchange);
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Invalid JSON request: " + escapeJson(e.getMessage()) + "\"}");
                return;
            }

            int accNo = parseInt(body.get("accountNumber"), 0);
            if (accNo <= 0) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Valid account number is required.\"}");
                return;
            }

            if (!verifyCustomerAccountOwnership(exchange, session, accNo)) {
                return;
            }

            BigDecimal amount;
            try {
                amount = parseBigDecimal(body.get("amount"));
                ValidationUtil.validateMonetaryAmount(amount);
            } catch (IllegalArgumentException ex) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(ex.getMessage()) + "\"}");
                return;
            }

            String pin = body.getOrDefault("pin", "");
            String destAcc = body.getOrDefault("destinationAccount", "");
            String destBank = body.getOrDefault("destinationBank", "");
            String purpose = body.getOrDefault("purpose", "External Payment");
            String idempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
                idempotencyKey = body.get("idempotencyKey");
            }

            try {
                ExternalPaymentRecord payment = externalPaymentService.initiateExternalPayment(
                    accNo, pin, amount, destAcc, destBank, purpose, idempotencyKey, session.getUser()
                );
                Account updated = bankDAO.getAccountByNumber(accNo);

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"message\":\"Payment initiated (Status: ").append(payment.getStatus()).append(")\",");
                json.append("\"payment\":").append(serializeExternalPayment(payment)).append(",");
                if (updated != null) {
                    json.append("\"updatedAccount\":").append(accountToJson(updated)).append(",");
                }
                json.append("\"isSimulated\":").append(payment.isSimulated()).append(",");
                json.append("\"disclaimer\":\"").append(escapeJson(PaymentResponse.SIMULATION_DISCLAIMER)).append("\"}");
                sendResponse(exchange, 200, json.toString());
            } catch (IllegalArgumentException | InsufficientFundsException | InactiveAccountException | AccountNotFoundException | AuthorizationException | SecurityException e) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Internal error in ExternalPaymentInitiateHandler", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"An internal server error occurred while processing external payment.\"}");
            }
        }
    }

    private class ExternalPaymentInquireHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            int paymentId = parseInt(query.get("paymentId"), 0);
            if (paymentId <= 0) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Valid paymentId query parameter is required.\"}");
                return;
            }

            try {
                ExternalPaymentRecord synced = externalPaymentService.inquireAndSyncPayment(paymentId);
                if (synced == null) {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Payment record not found or already terminal.\"}");
                    return;
                }
                sendResponse(exchange, 200, "{\"success\":true,\"payment\":" + serializeExternalPayment(synced) + "}");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Internal error in ExternalPaymentInquireHandler", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Internal error querying payment status: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class ExternalPaymentHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            int accNo = parseInt(query.get("accountNumber"), 0);
            int limit = parseInt(query.get("limit"), 20);

            if (accNo > 0 && !verifyCustomerAccountOwnership(exchange, session, accNo)) {
                return;
            }

            try {
                List<ExternalPaymentRecord> list = externalPaymentService.getPaymentsForAccount(accNo, limit);
                StringBuilder json = new StringBuilder("{\"success\":true,\"payments\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(serializeExternalPayment(list.get(i)));
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Internal error in ExternalPaymentHistoryHandler", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Internal error retrieving payment history: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class PaymentWebhookCallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String signature = exchange.getRequestHeaders().getFirst("X-Signature");
            if (signature == null || signature.trim().isEmpty()) {
                signature = exchange.getRequestHeaders().getFirst("X-Webhook-Signature");
            }

            String rawBody = readRawRequestBody(exchange);
            Map<String, String> body = parseJsonObjectToMap(rawBody);

            String providerRef = body.get("providerReference");
            String clientRef = body.get("clientReference");
            String statusStr = body.getOrDefault("status", "SUCCESS");
            PaymentStatus status;
            try {
                status = PaymentStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception e) {
                status = PaymentStatus.SUCCESS;
            }

            try {
                boolean processed = externalPaymentService.processProviderCallback(rawBody, signature, providerRef, clientRef, status);
                sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Webhook callback processed successfully\",\"processed\":" + processed + "}");
            } catch (SecurityException se) {
                sendResponse(exchange, 401, "{\"success\":false,\"message\":\"Authentication failed: " + escapeJson(se.getMessage()) + "\"}");
            } catch (IllegalArgumentException iae) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Bad request: " + escapeJson(iae.getMessage()) + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Internal error in PaymentWebhookCallbackHandler", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to process payment callback: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminPaymentReconcileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;
            if (session.getRole() != Role.ADMIN) {
                sendResponse(exchange, 403, "{\"success\":false,\"message\":\"Access Denied: Administrator role required.\"}");
                return;
            }

            try {
                Map<String, Object> result = externalPaymentService.reconcileExternalPayments();
                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"reconciliation\":{");
                json.append("\"totalPendingEvaluated\":").append(result.get("totalPendingEvaluated")).append(",");
                json.append("\"settledCount\":").append(result.get("settledCount")).append(",");
                json.append("\"failedCount\":").append(result.get("failedCount"));
                json.append("}}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Internal error in AdminPaymentReconcileHandler", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to execute payment reconciliation: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    // ==========================================
    // SESSION MONITORING ENDPOINTS
    // ==========================================

    private class AdminSessionStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                sessionService.cleanExpiredSessions();
                Map<String, Integer> counts = sessionService.getSessionDAO().getActiveSessionCountsByRole();
                String json = String.format(
                    "{\"success\":true,\"stats\":{\"totalActive\":%d,\"customersOnline\":%d,\"tellersOnline\":%d,\"adminsOnline\":%d}}",
                    counts.getOrDefault("totalActive", 0),
                    counts.getOrDefault("customersOnline", 0),
                    counts.getOrDefault("tellersOnline", 0),
                    counts.getOrDefault("adminsOnline", 0)
                );
                sendResponse(exchange, 200, json);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading session stats", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load session statistics: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminActiveSessionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                sessionService.cleanExpiredSessions();
                List<UserSession> activeList = sessionService.getSessionDAO().getActiveSessions();
                StringBuilder json = new StringBuilder("{\"success\":true,\"currentAdminSessionId\":\"")
                        .append(escapeJson(session.getSessionId())).append("\",\"sessions\":[");
                for (int i = 0; i < activeList.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(sessionToJson(activeList.get(i)));
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading active sessions", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load active sessions: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminSessionHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI().getQuery());
                String search = q.getOrDefault("search", "").trim();
                String role = q.getOrDefault("role", "ALL").trim();
                String status = q.getOrDefault("status", "ALL").trim();
                String fromStr = q.getOrDefault("fromDate", "").trim();
                String toStr = q.getOrDefault("toDate", "").trim();
                int page = parseInt(q.get("page"), 1);
                int pageSize = parseInt(q.get("pageSize"), 10);
                if (page < 1) page = 1;
                if (pageSize < 1) pageSize = 10;
                if (pageSize > 100) pageSize = 100;

                LocalDate fromDate = null;
                if (!fromStr.isEmpty()) {
                    try { fromDate = LocalDate.parse(fromStr); } catch (Exception ignored) {}
                }
                LocalDate toDate = null;
                if (!toStr.isEmpty()) {
                    try { toDate = LocalDate.parse(toStr); } catch (Exception ignored) {}
                }

                int total = sessionService.getSessionDAO().countSessionHistory(search, role, status, fromDate, toDate);
                List<UserSession> list = sessionService.getSessionDAO().getSessionHistory(search, role, status, fromDate, toDate, page, pageSize);
                int totalPages = (int) Math.ceil((double) total / pageSize);
                if (totalPages < 1) totalPages = 1;

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true,\"page\":").append(page);
                json.append(",\"pageSize\":").append(pageSize);
                json.append(",\"total\":").append(total);
                json.append(",\"totalPages\":").append(totalPages);
                json.append(",\"sessions\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(sessionToJson(list.get(i)));
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading session history", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load session history: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminTerminateSessionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            Map<String, String> body = parseJsonBody(exchange);
            String targetSessionId = body.getOrDefault("sessionId", "").trim();
            if (targetSessionId.isEmpty()) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"Session ID is required.\"}");
                return;
            }

            if (targetSessionId.equals(session.getSessionId())) {
                sendResponse(exchange, 400, "{\"success\":false,\"message\":\"You cannot terminate your own active administrator session.\"}");
                return;
            }

            try {
                boolean ok = sessionService.terminateSessionByAdmin(targetSessionId, session.getUser().getUsername());
                if (ok) {
                    recordAuditQuietly(session.getUser().getUsername(), Role.ADMIN, "TERMINATE_SESSION", "SESSION",
                            targetSessionId, "Administrator terminated active user session", "SUCCESS", getClientIp(exchange));
                    sendResponse(exchange, 200, "{\"success\":true,\"message\":\"Session successfully terminated.\"}");
                } else {
                    sendResponse(exchange, 404, "{\"success\":false,\"message\":\"Session not found or already inactive.\"}");
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error terminating session", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to terminate session: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class AdminSessionRecentActivityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticateAndAuthorize(exchange, Role.ADMIN);
            if (session == null) return;

            try {
                List<UserSession> recents = sessionService.getSessionDAO().getRecentActivity(20);
                StringBuilder json = new StringBuilder("{\"success\":true,\"recentActivity\":[");
                for (int i = 0; i < recents.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(sessionToJson(recents.get(i)));
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading session recent activity", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load recent activity: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private class UserRecentSessionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            SessionService.SessionRecord session = authenticate(exchange);
            if (session == null) return;

            try {
                int userId = session.getUser().getUserId();
                List<UserSession> userList = sessionService.getSessionDAO().getUserRecentSessions(userId, 6);

                StringBuilder json = new StringBuilder();
                json.append("{\"success\":true");
                json.append(",\"currentSession\":{");
                json.append("\"sessionId\":\"").append(escapeJson(session.getSessionId())).append("\",");
                json.append("\"loginTime\":\"").append(session.getCreatedAt().toString()).append("\",");
                json.append("\"ipAddress\":\"").append(escapeJson(session.getIpAddress())).append("\"");
                json.append("},\"recentSessions\":[");
                for (int i = 0; i < userList.size(); i++) {
                    if (i > 0) json.append(",");
                    json.append(sessionToJson(userList.get(i)));
                }
                json.append("]}");
                sendResponse(exchange, 200, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error loading user recent sessions", e);
                sendResponse(exchange, 500, "{\"success\":false,\"message\":\"Failed to load login activity: " + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private String sessionToJson(UserSession s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"sessionId\":\"").append(escapeJson(s.getSessionId())).append("\",");
        sb.append("\"userId\":").append(s.getUserId()).append(",");
        sb.append("\"username\":\"").append(escapeJson(s.getUsername())).append("\",");
        sb.append("\"role\":\"").append(s.getRole() != null ? s.getRole().name() : "").append("\",");
        sb.append("\"loginTime\":\"").append(s.getLoginTime() != null ? s.getLoginTime().toString() : "").append("\",");
        sb.append("\"logoutTime\":").append(s.getLogoutTime() != null ? "\"" + s.getLogoutTime().toString() + "\"" : "null").append(",");
        sb.append("\"lastActivityTime\":\"").append(s.getLastActivityTime() != null ? s.getLastActivityTime().toString() : "").append("\",");
        sb.append("\"sessionStatus\":\"").append(escapeJson(s.getSessionStatus())).append("\",");
        sb.append("\"sessionEndReason\":").append(s.getSessionEndReason() != null ? "\"" + escapeJson(s.getSessionEndReason()) + "\"" : "null").append(",");
        sb.append("\"ipAddress\":\"").append(escapeJson(s.getIpAddress() != null ? s.getIpAddress() : "")).append("\",");
        sb.append("\"duration\":\"").append(escapeJson(s.getDurationFormatted())).append("\"");
        sb.append("}");
        return sb.toString();
    }
}

