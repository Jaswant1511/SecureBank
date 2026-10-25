package com.securebank.ui.console;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.dao.UserDAO;
import com.securebank.exception.AuthenticationException;
import com.securebank.exception.AuthorizationException;
import com.securebank.exception.InactiveAccountException;
import com.securebank.exception.InsufficientFundsException;
import com.securebank.model.Account;
import com.securebank.model.AccountStatus;
import com.securebank.model.AccountType;
import com.securebank.model.AuditLog;
import com.securebank.model.Role;
import com.securebank.model.SavingsAccount;
import com.securebank.model.Transaction;
import com.securebank.model.TransactionReceipt;
import com.securebank.model.User;
import com.securebank.service.AccountService;
import com.securebank.service.AuthService;
import com.securebank.service.BankingService;
import com.securebank.util.CurrencyFormatter;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: BankApp.java (Console UI)
 *
 * Professional CLI Terminal with authenticated sessions, role-based workflows,
 * exact BigDecimal transactions, receipt formatting, and audit integration.
 */
public class BankApp {

    private final AuthService authService;
    private final AccountService accountService;
    private final BankingService bankingService;
    private final BankDAO bankDAO;
    private final UserDAO userDAO;
    private final AuditDAO auditDAO;
    private final Scanner scanner;

    private User currentUser;
    private Account activeAccount;

    public BankApp() {
        DatabaseConfig.initializeSchema();
        this.bankDAO = new BankDAO();
        this.userDAO = new UserDAO();
        this.auditDAO = new AuditDAO();
        CustomerDAO customerDAO = new CustomerDAO();
        TransactionDAO transactionDAO = new TransactionDAO();

        this.authService = new AuthService(userDAO, auditDAO);
        this.accountService = new AccountService(bankDAO, customerDAO, auditDAO);
        this.bankingService = new BankingService(bankDAO, transactionDAO, customerDAO, auditDAO);
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        BankApp app = new BankApp();
        app.run();
    }

    public void run() {
        printBanner();

        boolean running = true;
        while (running) {
            if (currentUser == null) {
                running = handleAuthMenu();
            } else {
                handleSessionMenu();
            }
        }
        System.out.println("\n[SYSTEM] SecureBank Session Terminated. Have a great day!\n");
    }

    private boolean handleAuthMenu() {
        System.out.println("\n================================================================================");
        System.out.println("   AUTHENTICATION & SECURE SIGN-IN GATEWAY");
        System.out.println("================================================================================");
        System.out.println("[1] Sign In with Username & Password");
        System.out.println("[2] Quick Demo Sign In (Aarav - Customer)");
        System.out.println("[3] Quick Demo Sign In (Suresh - Teller)");
        System.out.println("[4] Quick Demo Sign In (Rajesh - Administrator)");
        System.out.println("[0] Exit Application");

        int choice = readInt("Select Option (0-4): ");
        try {
            switch (choice) {
                case 1:
                    performLogin();
                    break;
                case 2:
                    currentUser = authService.login("aarav", "Password@123", "CONSOLE");
                    afterLogin();
                    break;
                case 3:
                    currentUser = authService.login("teller_suresh", "Teller@123", "CONSOLE");
                    afterLogin();
                    break;
                case 4:
                    currentUser = authService.login("admin_rajesh", "Admin@123", "CONSOLE");
                    afterLogin();
                    break;
                case 0:
                    return false;
                default:
                    System.out.println("[WARN] Invalid option. Please choose between 0 and 4.");
            }
        } catch (AuthenticationException e) {
            System.out.println("\n[SIGN IN FAILED] " + e.getMessage());
        }
        return true;
    }

    private void performLogin() throws AuthenticationException {
        System.out.print("Enter Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Enter Password: ");
        String password = scanner.nextLine().trim();

        this.currentUser = authService.login(username, password, "CONSOLE");
        afterLogin();
    }

