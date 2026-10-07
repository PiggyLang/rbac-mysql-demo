package com.example.rbacdemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DbCheckController {
    private final DbCheckService dbCheckService;

    public DbCheckController(DbCheckService dbCheckService) {
        this.dbCheckService = dbCheckService;
    }

    @GetMapping("/db-check")
    public DbCheckService.DbCheckResponse dbCheck() {
        return dbCheckService.check();
    }
}
