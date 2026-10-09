package com.example.rbacdemo.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JdbcUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbcTemplate;

    public JdbcUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return jdbcTemplate.query("SELECT username, password_hash, enabled FROM `user` WHERE username = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new UsernameNotFoundException("Invalid username or password");
                    }
                    User.UserBuilder builder = User.withUsername(rs.getString("username"))
                            .password(rs.getString("password_hash")).authorities(new String[0]);
                    return rs.getBoolean("enabled") ? builder.build() : builder.disabled(true).build();
                }, username);
    }
}
