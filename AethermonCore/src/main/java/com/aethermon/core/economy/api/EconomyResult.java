package com.aethermon.core.economy.api;

import java.math.BigDecimal;

/**
 * The result of any economy mutation (deposit, withdraw, transfer, set).
 * Always check status before using the balance field.
 */
public final class EconomyResult {

    public enum Status {
        /** Operation succeeded. */
        SUCCESS,
        /** Player had insufficient funds for a withdraw/transfer. */
        INSUFFICIENT_FUNDS,
        /** Player account does not exist in the database. */
        ACCOUNT_NOT_FOUND,
        /** Amount was zero or negative — invalid input. */
        INVALID_AMOUNT,
        /** A database or unexpected error occurred. Check message. */
        ERROR
    }

    private final Status status;
    private final BigDecimal newBalance;  // balance after the operation (null on failure)
    private final String message;         // human-readable detail for logs/error messages

    private EconomyResult(Status status, BigDecimal newBalance, String message) {
        this.status     = status;
        this.newBalance = newBalance;
        this.message    = message;
    }

    // ── Factory methods ───────────────────────────────────────

    public static EconomyResult success(BigDecimal newBalance) {
        return new EconomyResult(Status.SUCCESS, newBalance, "OK");
    }

    public static EconomyResult insufficientFunds(BigDecimal currentBalance) {
        return new EconomyResult(Status.INSUFFICIENT_FUNDS, currentBalance,
                "Insufficient funds. Current balance: " + currentBalance);
    }

    public static EconomyResult accountNotFound() {
        return new EconomyResult(Status.ACCOUNT_NOT_FOUND, null, "Account not found.");
    }

    public static EconomyResult invalidAmount(BigDecimal amount) {
        return new EconomyResult(Status.INVALID_AMOUNT, null,
                "Invalid amount: " + amount + ". Must be > 0.");
    }

    public static EconomyResult error(String message) {
        return new EconomyResult(Status.ERROR, null, message);
    }

    // ── Accessors ─────────────────────────────────────────────

    public boolean isSuccess()          { return status == Status.SUCCESS; }
    public Status getStatus()           { return status; }
    public BigDecimal getNewBalance()   { return newBalance; }
    public String getMessage()          { return message; }
}
