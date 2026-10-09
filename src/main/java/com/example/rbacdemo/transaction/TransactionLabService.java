package com.example.rbacdemo.transaction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionLabService {
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private final JdbcTemplate jdbcTemplate;

    public TransactionLabService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Account> accounts() {
        return jdbcTemplate.query("SELECT id, name, balance FROM account ORDER BY id",
                (rs, row) -> new Account(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("balance")));
    }

    @Transactional
    public TransferResult transfer(boolean failAfterDebit) {
        validateAccounts();
        debitAccountA();

        if (failAfterDebit) {
            throw new DemoTransferFailedException();
        }

        int credited = jdbcTemplate.update(
                "UPDATE account SET balance = balance + ? WHERE id = 2 AND name = 'B'", AMOUNT);
        if (credited != 1) {
            throw new InvalidLabAccountsException();
        }
        return new TransferResult("transferred", AMOUNT);
    }

    @Transactional
    public void transferCheckedDefault() throws CheckedTransferFailedException {
        validateAccounts();
        debitAccountA();
        throw new CheckedTransferFailedException();
    }

    @Transactional(rollbackFor = CheckedTransferFailedException.class)
    public void transferCheckedRollback() throws CheckedTransferFailedException {
        validateAccounts();
        debitAccountA();
        throw new CheckedTransferFailedException();
    }

    private void debitAccountA() {
        int debited = jdbcTemplate.update(
                "UPDATE account SET balance = balance - ? WHERE id = 1 AND name = 'A' AND balance >= ?",
                AMOUNT, AMOUNT);
        if (debited != 1) {
            if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account WHERE id = 1 AND name = 'A'", Integer.class) != 1) {
                throw new InvalidLabAccountsException();
            }
            throw new InsufficientBalanceException();
        }
    }

    private void validateAccounts() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE (id = 1 AND name = 'A') OR (id = 2 AND name = 'B')",
                Integer.class);
        if (count == null || count != 2) {
            throw new InvalidLabAccountsException();
        }
    }

    public record Account(long id, String name, BigDecimal balance) { }
    public record TransferResult(String message, BigDecimal amount) { }
}