    private void afterLogin() {
        System.out.printf("\n[AUTHENTICATION SUCCESS] Welcome, %s! Role: %s%n",
            currentUser.getUsername(), currentUser.getRole().getRoleName());
        try {
            List<Account> accounts = accountService.getAccessibleAccounts(currentUser);
            if (!accounts.isEmpty()) {
                activeAccount = accounts.get(0);
                System.out.printf("Active Working Account set to #%d (%s, Balance: %s)%n",
                    activeAccount.getAccountNumber(), activeAccount.getAccountType(),
                    CurrencyFormatter.format(activeAccount.getBalance()));
            }
        } catch (Exception ignored) {
        }
    }

    private void handleSessionMenu() {
        Role role = currentUser.getRole();
        if (role == Role.CUSTOMER) {
            handleCustomerMenu();
        } else if (role == Role.TELLER) {
            handleTellerMenu();
        } else {
            handleAdminMenu();
        }
    }

    // =========================================================================
    // CUSTOMER MENU
    // =========================================================================
    private void handleCustomerMenu() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf("  CUSTOMER DASHBOARD — %s | Active Account: #%s%n",
            currentUser.getUsername(), activeAccount != null ? activeAccount.getAccountNumber() : "None");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("[1] View Profile & 5-Txn Mini-Statement   [5] Atomic Fund Transfer (ACID)");
        System.out.println("[2] Full Transaction History (Database)    [6] Apply 4% Yearly Interest");
        System.out.println("[3] Deposit Funds (ACID)                 [7] Close Bank Account");
        System.out.println("[4] Withdraw Funds (Polymorphic)         [8] Switch / Select Account");
        System.out.println("[0] Sign Out (Logout)");

