package com.example.rbacdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DbCheckEndpointTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsConnectionAndOnlyNonSensitiveTableCounts() throws Exception {
        mockMvc.perform(get("/api/db-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectOne").value(1))
                .andExpect(jsonPath("$.tables.user").value(2))
                .andExpect(jsonPath("$.tables.role").value(2))
                .andExpect(jsonPath("$.tables.permission").value(3))
                .andExpect(jsonPath("$.tables.menu").value(2))
                .andExpect(jsonPath("$.tables.user_role").value(2))
                .andExpect(jsonPath("$.tables.role_permission").value(4))
                .andExpect(jsonPath("$.tables.role_menu").value(3))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("password"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("alice"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(not(containsString("bob"))));
    }

}
