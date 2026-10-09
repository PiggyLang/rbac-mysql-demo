package com.example.rbacdemo.menu;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MenuController {
    private final JdbcTemplate jdbcTemplate;

    public MenuController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/menus")
    public List<MenuItem> menus(Authentication authentication) {
        return jdbcTemplate.query("""
                        SELECT DISTINCT m.code, m.name, m.path, m.sort_order
                        FROM `user` u
                        JOIN user_role ur ON ur.user_id = u.id
                        JOIN role_menu rm ON rm.role_id = ur.role_id
                        JOIN menu m ON m.id = rm.menu_id
                        WHERE u.username = ?
                        ORDER BY m.sort_order, m.code
                        """,
                (rs, rowNum) -> new MenuItem(
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("path"),
                        rs.getInt("sort_order")),
                authentication.getName());
    }

    public record MenuItem(String code, String name, String path, int sortOrder) { }
}
