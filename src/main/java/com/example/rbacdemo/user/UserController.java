package com.example.rbacdemo.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final JdbcTemplate jdbcTemplate;

    public UserController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public List<String> users() {
        return jdbcTemplate.queryForList("SELECT username FROM `user` ORDER BY id", String.class);
    }

    @PostMapping
    public Map<String, Object> createDemo() {
        return Map.of("message", "user_create_allowed", "demo", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deleteDemo(@PathVariable long id) {
        return Map.of("message", "user_delete_allowed", "demo", true, "id", id);
    }
}
