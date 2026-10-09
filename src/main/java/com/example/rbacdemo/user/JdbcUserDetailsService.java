package com.example.rbacdemo.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JdbcUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<DatabaseUser> users = jdbcTemplate.query(
                "SELECT username, password_hash, enabled FROM `user` WHERE username = ?",
                (rs, rowNum) -> new DatabaseUser(
                        rs.getString("username"), rs.getString("password_hash"), rs.getBoolean("enabled")),
                username);
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("Invalid username or password");
        }

        DatabaseUser user = users.get(0);
        List<String> permissions = jdbcTemplate.queryForList("""
                SELECT DISTINCT p.code
                FROM `user` u
                JOIN user_role ur ON ur.user_id = u.id
                JOIN role_permission rp ON rp.role_id = ur.role_id
                JOIN permission p ON p.id = rp.permission_id
                WHERE u.username = ?
                ORDER BY p.code
                """, String.class, username);

        User.UserBuilder builder = User.withUsername(user.username())
                .password(user.passwordHash())
                .authorities(permissions.toArray(String[]::new));
        return user.enabled() ? builder.build() : builder.disabled(true).build();
    }

    private record DatabaseUser(String username, String passwordHash, boolean enabled) { }
}
