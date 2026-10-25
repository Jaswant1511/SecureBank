package com.securebank.ui.gui;

import com.securebank.dao.AuditDAO;
import com.securebank.dao.BankDAO;
import com.securebank.dao.CustomerDAO;
import com.securebank.dao.TransactionDAO;
import com.securebank.dao.UserDAO;
import com.securebank.exception.AuthenticationException;
import com.securebank.model.Account;
import com.securebank.model.AccountStatus;
import com.securebank.model.AccountType;
import com.securebank.model.AuditLog;
import com.securebank.model.CurrentAccount;
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
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * SecureBank: Role-Based Core Banking & Transaction Management System
 * File: BankGuiApp.java
 *
 * Comprehensive Professional Desktop Application (Java Swing).
 * Implements:
 *  - Modern responsive card layout
 *  - Role-specific dynamic workspaces (Customer, Teller, Administrator)
 *  - Interactive deposit, withdrawal, atomic transfer, and interest wizards
 *  - Official Transaction Receipt popups
 *  - Searchable transaction and audit logs
 */
public class BankGuiApp extends JFrame {

    private static final long serialVersionUID = 1L;

    private final AuthService authService;
    private final AccountService accountService;
    private final BankingService bankingService;
    private final BankDAO bankDAO;
    private final UserDAO userDAO;
    private final CustomerDAO customerDAO;
    private final AuditDAO auditDAO;

    private User currentUser;
    private Account currentAccount;

    // Layout
    private final CardLayout cardLayout;
    private final JPanel mainContainer;

    // View Identifiers
    private static final String VIEW_LOGIN = "LOGIN";
    private static final String VIEW_CUSTOMER = "CUSTOMER";
    private static final String VIEW_TELLER = "TELLER";
    private static final String VIEW_ADMIN = "ADMIN";

    // Sub-panels
    private JPanel loginPanel;
    private JPanel customerPanel;
    private JPanel tellerPanel;
    private JPanel adminPanel;

    public BankGuiApp() {
        super("SecureBank — Core Banking & Transaction Management Platform");

        // Ensure database initialized (MySQL or embedded H2)
        DatabaseConfig.initializeSchema();

        this.bankDAO = new BankDAO();
        this.userDAO = new UserDAO();
        this.customerDAO = new CustomerDAO();
        this.auditDAO = new AuditDAO();
        this.authService = new AuthService(userDAO, auditDAO);
        this.accountService = new AccountService(bankDAO, customerDAO, auditDAO);
        this.bankingService = new BankingService(bankDAO, new TransactionDAO(), customerDAO, auditDAO);

        this.cardLayout = new CardLayout();
        this.mainContainer = new JPanel(cardLayout);

        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(950, 620));
        setLocationRelativeTo(null);

        loginPanel = buildLoginPanel();
        mainContainer.add(loginPanel, VIEW_LOGIN);

