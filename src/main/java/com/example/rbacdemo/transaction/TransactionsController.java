package com.example.rbacdemo.transaction;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionsController {
    private final TransactionLabService transactionLabService;

    public TransactionsController(TransactionLabService transactionLabService) {
        this.transactionLabService = transactionLabService;
    }

    @GetMapping("/accounts")
    public List<TransactionLabService.Account> accounts() {
        return transactionLabService.accounts();
    }

    @PostMapping("/transfer")
    public TransactionLabService.TransferResult transfer() {
        return transactionLabService.transfer(false);
    }

    @PostMapping("/transfer-fail")
    public TransactionLabService.TransferResult transferFail() {
        return transactionLabService.transfer(true);
    }

    @PostMapping("/transfer-checked-default")
    public void transferCheckedDefault() throws CheckedTransferFailedException {
        transactionLabService.transferCheckedDefault();
    }

    @PostMapping("/transfer-checked-rollback")
    public void transferCheckedRollback() throws CheckedTransferFailedException {
        transactionLabService.transferCheckedRollback();
    }
}
