package com.example.rbacdemo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DbCheckService {
    private static final List<String> TABLES = List.of(
            "user", "role", "permission", "menu", "user_role", "role_permission", "role_menu");

    private final JdbcTemplate jdbcTemplate;

    public DbCheckService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DbCheckResponse check() {
        int selectOne = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TABLES) {
            String identifier = table.equals("user") ? "`user`" : table;
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + identifier, Long.class);
            counts.put(table, count);
        }
        return new DbCheckResponse(selectOne, counts);
    }

    public record DbCheckResponse(int selectOne, Map<String, Long> tables) { }
}