        add(mainContainer);
        cardLayout.show(mainContainer, VIEW_LOGIN);
    }

    // =========================================================================
    // 1. LOGIN VIEW
    // =========================================================================
    private JPanel buildLoginPanel() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(ThemeManager.BG_LIGHT);

        JPanel card = ThemeManager.createCardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(460, 560));

        // Header Title
        JLabel brand = new JLabel("SECUREBANK", SwingConstants.CENTER);
        brand.setFont(new Font("Segoe UI", Font.BOLD, 26));
        brand.setForeground(ThemeManager.PRIMARY_NAVY);
        brand.setAlignmentX(CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Core Banking & Transaction Platform", SwingConstants.CENTER);
        subtitle.setFont(ThemeManager.FONT_BODY);
        subtitle.setForeground(ThemeManager.TEXT_MUTED);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);

        JLabel lockNotice = new JLabel("Protected with Salted BCrypt & 3-Attempt Lockout", SwingConstants.CENTER);
        lockNotice.setFont(ThemeManager.FONT_SMALL);
        lockNotice.setForeground(ThemeManager.ACCENT_BLUE);
        lockNotice.setAlignmentX(CENTER_ALIGNMENT);

        // Fields
        JTextField usernameField = new JTextField(20);
        usernameField.setFont(ThemeManager.FONT_BODY);
        JPasswordField passwordField = new JPasswordField(20);
        passwordField.setFont(ThemeManager.FONT_BODY);

        JLabel userLabel = new JLabel("Username:");
        userLabel.setFont(ThemeManager.FONT_BOLD);
        JLabel passLabel = new JLabel("Password:");
        passLabel.setFont(ThemeManager.FONT_BOLD);

        JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(ThemeManager.FONT_SMALL);
        statusLabel.setForeground(ThemeManager.DANGER_RED);
        statusLabel.setAlignmentX(CENTER_ALIGNMENT);

        JButton loginBtn = ThemeManager.createPrimaryButton("Secure Sign In");
        loginBtn.setAlignmentX(CENTER_ALIGNMENT);
        loginBtn.setMaximumSize(new Dimension(380, 42));

        loginBtn.addActionListener(e -> {
            String u = usernameField.getText().trim();
            String p = new String(passwordField.getPassword());
            try {
                User user = authService.login(u, p, "127.0.0.1 (GUI)");
                this.currentUser = user;
                statusLabel.setText(" ");
                routeToDashboard();
            } catch (AuthenticationException ex) {
                statusLabel.setText(ex.getMessage());
            }
        });

        // Demo Quick-Login Helpers
        JLabel demoLabel = new JLabel("Demo One-Click Sign In (Development Preview):", SwingConstants.CENTER);
        demoLabel.setFont(ThemeManager.FONT_SMALL);
        demoLabel.setForeground(ThemeManager.TEXT_MUTED);
        demoLabel.setAlignmentX(CENTER_ALIGNMENT);

        JPanel demoBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        demoBtns.setOpaque(false);

        JButton demoCust = ThemeManager.createSecondaryButton("Customer (Aarav)");
        demoCust.setFont(ThemeManager.FONT_SMALL);
        demoCust.addActionListener(ev -> {
            usernameField.setText("aarav");
            passwordField.setText("Password@123");
            loginBtn.doClick();
        });

        JButton demoTeller = ThemeManager.createSecondaryButton("Teller (Suresh)");
        demoTeller.setFont(ThemeManager.FONT_SMALL);
        demoTeller.addActionListener(ev -> {
            usernameField.setText("teller_suresh");
            passwordField.setText("Teller@123");
            loginBtn.doClick();
        });

        JButton demoAdmin = ThemeManager.createSecondaryButton("Admin (Rajesh)");
        demoAdmin.setFont(ThemeManager.FONT_SMALL);
        demoAdmin.addActionListener(ev -> {
            usernameField.setText("admin_rajesh");
            passwordField.setText("Admin@123");
            loginBtn.doClick();
        });

        demoBtns.add(demoCust);
        demoBtns.add(demoTeller);
        demoBtns.add(demoAdmin);

        // Assemble
        card.add(Box.createVerticalStrut(10));
        card.add(brand);
        card.add(Box.createVerticalStrut(4));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(6));
        card.add(lockNotice);
        card.add(Box.createVerticalStrut(25));

        JPanel form = new JPanel(new GridLayout(4, 1, 6, 6));
        form.setOpaque(false);
        form.setMaximumSize(new Dimension(380, 160));
        form.add(userLabel);
        form.add(usernameField);
        form.add(passLabel);
        form.add(passwordField);

        card.add(form);
        card.add(Box.createVerticalStrut(10));
        card.add(statusLabel);
        card.add(Box.createVerticalStrut(10));
        card.add(loginBtn);
        card.add(Box.createVerticalStrut(25));
        card.add(demoLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(demoBtns);

        root.add(card);
        return root;
    }

    private void routeToDashboard() {
        if (currentUser == null) {
            return;
        }
        if (currentUser.getRole() == Role.CUSTOMER) {
            customerPanel = buildCustomerDashboard();
            mainContainer.add(customerPanel, VIEW_CUSTOMER);
            cardLayout.show(mainContainer, VIEW_CUSTOMER);
        } else if (currentUser.getRole() == Role.TELLER) {
            tellerPanel = buildTellerDashboard();
            mainContainer.add(tellerPanel, VIEW_TELLER);
            cardLayout.show(mainContainer, VIEW_TELLER);
        } else if (currentUser.getRole() == Role.ADMIN) {
            adminPanel = buildAdminDashboard();
            mainContainer.add(adminPanel, VIEW_ADMIN);
            cardLayout.show(mainContainer, VIEW_ADMIN);
        }
    }

    private void logout() {
        this.currentUser = null;
        this.currentAccount = null;
        cardLayout.show(mainContainer, VIEW_LOGIN);
    }

    // =========================================================================
    // 2. CUSTOMER DASHBOARD
    // =========================================================================
    private JPanel buildCustomerDashboard() {
        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBackground(ThemeManager.BG_LIGHT);
        root.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));

        // Top Navigation Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JLabel welcome = new JLabel("Welcome back, " + currentUser.getUsername() + " [CUSTOMER]");
        welcome.setFont(ThemeManager.FONT_TITLE);
        welcome.setForeground(ThemeManager.PRIMARY_NAVY);

        JButton logoutBtn = ThemeManager.createSecondaryButton("Sign Out");
        logoutBtn.addActionListener(e -> logout());

        topBar.add(welcome, BorderLayout.WEST);
        topBar.add(logoutBtn, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // Center Content
        JPanel center = new JPanel(new BorderLayout(15, 15));
        center.setOpaque(false);

        // Account Summary Card
        JPanel summaryCard = ThemeManager.createCardPanel();
        summaryCard.setLayout(new GridLayout(2, 3, 15, 10));

        JLabel accNoLbl = new JLabel("Loading account...");
        accNoLbl.setFont(ThemeManager.FONT_BOLD);
        JLabel balLbl = new JLabel("Rs. 0.00");
        balLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        balLbl.setForeground(ThemeManager.ACCENT_BLUE);
        JLabel ruleLbl = new JLabel("-");
        ruleLbl.setFont(ThemeManager.FONT_BODY);
        JLabel statusLbl = new JLabel("Status: -");
        statusLbl.setFont(ThemeManager.FONT_BOLD);
        JLabel withdrawableLbl = new JLabel("Available: Rs. 0.00");
        withdrawableLbl.setFont(ThemeManager.FONT_BODY);

        JComboBox<String> accountSelector = new JComboBox<>();
        accountSelector.setFont(ThemeManager.FONT_BOLD);

        summaryCard.add(new JLabel("Selected Account:"));
        summaryCard.add(new JLabel("Authoritative Balance:"));
        summaryCard.add(new JLabel("Policy Rule:"));
        summaryCard.add(accountSelector);
        summaryCard.add(balLbl);
        summaryCard.add(ruleLbl);

        // Refresh accounts for customer
        Runnable refreshAccounts = () -> {
            try {
                List<Account> accounts = accountService.getAccessibleAccounts(currentUser);
                accountSelector.removeAllItems();
                for (Account a : accounts) {
                    accountSelector.addItem("#" + a.getAccountNumber() + " - " + a.getHolderName() + " (" + a.getAccountType() + ")");
                }
                if (!accounts.isEmpty()) {
                    currentAccount = accounts.get(0);
                    updateAccountDisplay(currentAccount, balLbl, ruleLbl, statusLbl, withdrawableLbl);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed loading accounts: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        accountSelector.addActionListener(e -> {
            String selected = (String) accountSelector.getSelectedItem();
            if (selected != null && selected.startsWith("#")) {
                int accNo = Integer.parseInt(selected.substring(1, selected.indexOf(" ")));
                try {
                    currentAccount = bankDAO.getAccountByNumber(accNo);
                    updateAccountDisplay(currentAccount, balLbl, ruleLbl, statusLbl, withdrawableLbl);
                } catch (Exception ignored) {
                }
            }
        });

        // Quick Financial Actions Panel
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        actionPanel.setOpaque(false);

        JButton depositBtn = ThemeManager.createPrimaryButton("Deposit");
        depositBtn.addActionListener(e -> showDepositDialog(refreshAccounts));

        JButton withdrawBtn = ThemeManager.createPrimaryButton("Withdraw");
        withdrawBtn.addActionListener(e -> showWithdrawDialog(refreshAccounts));

        JButton transferBtn = ThemeManager.createPrimaryButton("Transfer Funds");
        transferBtn.addActionListener(e -> showTransferDialog(refreshAccounts));

        JButton interestBtn = ThemeManager.createSuccessButton("Apply 4% Interest");
        interestBtn.addActionListener(e -> showInterestDialog(refreshAccounts));

        JButton miniStatementBtn = ThemeManager.createSecondaryButton("Mini-Statement (Buffer)");
        miniStatementBtn.addActionListener(e -> showMiniStatementDialog());

        JButton closeAccBtn = ThemeManager.createDangerButton("Close Account");
        closeAccBtn.addActionListener(e -> showCloseAccountDialog(refreshAccounts));

        actionPanel.add(depositBtn);
        actionPanel.add(withdrawBtn);
        actionPanel.add(transferBtn);
        actionPanel.add(interestBtn);
        actionPanel.add(miniStatementBtn);
        actionPanel.add(closeAccBtn);

        JPanel topGroup = new JPanel(new BorderLayout(8, 8));
        topGroup.setOpaque(false);
        topGroup.add(summaryCard, BorderLayout.CENTER);
        topGroup.add(actionPanel, BorderLayout.SOUTH);
        center.add(topGroup, BorderLayout.NORTH);

        // Ledger Transactions Table
        DefaultTableModel txnModel = new DefaultTableModel(
            new String[]{"ID", "Reference", "Type", "Amount", "Balance After", "Description", "Timestamp"}, 0
        ) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        JTable txnTable = new JTable(txnModel);
        ThemeManager.styleTable(txnTable);
        JScrollPane tableScroll = new JScrollPane(txnTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR));

        JPanel tableCard = ThemeManager.createCardPanel();
        tableCard.setLayout(new BorderLayout(8, 8));
        JLabel tableHeader = new JLabel("Official Financial Ledger Activity (Real-Time Database Records)");
        tableHeader.setFont(ThemeManager.FONT_SUBTITLE);
        tableCard.add(tableHeader, BorderLayout.NORTH);
        tableCard.add(tableScroll, BorderLayout.CENTER);

        center.add(tableCard, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);

        // Initial populate
        refreshAccounts.run();
        populateTxnTable(txnModel);

        return root;
    }

    private void updateAccountDisplay(Account acc, JLabel bal, JLabel rule, JLabel status, JLabel withdrawable) {
        if (acc == null) {
            return;
        }
        bal.setText(CurrencyFormatter.format(acc.getBalance()));
        if (acc instanceof SavingsAccount) {
            rule.setText("Min Balance: " + CurrencyFormatter.format(SavingsAccount.MIN_BALANCE) + " | 4% Yearly Interest");
        } else if (acc instanceof CurrentAccount) {
            CurrentAccount ca = (CurrentAccount) acc;
            rule.setText("Overdraft: " + CurrencyFormatter.format(CurrentAccount.OVERDRAFT_LIMIT) + " (Used: " + CurrencyFormatter.format(ca.getOverdraftUsed()) + ")");
        }
        status.setText("Status: " + acc.getStatus());
    }

    private void populateTxnTable(DefaultTableModel model) {
        if (currentAccount == null) {
            return;
        }
        try {
            model.setRowCount(0);
            List<Transaction> list = bankingService.getRecentTransactions(currentAccount.getAccountNumber(), 50, currentUser);
            for (Transaction t : list) {
                model.addRow(new Object[]{
                    t.getTransactionId(),
                    t.getTransactionRef(),
                    t.getTxnType(),
                    CurrencyFormatter.format(t.getAmount()),
                    CurrencyFormatter.format(t.getBalanceAfter()),
                    t.getRemarks(),
                    t.getFormattedTimestamp()
                });
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // 3. TELLER DASHBOARD
    // =========================================================================
    private JPanel buildTellerDashboard() {
        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBackground(ThemeManager.BG_LIGHT);
        root.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));

        // Header
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel title = new JLabel("Branch Operations & Teller Terminal — " + currentUser.getUsername());
        title.setFont(ThemeManager.FONT_TITLE);
        title.setForeground(ThemeManager.PRIMARY_NAVY);
        JButton logoutBtn = ThemeManager.createSecondaryButton("Sign Out");
        logoutBtn.addActionListener(e -> logout());
        topBar.add(title, BorderLayout.WEST);
        topBar.add(logoutBtn, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(ThemeManager.FONT_BOLD);

        // Tab 1: Open Account
        JPanel openAccTab = buildOpenAccountTab();
        tabs.addTab("Open New Customer Account", openAccTab);

        // Tab 2: Counter Deposit / Withdraw
        JPanel counterOpsTab = buildCounterOpsTab();
        tabs.addTab("Counter Transactions", counterOpsTab);

        // Tab 3: Customer Lookup
        JPanel lookupTab = buildCustomerLookupTab();
        tabs.addTab("Customer & Account Directory", lookupTab);

        root.add(tabs, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildOpenAccountTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(ThemeManager.BG_LIGHT);

        JPanel card = ThemeManager.createCardPanel();
        card.setLayout(new GridLayout(6, 2, 12, 12));
        card.setPreferredSize(new Dimension(540, 320));

        JTextField nameField = new JTextField();
        JComboBox<String> typeBox = new JComboBox<>(new String[]{"SAVINGS (Min Balance Rs. 1,000.00)", "CURRENT (Overdraft Rs. 5,000.00)"});
        JTextField depositField = new JTextField("1000.00");
        JPasswordField pinField = new JPasswordField();
        JTextField custIdField = new JTextField("501");

        JButton createBtn = ThemeManager.createPrimaryButton("Create Verified Account");
        createBtn.addActionListener(e -> {
            try {
                String name = nameField.getText().trim();
                AccountType type = typeBox.getSelectedIndex() == 0 ? AccountType.SAVINGS : AccountType.CURRENT;
                BigDecimal deposit = CurrencyFormatter.parsePositiveMoney(depositField.getText());
                String pin = new String(pinField.getPassword()).trim();
                int custId = Integer.parseInt(custIdField.getText().trim());

                Account acc = accountService.createAccount(custId, name, type, deposit, pin, currentUser);
                JOptionPane.showMessageDialog(this,
                    String.format("Account #%d successfully created!%nHolder: %s%nOpening Balance: %s",
                        acc.getAccountNumber(), acc.getHolderName(), CurrencyFormatter.format(acc.getBalance())),
                    "Account Created", JOptionPane.INFORMATION_MESSAGE);
                nameField.setText("");
                pinField.setText("");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Account Creation Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        card.add(new JLabel("Customer ID:"));
        card.add(custIdField);
        card.add(new JLabel("Account Holder Full Name:"));
        card.add(nameField);
        card.add(new JLabel("Account Category:"));
        card.add(typeBox);
        card.add(new JLabel("Opening Deposit (Rs.):"));
        card.add(depositField);
        card.add(new JLabel("Set 4-Digit Security PIN:"));
        card.add(pinField);
        card.add(new JLabel(""));
        card.add(createBtn);

        p.add(card);
        return p;
    }

    private JPanel buildCounterOpsTab() {
        JPanel root = new JPanel(new GridLayout(1, 2, 15, 15));
        root.setBackground(ThemeManager.BG_LIGHT);
        root.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Deposit Card
        JPanel depCard = ThemeManager.createCardPanel();
        depCard.setLayout(new GridLayout(5, 2, 8, 8));
        depCard.add(new JLabel("COUNTER DEPOSIT"));
        depCard.add(new JLabel(""));
        JTextField dAcc = new JTextField();
        JTextField dAmt = new JTextField();
        JTextField dRem = new JTextField("Counter Cash Deposit");
        depCard.add(new JLabel("Target Account #:"));
        depCard.add(dAcc);
        depCard.add(new JLabel("Deposit Amount (Rs.):"));
        depCard.add(dAmt);
        depCard.add(new JLabel("Description:"));
        depCard.add(dRem);
        JButton dBtn = ThemeManager.createPrimaryButton("Process Deposit");
        dBtn.addActionListener(e -> {
            try {
                int acc = Integer.parseInt(dAcc.getText().trim());
                BigDecimal amt = CurrencyFormatter.parsePositiveMoney(dAmt.getText());
                TransactionReceipt rec = bankingService.deposit(acc, amt, dRem.getText(), currentUser);
                showReceiptDialog(rec);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Deposit Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        depCard.add(new JLabel(""));
        depCard.add(dBtn);

        // Withdraw Card
        JPanel withCard = ThemeManager.createCardPanel();
        withCard.setLayout(new GridLayout(6, 2, 8, 8));
        withCard.add(new JLabel("COUNTER WITHDRAWAL"));
        withCard.add(new JLabel(""));
        JTextField wAcc = new JTextField();
        JPasswordField wPin = new JPasswordField();
        JTextField wAmt = new JTextField();
        JTextField wRem = new JTextField("Counter Withdrawal");
        withCard.add(new JLabel("Account #:"));
        withCard.add(wAcc);
        withCard.add(new JLabel("Customer PIN:"));
        withCard.add(wPin);
        withCard.add(new JLabel("Withdrawal Amount (Rs.):"));
        withCard.add(wAmt);
        withCard.add(new JLabel("Description:"));
        withCard.add(wRem);
        JButton wBtn = ThemeManager.createPrimaryButton("Process Withdrawal");
        wBtn.addActionListener(e -> {
            try {
                int acc = Integer.parseInt(wAcc.getText().trim());
                String pin = new String(wPin.getPassword()).trim();
                BigDecimal amt = CurrencyFormatter.parsePositiveMoney(wAmt.getText());
                TransactionReceipt rec = bankingService.withdraw(acc, pin, amt, wRem.getText(), currentUser);
                showReceiptDialog(rec);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Withdrawal Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        withCard.add(new JLabel(""));
        withCard.add(wBtn);

        root.add(depCard);
        root.add(withCard);
        return root;
    }

    private JPanel buildCustomerLookupTab() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(ThemeManager.BG_LIGHT);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultTableModel model = new DefaultTableModel(
            new String[]{"Account #", "Customer ID", "Holder Name", "Type", "Balance", "Failed Attempts", "Status"}, 0
        ) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(model);
        ThemeManager.styleTable(table);

        JButton refreshBtn = ThemeManager.createSecondaryButton("Refresh Directory");
        refreshBtn.addActionListener(e -> {
            try {
                model.setRowCount(0);
                List<Account> all = bankDAO.getAllAccounts();
                for (Account a : all) {
                    model.addRow(new Object[]{
                        a.getAccountNumber(),
                        a.getCustomerId(),
                        a.getHolderName(),
                        a.getAccountType(),
                        CurrencyFormatter.format(a.getBalance()),
                        a.getFailedAttempts() + "/3",
                        a.getStatus()
                    });
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        });

        p.add(refreshBtn, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        refreshBtn.doClick();
        return p;
    }

    // =========================================================================
    // 4. ADMINISTRATOR DASHBOARD
    // =========================================================================
    private JPanel buildAdminDashboard() {
        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBackground(ThemeManager.BG_LIGHT);
        root.setBorder(BorderFactory.createEmptyBorder(15, 20, 20, 20));

        // Header
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JLabel title = new JLabel("System Administration & Audit Suite — " + currentUser.getUsername());
        title.setFont(ThemeManager.FONT_TITLE);
        title.setForeground(ThemeManager.PRIMARY_NAVY);
        JButton logoutBtn = ThemeManager.createSecondaryButton("Sign Out");
        logoutBtn.addActionListener(e -> logout());
        topBar.add(title, BorderLayout.WEST);
        topBar.add(logoutBtn, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(ThemeManager.FONT_BOLD);

        // Tab 1: Overview & Metrics
        JPanel metricsTab = buildAdminMetricsTab();
        tabs.addTab("Executive Metrics", metricsTab);

        // Tab 2: User Security & Roles
        JPanel userMgmtTab = buildAdminUserManagementTab();
        tabs.addTab("User & Role Control", userMgmtTab);

        // Tab 3: Security Audit Trail
        JPanel auditTab = buildAdminAuditTab();
        tabs.addTab("Security Audit Log", auditTab);

        root.add(tabs, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildAdminMetricsTab() {
        JPanel root = new JPanel(new BorderLayout(15, 15));
        root.setBackground(ThemeManager.BG_LIGHT);
        root.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel cardsGrid = new JPanel(new GridLayout(1, 3, 15, 15));
        cardsGrid.setOpaque(false);

        JPanel c1 = ThemeManager.createCardPanel();
        c1.setLayout(new GridLayout(2, 1));
        JLabel totalAccsLbl = new JLabel("0", SwingConstants.CENTER);
        totalAccsLbl.setFont(new Font("Segoe UI", Font.BOLD, 28));
        totalAccsLbl.setForeground(ThemeManager.ACCENT_BLUE);
        c1.add(new JLabel("TOTAL REGISTERED ACCOUNTS", SwingConstants.CENTER));
        c1.add(totalAccsLbl);

        JPanel c2 = ThemeManager.createCardPanel();
        c2.setLayout(new GridLayout(2, 1));
        JLabel totalBalLbl = new JLabel("Rs. 0.00", SwingConstants.CENTER);
        totalBalLbl.setFont(new Font("Segoe UI", Font.BOLD, 24));
        totalBalLbl.setForeground(ThemeManager.SUCCESS_GREEN);
        c2.add(new JLabel("TOTAL SYSTEM DEPOSITS", SwingConstants.CENTER));
        c2.add(totalBalLbl);

        JPanel c3 = ThemeManager.createCardPanel();
        c3.setLayout(new GridLayout(2, 1));
        JLabel lockedLbl = new JLabel("0", SwingConstants.CENTER);
        lockedLbl.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lockedLbl.setForeground(ThemeManager.DANGER_RED);
        c3.add(new JLabel("SECURITY LOCKED ACCOUNTS", SwingConstants.CENTER));
        c3.add(lockedLbl);

        cardsGrid.add(c1);
        cardsGrid.add(c2);
        cardsGrid.add(c3);
        root.add(cardsGrid, BorderLayout.NORTH);

        // Accounts Management Table
        DefaultTableModel model = new DefaultTableModel(
            new String[]{"Account #", "Customer", "Type", "Balance", "Status", "Action"}, 0
        ) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(model);
        ThemeManager.styleTable(table);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setOpaque(false);
        JButton freezeBtn = ThemeManager.createSecondaryButton("Freeze Selected");
        freezeBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                int accNo = (int) table.getValueAt(row, 0);
                try {
                    accountService.updateStatus(accNo, AccountStatus.FROZEN, currentUser);
                    JOptionPane.showMessageDialog(this, "Account #" + accNo + " is now FROZEN.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            }
        });

        JButton activateBtn = ThemeManager.createSuccessButton("Activate / Unfreeze Selected");
        activateBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                int accNo = (int) table.getValueAt(row, 0);
                try {
                    accountService.updateStatus(accNo, AccountStatus.ACTIVE, currentUser);
                    JOptionPane.showMessageDialog(this, "Account #" + accNo + " is now ACTIVE.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            }
        });

        bottom.add(freezeBtn);
        bottom.add(activateBtn);

        root.add(new JScrollPane(table), BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);

        // Load metrics
        try {
            List<Account> all = bankDAO.getAllAccounts();
            totalAccsLbl.setText(String.valueOf(all.size()));
            BigDecimal total = BigDecimal.ZERO;
            int locked = 0;
            for (Account a : all) {
                if (a.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    total = total.add(a.getBalance());
                }
                if (a.getStatus() == AccountStatus.LOCKED || a.isLocked()) {
                    locked++;
                }
                model.addRow(new Object[]{
                    a.getAccountNumber(),
                    a.getHolderName(),
                    a.getAccountType(),
                    CurrencyFormatter.format(a.getBalance()),
                    a.getStatus(),
                    "Manage"
                });
            }
            totalBalLbl.setText(CurrencyFormatter.format(total));
            lockedLbl.setText(String.valueOf(locked));
        } catch (Exception ignored) {
        }

        return root;
    }

    private JPanel buildAdminUserManagementTab() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(ThemeManager.BG_LIGHT);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultTableModel model = new DefaultTableModel(
            new String[]{"User ID", "Username", "Role", "Failed Logins", "Is Locked", "Active Status", "Last Login"}, 0
        ) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(model);
        ThemeManager.styleTable(table);

        Runnable refreshUsers = () -> {
            try {
                model.setRowCount(0);
                List<User> list = userDAO.getAllUsers();
                for (User u : list) {
                    model.addRow(new Object[]{
                        u.getUserId(),
                        u.getUsername(),
                        u.getRole(),
                        u.getFailedLogins(),
                        u.isLocked() ? "YES" : "NO",
                        u.isActive() ? "ACTIVE" : "DEACTIVATED",
                        u.getLastLogin() != null ? u.getLastLogin().toString() : "NEVER"
                    });
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        };

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        btnBar.setOpaque(false);

        JButton unlockBtn = ThemeManager.createPrimaryButton("Unlock Selected User");
        unlockBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                int uid = (int) table.getValueAt(row, 0);
                try {
                    authService.unlockUser(uid, currentUser);
                    JOptionPane.showMessageDialog(this, "User #" + uid + " unlocked successfully.");
                    refreshUsers.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                }
            }
        });

        JButton refreshBtn = ThemeManager.createSecondaryButton("Refresh Users");
        refreshBtn.addActionListener(e -> refreshUsers.run());

        btnBar.add(unlockBtn);
        btnBar.add(refreshBtn);

        root.add(btnBar, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);

        refreshUsers.run();
        return root;
    }

    private JPanel buildAdminAuditTab() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(ThemeManager.BG_LIGHT);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultTableModel model = new DefaultTableModel(
            new String[]{"Log ID", "Actor", "Role", "Action", "Target", "Details", "Status", "IP", "Timestamp"}, 0
        ) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(model);
        ThemeManager.styleTable(table);

        JButton refreshBtn = ThemeManager.createSecondaryButton("Refresh Audit Logs");
        refreshBtn.addActionListener(e -> {
            try {
                model.setRowCount(0);
                List<AuditLog> list = auditDAO.getRecentAuditLogs(100);
                for (AuditLog al : list) {
                    model.addRow(new Object[]{
                        al.getLogId(),
                        al.getActorUsername(),
                        al.getRole(),
                        al.getAction(),
                        al.getEntityType() + " #" + al.getEntityId(),
                        al.getDetails(),
                        al.getStatus(),
                        al.getIpAddress(),
                        al.getFormattedTimestamp()
                    });
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage());
            }
        });

        root.add(refreshBtn, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        refreshBtn.doClick();

        return root;
    }

    // =========================================================================
    // MODAL DIALOGS (FINANCIAL OPERATIONS & RECEIPTS)
    // =========================================================================
    private void showDepositDialog(Runnable onSuccess) {
        if (currentAccount == null) return;
        JTextField amtField = new JTextField(10);
        JTextField remField = new JTextField("Counter Cash Deposit", 15);

        JPanel p = new JPanel(new GridLayout(2, 2, 8, 8));
        p.add(new JLabel("Deposit Amount (Rs.):"));
        p.add(amtField);
        p.add(new JLabel("Description / Remarks:"));
        p.add(remField);

        int res = JOptionPane.showConfirmDialog(this, p, "Deposit Funds to #" + currentAccount.getAccountNumber(), JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            try {
                BigDecimal amt = CurrencyFormatter.parsePositiveMoney(amtField.getText());
                TransactionReceipt rec = bankingService.deposit(currentAccount.getAccountNumber(), amt, remField.getText(), currentUser);
                showReceiptDialog(rec);
                if (onSuccess != null) onSuccess.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Deposit Rejected", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showWithdrawDialog(Runnable onSuccess) {
        if (currentAccount == null) return;
        JPasswordField pinField = new JPasswordField(10);
        JTextField amtField = new JTextField(10);
        JTextField remField = new JTextField(currentAccount.getAccountType() + " Cash Withdrawal", 15);

        JPanel p = new JPanel(new GridLayout(3, 2, 8, 8));
        p.add(new JLabel("4-Digit Security PIN:"));
        p.add(pinField);
        p.add(new JLabel("Withdrawal Amount (Rs.):"));
        p.add(amtField);
        p.add(new JLabel("Description:"));
        p.add(remField);

        int res = JOptionPane.showConfirmDialog(this, p, "Withdraw from #" + currentAccount.getAccountNumber(), JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            try {
                String pin = new String(pinField.getPassword()).trim();
                BigDecimal amt = CurrencyFormatter.parsePositiveMoney(amtField.getText());
                TransactionReceipt rec = bankingService.withdraw(currentAccount.getAccountNumber(), pin, amt, remField.getText(), currentUser);
                showReceiptDialog(rec);
                if (onSuccess != null) onSuccess.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Withdrawal Rejected", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showTransferDialog(Runnable onSuccess) {
        if (currentAccount == null) return;
        JPasswordField pinField = new JPasswordField(10);
        JTextField destField = new JTextField(10);
        JTextField amtField = new JTextField(10);
        JTextField remField = new JTextField("Transfer Funds", 15);

        JPanel p = new JPanel(new GridLayout(4, 2, 8, 8));
        p.add(new JLabel("Your 4-Digit Security PIN:"));
        p.add(pinField);
        p.add(new JLabel("Beneficiary Account #:"));
        p.add(destField);
        p.add(new JLabel("Transfer Amount (Rs.):"));
        p.add(amtField);
        p.add(new JLabel("Remarks:"));
        p.add(remField);

        int res = JOptionPane.showConfirmDialog(this, p, "Atomic Fund Transfer from #" + currentAccount.getAccountNumber(), JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            try {
                String pin = new String(pinField.getPassword()).trim();
                int receiver = Integer.parseInt(destField.getText().trim());
                BigDecimal amt = CurrencyFormatter.parsePositiveMoney(amtField.getText());
                TransactionReceipt rec = bankingService.transfer(currentAccount.getAccountNumber(), pin, receiver, amt, remField.getText(), currentUser);
                showReceiptDialog(rec);
                if (onSuccess != null) onSuccess.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Transfer Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showInterestDialog(Runnable onSuccess) {
        if (currentAccount == null) return;
        if (!(currentAccount instanceof SavingsAccount)) {
            JOptionPane.showMessageDialog(this, "Yearly 4% interest applies exclusively to SAVINGS accounts.", "Not Applicable", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this,
            "Apply 4% yearly interest to Savings Account #" + currentAccount.getAccountNumber() + "?",
            "Confirm Interest Credit", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                TransactionReceipt rec = bankingService.applyYearlyInterest(currentAccount.getAccountNumber(), currentUser);
                showReceiptDialog(rec);
                if (onSuccess != null) onSuccess.run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Interest Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showMiniStatementDialog() {
        if (currentAccount == null) return;
        try {
            Account acc = bankDAO.getAccountByNumber(currentAccount.getAccountNumber());
            String[] buffer = acc.getRecentTransactions();
            StringBuilder sb = new StringBuilder();
            sb.append("Account #").append(acc.getAccountNumber()).append(" — ").append(acc.getHolderName()).append("\n\n");
            boolean has = false;
            for (int i = 0; i < buffer.length; i++) {
                if (buffer[i] != null) {
                    sb.append(i + 1).append(". ").append(buffer[i]).append("\n");
                    has = true;
                }
            }
            if (!has) {
                sb.append("[No transactions recorded in buffer]");
            }

            JTextArea ta = new JTextArea(sb.toString(), 10, 60);
            ta.setFont(ThemeManager.FONT_MONO);
            ta.setEditable(false);
            JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Fixed-Size Mini-Statement Buffer", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
    }

    private void showCloseAccountDialog(Runnable onSuccess) {
        if (currentAccount == null) return;
        JPasswordField pinField = new JPasswordField(10);
        JPanel p = new JPanel(new GridLayout(2, 2, 8, 8));
        p.add(new JLabel("Authorize with 4-Digit PIN:"));
        p.add(pinField);

        int res = JOptionPane.showConfirmDialog(this, p, "Close Account #" + currentAccount.getAccountNumber(), JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            try {
                String pin = new String(pinField.getPassword()).trim();
                boolean closed = accountService.closeAccount(currentAccount.getAccountNumber(), pin, currentUser);
                if (closed) {
                    JOptionPane.showMessageDialog(this, "Account #" + currentAccount.getAccountNumber() + " safely closed.\nFinancial ledger preserved for regulatory compliance.");
                    if (onSuccess != null) onSuccess.run();
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Closure Rejected", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showReceiptDialog(TransactionReceipt receipt) {
        JDialog dialog = new JDialog(this, "Transaction Receipt — " + receipt.getTransactionRef(), true);
        dialog.setSize(620, 480);
        dialog.setLocationRelativeTo(this);

        JTextArea ta = new JTextArea(receipt.toPrintableText());
        ta.setFont(ThemeManager.FONT_MONO);
        ta.setEditable(false);
        ta.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = ThemeManager.createPrimaryButton("Close");
        closeBtn.addActionListener(e -> dialog.dispose());
        btnPanel.add(closeBtn);

        dialog.add(new JScrollPane(ta), BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BankGuiApp app = new BankGuiApp();
            app.setVisible(true);
        });
    }
}
