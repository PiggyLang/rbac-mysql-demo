package com.example.rbacdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RbacSeedIntegrationTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesOnlyValidBcryptHashesForAliceAndBob() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String aliceHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM `user` WHERE username = 'alice'", String.class);
        String bobHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM `user` WHERE username = 'bob'", String.class);

        assertThat(encoder.matches("learn-only-demo", aliceHash)).isTrue();
        assertThat(encoder.matches("learn-only-demo", bobHash)).isTrue();
        assertThat(aliceHash).doesNotContain("learn-only-demo");
        assertThat(bobHash).doesNotContain("learn-only-demo");
    }

    @Test
    void assignsAllThreePermissionsToAliceAndOnlyReadToBob() {
        assertThat(permissionCodes("alice")).containsExactlyInAnyOrder("user:read", "user:create", "user:delete");
        assertThat(permissionCodes("bob")).containsExactlyInAnyOrder("user:read");
    }

    @Test
    void keepsNavigationMenusInTheirOwnRoleRelation() {
        assertThat(menuCodes("alice")).containsExactlyInAnyOrder("users", "reports");
        assertThat(menuCodes("bob")).containsExactlyInAnyOrder("reports");
    }

    private Set<String> permissionCodes(String username) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT p.code FROM `user` u
                JOIN user_role ur ON ur.user_id = u.id
                JOIN `role` r ON r.id = ur.role_id
                JOIN role_permission rp ON rp.role_id = r.id
                JOIN permission p ON p.id = rp.permission_id
                WHERE u.username = ? ORDER BY p.code
                """, String.class, username));
    }

    private Set<String> menuCodes(String username) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT m.code FROM `user` u
                JOIN user_role ur ON ur.user_id = u.id
                JOIN `role` r ON r.id = ur.role_id
                JOIN role_menu rm ON rm.role_id = r.id
                JOIN menu m ON m.id = rm.menu_id
                WHERE u.username = ? ORDER BY m.code
                """, String.class, username));
    }
}