        int choice = readInt("Select an operation (0-8): ");
        try {
            switch (choice) {
                case 1:
                    handleViewProfileAndStatement();
                    break;
                case 2:
                    handleFullTransactionHistory();
                    break;
                case 3:
                    handleCustomerDeposit();
                    break;
                case 4:
                    handleCustomerWithdraw();
                    break;
                case 5:
                    handleCustomerTransfer();
                    break;
                case 6:
                    handleCustomerInterest();
                    break;
                case 7:
                    handleCustomerClose();
                    break;
                case 8:
                    handleSwitchAccount();
                    break;
                case 0:
                    currentUser = null;
                    activeAccount = null;
                    System.out.println("[LOGOUT] Signed out successfully.");
                    break;
                default:
                    System.out.println("[WARN] Invalid option.");
            }
        } catch (InsufficientFundsException ife) {
            System.out.println("\n[TRANSACTION REJECTED] " + ife.getFormattedAlert());
        } catch (IllegalArgumentException | SecurityException | InactiveAccountException | AuthorizationException ve) {
            System.out.println("\n[SECURITY / VALIDATION ALERT] " + ve.getMessage());
        } catch (Exception se) {
            System.out.println("\n[SYSTEM / DBMS ERROR] " + se.getMessage());
        }
    }

    // =========================================================================
    // TELLER MENU
    // =========================================================================
    private void handleTellerMenu() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf("  TELLER OPERATIONS TERMINAL — %s [BRANCH STAFF]%n", currentUser.getUsername());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("[1] Open New Account (CREATE)            [5] Atomic Fund Transfer");
        System.out.println("[2] View Customer Account & Statement    [6] Apply Yearly Interest");
        System.out.println("[3] Process Customer Deposit             [7] List All Branch Accounts");
        System.out.println("[4] Process Customer Withdrawal          [0] Sign Out (Logout)");

        int choice = readInt("Select operation (0-7): ");
        try {
            switch (choice) {
                case 1:
                    handleTellerOpenAccount();
                    break;
                case 2:
                    handleTellerViewAccount();
                    break;
                case 3:
                    handleTellerDeposit();
                    break;
                case 4:
                    handleTellerWithdraw();
                    break;
                case 5:
                    handleTellerTransfer();
                    break;
                case 6:
                    handleTellerInterest();
                    break;
                case 7:
                    handleListAllAccounts();
                    break;
                case 0:
                    currentUser = null;
                    System.out.println("[LOGOUT] Teller logged out.");
                    break;
                default:
                    System.out.println("[WARN] Invalid option.");
            }
        } catch (Exception e) {
            System.out.println("\n[OPERATION ERROR] " + e.getMessage());
        }
    }

    // =========================================================================
    // ADMINISTRATOR MENU
    // =========================================================================
    private void handleAdminMenu() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.printf("  ADMINISTRATOR SUITE — %s [SYSTEM ADMIN]%n", currentUser.getUsername());
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("[1] Executive Metrics & Ledger Summary   [5] Security Audit Trail Log");
        System.out.println("[2] List All Accounts (Full Details)     [6] Open New Account");
        System.out.println("[3] Freeze / Unfreeze Account            [7] Register New Staff User");
        System.out.println("[4] User Directory & Security Unlock     [0] Sign Out (Logout)");

        int choice = readInt("Select option (0-7): ");
        try {
            switch (choice) {
                case 1:
                    handleAdminMetrics();
                    break;
                case 2:
                    handleListAllAccounts();
                    break;
                case 3:
                    handleAdminFreezeUnfreeze();
                    break;
                case 4:
                    handleAdminUserManagement();
                    break;
                case 5:
                    handleAdminAuditLogs();
                    break;
                case 6:
                    handleTellerOpenAccount();
                    break;
                case 7:
                    handleRegisterStaff();
                    break;
                case 0:
                    currentUser = null;
                    System.out.println("[LOGOUT] Administrator logged out.");
                    break;
                default:
                    System.out.println("[WARN] Invalid option.");
            }
        } catch (Exception e) {
            System.out.println("\n[ADMIN ERROR] " + e.getMessage());
        }
    }

    // =========================================================================
    // CUSTOMER WORKFLOW METHODS
    // =========================================================================
    private void handleViewProfileAndStatement() throws SQLException {
        if (activeAccount == null) {
            System.out.println("[WARN] No active account selected.");
            return;
        }
        activeAccount = bankDAO.getAccountByNumber(activeAccount.getAccountNumber());
        activeAccount.displayDetails();
        activeAccount.printMiniStatement();
    }

    private void handleFullTransactionHistory() throws Exception {
        if (activeAccount == null) {
            System.out.println("[WARN] No active account selected.");
            return;
        }
        List<Transaction> list = bankingService.getRecentTransactions(activeAccount.getAccountNumber(), 20, currentUser);
        System.out.println("\n================================================================================");
        System.out.printf("  FULL DATABASE LEDGER HISTORY (Account #%d — %d records)%n",
            activeAccount.getAccountNumber(), list.size());
        System.out.println("================================================================================");
        for (Transaction t : list) {
            System.out.println(" " + t.toFormattedString());
        }
    }

    private void handleCustomerDeposit() throws Exception {
        if (activeAccount == null) return;
        System.out.println("\n--- DEPOSIT FUNDS ---");
        System.out.printf("Target Account: #%d (%s)%n", activeAccount.getAccountNumber(), activeAccount.getHolderName());
        double amtVal = readDouble("Enter Deposit Amount (Rs.): ");
        System.out.print("Enter Remarks [optional]: ");
        String rem = scanner.nextLine().trim();

        TransactionReceipt receipt = bankingService.deposit(
            activeAccount.getAccountNumber(),
            CurrencyFormatter.toMoney(amtVal),
            rem.isEmpty() ? "Counter Deposit" : rem,
            currentUser
        );
        System.out.println("\n" + receipt.toPrintableText());
    }

    private void handleCustomerWithdraw() throws Exception {
        if (activeAccount == null) return;
        System.out.println("\n--- WITHDRAW FUNDS (POLYMORPHIC CHECK) ---");
        System.out.printf("Source Account: #%d (%s, Available Balance: %s)%n",
            activeAccount.getAccountNumber(), activeAccount.getHolderName(),
            CurrencyFormatter.format(activeAccount.getBalance()));

        System.out.print("Enter 4-Digit Security PIN: ");
        String pin = scanner.nextLine().trim();
        double amtVal = readDouble("Enter Withdrawal Amount (Rs.): ");
        System.out.print("Enter Remarks [optional]: ");
        String rem = scanner.nextLine().trim();

        TransactionReceipt receipt = bankingService.withdraw(
            activeAccount.getAccountNumber(),
            pin,
            CurrencyFormatter.toMoney(amtVal),
            rem.isEmpty() ? "ATM Withdrawal" : rem,
            currentUser
        );
        System.out.println("\n" + receipt.toPrintableText());
    }

    private void handleCustomerTransfer() throws Exception {
        if (activeAccount == null) return;
        System.out.println("\n--- ATOMIC FUND TRANSFER (ACID TRANSACTION) ---");
        System.out.printf("Sender Account: #%d (%s)%n", activeAccount.getAccountNumber(), activeAccount.getHolderName());

        System.out.print("Enter Sender 4-Digit Security PIN: ");
        String pin = scanner.nextLine().trim();
        int receiverAcc = readInt("Enter Beneficiary Account #: ");
        double amtVal = readDouble("Enter Transfer Amount (Rs.): ");
        System.out.print("Enter Transfer Remarks: ");
        String rem = scanner.nextLine().trim();

        TransactionReceipt receipt = bankingService.transfer(
            activeAccount.getAccountNumber(),
            pin,
            receiverAcc,
            CurrencyFormatter.toMoney(amtVal),
            rem.isEmpty() ? ("Transfer to #" + receiverAcc) : rem,
            currentUser
        );
        System.out.println("\n" + receipt.toPrintableText());
    }

    private void handleCustomerInterest() throws Exception {
        if (activeAccount == null) return;
        if (!(activeAccount instanceof SavingsAccount)) {
            System.out.println("[RULE REJECTED] Yearly 4% interest applies exclusively to SAVINGS accounts.");
            return;
        }
        TransactionReceipt receipt = bankingService.applyYearlyInterest(activeAccount.getAccountNumber(), currentUser);
        System.out.println("\n" + receipt.toPrintableText());
    }

    private void handleCustomerClose() throws Exception {
        if (activeAccount == null) return;
        System.out.println("\n--- CLOSE BANK ACCOUNT ---");
        System.out.print("Enter 4-Digit PIN to authorize account closure: ");
        String pin = scanner.nextLine().trim();

        boolean closed = accountService.closeAccount(activeAccount.getAccountNumber(), pin, currentUser);
        if (closed) {
            System.out.printf("[ACCOUNT CLOSED] Account #%d was closed safely. Financial audit trail preserved.%n",
                activeAccount.getAccountNumber());
            activeAccount = null;
        }
    }

    private void handleSwitchAccount() throws Exception {
        List<Account> accounts = accountService.getAccessibleAccounts(currentUser);
        System.out.println("\n--- SELECT ACTIVE ACCOUNT ---");
        for (int i = 0; i < accounts.size(); i++) {
            Account a = accounts.get(i);
            System.out.printf("[%d] Account #%d (%s, %s, %s)%n",
                i + 1, a.getAccountNumber(), a.getHolderName(), a.getAccountType(), CurrencyFormatter.format(a.getBalance()));
        }
        int sel = readInt("Choose account (1-" + accounts.size() + "): ");
        if (sel >= 1 && sel <= accounts.size()) {
            activeAccount = accounts.get(sel - 1);
            System.out.println("[SELECTED] Active account: #" + activeAccount.getAccountNumber());
        }
    }

    // =========================================================================
    // TELLER WORKFLOW METHODS
    // =========================================================================
    private void handleTellerOpenAccount() throws Exception {
        System.out.println("\n--- OPEN NEW BANK ACCOUNT (CREATE) ---");
        System.out.print("Enter Customer ID (e.g. 501): ");
        int custId = readInt("");
        System.out.print("Enter Account Holder Full Name: ");
        String holderName = scanner.nextLine().trim();

        System.out.print("Select Account Type (1 = SAVINGS [Min Rs. 1,000], 2 = CURRENT [OD Rs. 5,000]): ");
        int typeChoice = readInt("");
        AccountType type = (typeChoice == 1) ? AccountType.SAVINGS : AccountType.CURRENT;

        double openingDeposit = readDouble("Enter Opening Deposit Amount (Rs.): ");
        System.out.print("Set 4-Digit Security PIN: ");
        String pin = scanner.nextLine().trim();

        Account acc = accountService.createAccount(
            custId,
            holderName,
            type,
            CurrencyFormatter.toMoney(openingDeposit),
            pin,
            currentUser
        );
        System.out.printf("[SUCCESS] %s Account created with Account Number #%d (Balance: %s)%n",
            acc.getAccountType(), acc.getAccountNumber(), CurrencyFormatter.format(acc.getBalance()));
    }

    private void handleTellerViewAccount() throws Exception {
        int accNo = readInt("Enter Account Number (e.g. 1001): ");
        Account acc = accountService.getAccount(accNo, currentUser);
        acc.displayDetails();
        acc.printMiniStatement();
    }

    private void handleTellerDeposit() throws Exception {
        int accNo = readInt("Enter Target Account Number: ");
        double amt = readDouble("Enter Deposit Amount (Rs.): ");
        System.out.print("Enter Remarks: ");
        String rem = scanner.nextLine().trim();

        TransactionReceipt receipt = bankingService.deposit(
            accNo,
            CurrencyFormatter.toMoney(amt),
            rem.isEmpty() ? "Counter Deposit" : rem,
            currentUser
        );
        System.out.println("\n" + receipt.toPrintableText());
    }

    private void handleTellerWithdraw() throws Exception {
        int accNo = readInt("Enter Source Account Number: ");
        System.out.print("Enter Customer 4-Digit PIN: ");
        String pin = scanner.nextLine().trim();
        double amt = readDouble("Enter Withdrawal Amount (Rs.): ");
        System.out.print("Enter Remarks: ");
        String rem = scanner.nextLine().trim();

        TransactionReceipt receipt = bankingService.withdraw(
            accNo,
            pin,
            CurrencyFormatter.toMoney(amt),
            rem.isEmpty() ? "Counter Withdrawal" : rem,
            currentUser
        );
        System.out.println("\n" + receipt.toPrintableText());
    }

    private void handleTellerTransfer() throws Exception {
        int sender = readInt("Enter Sender Account Number: ");
        System.out.print("Enter Sender 4-Digit PIN: ");
        String pin = scanner.nextLine().trim();
        int receiver = readInt("Enter Beneficiary Account Number: ");
        double amt = readDouble("Enter Transfer Amount (Rs.): ");
        System.out.print("Enter Remarks: ");
        String rem = scanner.nextLine().trim();

        TransactionReceipt receipt = bankingService.transfer(
            sender,
            pin,
            receiver,
            CurrencyFormatter.toMoney(amt),
            rem.isEmpty() ? ("Counter Transfer to #" + receiver) : rem,
            currentUser
        );
        System.out.println("\n" + receipt.toPrintableText());
    }

    private void handleTellerInterest() throws Exception {
        int accNo = readInt("Enter Savings Account Number: ");
        TransactionReceipt receipt = bankingService.applyYearlyInterest(accNo, currentUser);
        System.out.println("\n" + receipt.toPrintableText());
    }

    private void handleListAllAccounts() throws Exception {
        List<Account> all = bankDAO.getAllAccounts();
        System.out.println("\n================================================================================");
        System.out.println("  SECUREBANK LEDGER — ALL REGISTERED ACCOUNTS (" + all.size() + ")");
        System.out.println("================================================================================");
        for (Account acc : all) {
            acc.displayDetails();
        }
    }

    // =========================================================================
    // ADMIN WORKFLOW METHODS
    // =========================================================================
    private void handleAdminMetrics() throws Exception {
        List<Account> all = bankDAO.getAllAccounts();
        BigDecimal totalDeposits = BigDecimal.ZERO;
        int active = 0, locked = 0, closed = 0, frozen = 0;

        for (Account a : all) {
            if (a.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                totalDeposits = totalDeposits.add(a.getBalance());
            }
            if (a.getStatus() == AccountStatus.ACTIVE) active++;
            else if (a.getStatus() == AccountStatus.LOCKED) locked++;
            else if (a.getStatus() == AccountStatus.FROZEN) frozen++;
            else if (a.getStatus() == AccountStatus.CLOSED) closed++;
        }

        System.out.println("\n================================================================================");
        System.out.println("  EXECUTIVE LEDGER METRICS & AUDIT OVERVIEW");
        System.out.println("================================================================================");
        System.out.printf("  Total Accounts Managed   : %d%n", all.size());
        System.out.printf("  Active Accounts          : %d%n", active);
        System.out.printf("  Security Locked Accounts : %d%n", locked);
        System.out.printf("  Administratively Frozen  : %d%n", frozen);
        System.out.printf("  Closed Accounts (Ledger) : %d%n", closed);
        System.out.printf("  Total Deposits Under Care: %s%n", CurrencyFormatter.format(totalDeposits));
        System.out.println("================================================================================");
    }

    private void handleAdminFreezeUnfreeze() throws Exception {
        int accNo = readInt("Enter Account Number to change status: ");
        System.out.println("Select New Status: [1] ACTIVE, [2] FROZEN, [3] LOCKED, [4] CLOSED");
        int stChoice = readInt("Choice: ");
        AccountStatus st;
        switch (stChoice) {
            case 1: st = AccountStatus.ACTIVE; break;
            case 2: st = AccountStatus.FROZEN; break;
            case 3: st = AccountStatus.LOCKED; break;
            case 4: st = AccountStatus.CLOSED; break;
            default: return;
        }
        accountService.updateStatus(accNo, st, currentUser);
        System.out.printf("[SUCCESS] Account #%d status updated to %s%n", accNo, st);
    }

    private void handleAdminUserManagement() throws Exception {
        List<User> users = userDAO.getAllUsers();
        System.out.println("\n================================================================================");
        System.out.println("  SYSTEM USERS & SECURITY LOCKOUT CONTROL (" + users.size() + ")");
        System.out.println("================================================================================");
        for (User u : users) {
            System.out.printf("  ID: %-4d | User: %-15s | Role: %-10s | Locked: %-5s | Fails: %d/3 | Active: %s%n",
                u.getUserId(), u.getUsername(), u.getRole(), u.isLocked() ? "YES" : "NO",
                u.getFailedLogins(), u.isActive() ? "YES" : "NO");
        }
        System.out.print("\nUnlock a user account? Enter User ID (or 0 to skip): ");
        int uid = readInt("");
        if (uid > 0) {
            authService.unlockUser(uid, currentUser);
            System.out.println("[SUCCESS] User #" + uid + " unlocked successfully.");
        }
    }

    private void handleAdminAuditLogs() throws Exception {
        List<AuditLog> list = auditDAO.getRecentAuditLogs(25);
        System.out.println("\n================================================================================");
        System.out.println("  IMMUTABLE SECURITY AUDIT TRAIL (Last 25 Events)");
        System.out.println("================================================================================");
        for (AuditLog al : list) {
            System.out.printf("[%s] %-14s | Actor: %-12s | Status: %-8s | %s%n",
                al.getFormattedTimestamp(), al.getAction(), al.getActorUsername(), al.getStatus(), al.getDetails());
        }
    }

    private void handleRegisterStaff() throws Exception {
        System.out.println("\n--- ONBOARD NEW BANK STAFF ---");
        System.out.print("Enter New Staff Username: ");
        String username = scanner.nextLine().trim();
        System.out.print("Enter Temporary Password (min 6 chars): ");
        String pass = scanner.nextLine().trim();
        System.out.print("Role [1 = TELLER, 2 = ADMIN]: ");
        int r = readInt("");
        Role role = (r == 2) ? Role.ADMIN : Role.TELLER;

        User created = authService.registerUser(username, pass, role, currentUser.getUsername());
        System.out.printf("[STAFF ONBOARDED] User #%d '%s' registered with role %s%n",
            created.getUserId(), created.getUsername(), created.getRole());
    }

    private void printBanner() {
        System.out.println("================================================================================");
        System.out.println("     SECUREBANK — ROLE-BASED CORE BANKING & TRANSACTION PLATFORM");
        System.out.println("       Java OOP Architecture | ACID MySQL/H2 Persistence | BCrypt RBAC");
        System.out.println("================================================================================");
    }

    private int readInt(String prompt) {
        while (true) {
            if (!prompt.isEmpty()) {
                System.out.print(prompt);
            }
            if (!scanner.hasNextLine()) {
                return 0;
            }
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.print("Please enter a valid integer: ");
            }
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!scanner.hasNextLine()) {
                return 0.0;
            }
            String line = scanner.nextLine().trim();
            try {
                return Double.parseDouble(line);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid numeric amount.");
            }
        }
    }
}
