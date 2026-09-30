package com.aethermon.core.economy;

import com.aethermon.core.economy.api.Currency;
import com.aethermon.core.economy.api.EconomyResult;
import com.aethermon.core.economy.db.DatabaseManager;
import com.aethermon.core.economy.impl.SqliteEconomyService;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SqliteEconomyService.
 * Uses an in-memory SQLite database (file deleted after each test).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EconomyServiceTest {

    private static DatabaseManager db;
    private static SqliteEconomyService service;
    private static final UUID PLAYER_A = UUID.randomUUID();
    private static final UUID PLAYER_B = UUID.randomUUID();
    private static final BigDecimal START_COINS = BigDecimal.valueOf(50_000);
    private static final BigDecimal START_GEMS  = BigDecimal.valueOf(25);

    @BeforeAll
    static void setup() throws Exception {
        Path tmpDir = Files.createTempDirectory("aethermon-test");
        db = new DatabaseManager(tmpDir);
        db.initSchema();
        service = new SqliteEconomyService(db, START_COINS, START_GEMS);

        // Create accounts for both test players
        service.createAccount(PLAYER_A, "PlayerA").get();
        service.createAccount(PLAYER_B, "PlayerB").get();
    }

    @Test @Order(1)
    void newAccount_hasStartingBalance() throws Exception {
        BigDecimal coins = service.getBalance(PLAYER_A, Currency.COINS).get();
        BigDecimal gems  = service.getBalance(PLAYER_A, Currency.GEMS).get();
        assertEquals(0, START_COINS.compareTo(coins), "Starting Coins should be 50,000");
        assertEquals(0, START_GEMS.compareTo(gems),   "Starting Gems should be 25");
    }

    @Test @Order(2)
    void createAccount_isIdempotent() throws Exception {
        // Calling createAccount twice should not double the balance
        service.createAccount(PLAYER_A, "PlayerA").get();
        BigDecimal coins = service.getBalance(PLAYER_A, Currency.COINS).get();
        assertEquals(0, START_COINS.compareTo(coins), "Balance should not change on duplicate createAccount");
    }

    @Test @Order(3)
    void deposit_increasesBalance() throws Exception {
        EconomyResult result = service.deposit(PLAYER_A, Currency.COINS,
                BigDecimal.valueOf(1000), "test_deposit").get();
        assertTrue(result.isSuccess());
        assertEquals(0, BigDecimal.valueOf(51_000).compareTo(result.getNewBalance()));
    }

    @Test @Order(4)
    void withdraw_decreasesBalance() throws Exception {
        EconomyResult result = service.withdraw(PLAYER_A, Currency.COINS,
                BigDecimal.valueOf(1000), "test_withdraw").get();
        assertTrue(result.isSuccess());
        assertEquals(0, BigDecimal.valueOf(50_000).compareTo(result.getNewBalance()));
    }

    @Test @Order(5)
    void withdraw_failsOnInsufficientFunds() throws Exception {
        EconomyResult result = service.withdraw(PLAYER_A, Currency.COINS,
                BigDecimal.valueOf(999_999_999), "test_overdraft").get();
        assertFalse(result.isSuccess());
        assertEquals(EconomyResult.Status.INSUFFICIENT_FUNDS, result.getStatus());
    }

    @Test @Order(6)
    void transfer_movesMoneyAtomically() throws Exception {
        BigDecimal amount = BigDecimal.valueOf(5_000);
        EconomyResult result = service.transfer(PLAYER_A, PLAYER_B, Currency.COINS,
                amount, "test_transfer").get();
        assertTrue(result.isSuccess());

        BigDecimal aBalance = service.getBalance(PLAYER_A, Currency.COINS).get();
        BigDecimal bBalance = service.getBalance(PLAYER_B, Currency.COINS).get();

        assertEquals(0, BigDecimal.valueOf(45_000).compareTo(aBalance), "Sender should have 45,000");
        assertEquals(0, BigDecimal.valueOf(55_000).compareTo(bBalance), "Receiver should have 55,000");
    }

    @Test @Order(7)
    void deposit_rejectsZeroAmount() throws Exception {
        EconomyResult result = service.deposit(PLAYER_A, Currency.COINS,
                BigDecimal.ZERO, "test_zero").get();
        assertFalse(result.isSuccess());
        assertEquals(EconomyResult.Status.INVALID_AMOUNT, result.getStatus());
    }

    @Test @Order(8)
    void setBalance_setsExactAmount() throws Exception {
        EconomyResult result = service.setBalance(PLAYER_A, Currency.COINS,
                BigDecimal.valueOf(100_000), "test_set").get();
        assertTrue(result.isSuccess());
        BigDecimal bal = service.getBalance(PLAYER_A, Currency.COINS).get();
        assertEquals(0, BigDecimal.valueOf(100_000).compareTo(bal));
    }

    @Test @Order(9)
    void hasAccount_returnsTrue() throws Exception {
        assertTrue(service.hasAccount(PLAYER_A).get());
        assertFalse(service.hasAccount(UUID.randomUUID()).get());
    }
}
