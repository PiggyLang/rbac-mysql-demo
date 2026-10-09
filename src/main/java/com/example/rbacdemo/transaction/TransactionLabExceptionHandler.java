package com.example.rbacdemo.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = TransactionsController.class)
public class TransactionLabExceptionHandler {
    @ExceptionHandler(DemoTransferFailedException.class)
    public ResponseEntity<Map<String, String>> demoTransferFailed() {
        return response("demo_transfer_failed");
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, String>> insufficientBalance() {
        return response("insufficient_balance");
    }

    @ExceptionHandler(InvalidLabAccountsException.class)
    public ResponseEntity<Map<String, String>> invalidAccounts() {
        return response("invalid_lab_accounts");
    }

    @ExceptionHandler(CheckedTransferFailedException.class)
    public ResponseEntity<Map<String, String>> checkedTransferFailed() {
        return response("checked_transfer_failed");
    }

    private ResponseEntity<Map<String, String>> response(String error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", error));
    }
}
