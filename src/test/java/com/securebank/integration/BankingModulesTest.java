package com.securebank.integration;

import com.securebank.dao.BankDAO;
import com.securebank.exception.AuthorizationException;
import com.securebank.model.Beneficiary;
import com.securebank.model.Biller;
import com.securebank.model.BillPayment;
import com.securebank.model.DebitCard;
import com.securebank.model.FixedDeposit;
import com.securebank.model.Loan;
import com.securebank.model.Role;
import com.securebank.model.User;
import com.securebank.service.BankingModulesService;
import com.securebank.service.BeneficiaryService;
import com.securebank.util.DatabaseConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BankingModulesTest {

    private BankingModulesService modulesService;
    private BeneficiaryService beneficiaryService;
    private BankDAO bankDAO;

    @BeforeEach
    public void setup() {
        DatabaseConfig.switchToEmbeddedH2();
        DatabaseConfig.seedTestData();
        this.bankDAO = new BankDAO();
        this.modulesService = new BankingModulesService();
        this.beneficiaryService = new BeneficiaryService();
    }

    // ==========================================
    // 1. BILL PAYMENTS
    // ==========================================

    @Test
    @DisplayName("Bill Payments: Valid biller payment deducts funds and generates verifiable receipt")
    public void testBillPaymentWorkflow() throws Exception {
        User aarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        List<Biller> billers = modulesService.getAllBillers();
        assertFalse(billers.isEmpty(), "Seed billers should be available.");
        Biller bescom = billers.stream().filter(b -> b.getBillerCode().contains("BESCOM")).findFirst().orElse(billers.get(0));

        BigDecimal balBefore = bankDAO.getAccountByNumber(1001).getBalance();
        BigDecimal billAmount = new BigDecimal("850.00");

        BillPayment payment = modulesService.payBill(501, 1001, "1234", bescom.getBillerId(), "BES-987654321", billAmount, aarav);
        assertNotNull(payment);
        assertNotNull(payment.getPaymentRef());
        assertEquals("SUCCESS", payment.getStatus());

        BigDecimal balAfter = bankDAO.getAccountByNumber(1001).getBalance();
        assertEquals(0, balBefore.subtract(billAmount).compareTo(balAfter));

        List<BillPayment> history = modulesService.getCustomerBillPayments(501, aarav);
        assertFalse(history.isEmpty());
        assertEquals(payment.getPaymentRef(), history.get(0).getPaymentRef());
    }

    @Test
    @DisplayName("Bill Payments: Wrong PIN or unowned account rejects bill payment")
    public void testBillPaymentSecurityRejections() {
        User aarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);

        // Invalid PIN
        assertThrows(SecurityException.class, () -> {
            modulesService.payBill(501, 1001, "9999", 1, "CONS-1", new BigDecimal("500.00"), aarav);
        });

        // Unowned account (Account 1002 belongs to Priya, customer 502)
        assertThrows(AuthorizationException.class, () -> {
            modulesService.payBill(501, 1002, "1234", 1, "CONS-1", new BigDecimal("500.00"), aarav);
        });
    }

    // ==========================================
    // 2. FIXED DEPOSITS
    // ==========================================

    @Test
    @DisplayName("Fixed Deposits: Compound interest maturity calculation and creation workflow")
    public void testFixedDepositCreationAndMaturityCalculation() throws Exception {
        User aarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        BigDecimal principal = new BigDecimal("5000.00");
        int tenureMonths = 12;

        BigDecimal balBefore = bankDAO.getAccountByNumber(1001).getBalance();
        FixedDeposit fd = modulesService.openFixedDeposit(501, 1001, "1234", principal, tenureMonths, aarav);

        assertNotNull(fd);
        assertEquals("ACTIVE", fd.getStatus());
        assertTrue(fd.getMaturityAmount().compareTo(principal) > 0, "Maturity amount must exceed principal with interest.");
        assertEquals(0, balBefore.subtract(principal).compareTo(bankDAO.getAccountByNumber(1001).getBalance()));

        // Premature liquidation returns funds back to funding account
        boolean closed = modulesService.closeFixedDeposit(fd.getFdId(), 501, aarav);
        assertTrue(closed);
        assertEquals(0, balBefore.compareTo(bankDAO.getAccountByNumber(1001).getBalance()));
    }

    // ==========================================
    // 3. LOAN MANAGEMENT
    // ==========================================

    @Test
    @DisplayName("Loan Management: Application, EMI calculation, staff approval and fund disbursement")
    public void testLoanApplicationAndDisbursementWorkflow() throws Exception {
        User aarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        User teller = new User(102, "teller_suresh", "hash", Role.TELLER, 0, false, true);

        BigDecimal principal = new BigDecimal("50000.00");
        int tenureMonths = 24;

        Loan loan = modulesService.applyLoan(501, "PERSONAL", principal, tenureMonths, 1001, aarav);
        assertNotNull(loan);
        assertEquals("APPLIED", loan.getStatus());
        assertTrue(loan.getEmiAmount().compareTo(BigDecimal.ZERO) > 0);

        BigDecimal balBeforeDisburse = bankDAO.getAccountByNumber(1001).getBalance();

        // Teller reviews and approves loan
        boolean approved = modulesService.reviewLoanApplication(loan.getLoanId(), "APPROVED", teller);
        assertTrue(approved);

        // Verification of disbursement into customer account 1001
        BigDecimal balAfterDisburse = bankDAO.getAccountByNumber(1001).getBalance();
        assertEquals(0, balBeforeDisburse.add(principal).compareTo(balAfterDisburse));

        // Customer pays an EMI installment
        boolean repaid = modulesService.repayLoanEmi(loan.getLoanId(), 501, 1001, "1234", loan.getEmiAmount(), aarav);
        assertTrue(repaid);
    }

    // ==========================================
    // 4. DEBIT CARDS
    // ==========================================

    @Test
    @DisplayName("Debit Cards: Customer controls for freeze, daily limit, and contactless toggle")
    public void testDebitCardControls() throws Exception {
        User aarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);
        List<DebitCard> cards = modulesService.getCustomerCards(501, aarav);
        assertFalse(cards.isEmpty(), "Seed debit card should exist for customer.");

        DebitCard card = cards.get(0);
        int cardId = card.getCardId();

        // 1. Toggle freeze
        boolean blocked = modulesService.toggleCardBlock(cardId, 501, true, aarav);
        assertTrue(blocked);

        // 2. Update daily limit
        BigDecimal newLimit = new BigDecimal("75000.00");
        boolean limitUpdated = modulesService.updateCardDailyLimit(cardId, 501, newLimit, aarav);
        assertTrue(limitUpdated);

        // 3. Toggle contactless
        boolean contactless = modulesService.toggleContactless(cardId, 501, false, aarav);
        assertTrue(contactless);
    }

    // ==========================================
    // 5. BENEFICIARY MANAGEMENT
    // ==========================================

    @Test
    @DisplayName("Beneficiary: Add counterparty, prevent self-beneficiary, and list verified counterparties")
    public void testBeneficiaryManagement() throws Exception {
        User aarav = new User(103, "aarav", "hash", Role.CUSTOMER, 0, false, true);

        // Aarav adds Priya (Account 1002) as beneficiary (if not already added in seed) or Meera (1005)
        Beneficiary b = beneficiaryService.addBeneficiary(501, 1003, "Rohan Verma", "SecureBank", "SCBK0001001", new BigDecimal("25000.00"), aarav);
        assertNotNull(b);
        assertTrue(b.getBeneficiaryId() > 0);

        List<Beneficiary> list = beneficiaryService.getBeneficiaries(501, aarav);
        assertTrue(list.stream().anyMatch(item -> item.getBeneficiaryAccount() == 1003));

        // Prevent adding self account (Account 1001) as beneficiary
        assertThrows(IllegalArgumentException.class, () -> {
            beneficiaryService.addBeneficiary(501, 1001, "Aarav Self", "SecureBank", "SCBK0001001", new BigDecimal("10000.00"), aarav);
        });

        // Deactivate
        boolean deleted = beneficiaryService.deleteBeneficiary(b.getBeneficiaryId(), 501, aarav);
        assertTrue(deleted);
    }
}
