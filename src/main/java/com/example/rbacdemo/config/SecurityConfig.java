package com.example.rbacdemo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/csrf", "/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users").hasAuthority("user:read")
                        .requestMatchers(HttpMethod.POST, "/api/users").hasAuthority("user:create")
                        .requestMatchers(HttpMethod.DELETE, "/api/users/{id}").hasAuthority("user:delete")
                        .requestMatchers("/api/transactions/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/transactions/**"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .successHandler(jsonLoginSuccess(objectMapper))
                        .failureHandler((request, response, exception) -> writeJson(response, objectMapper, 401,
                                Map.of("error", "invalid_credentials"))))
                .logout(logout -> logout.logoutSuccessHandler((request, response, authentication) ->
                        writeJson(response, objectMapper, 200, Map.of("message", "logged_out"))))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeJson(response, objectMapper, 401,
                                Map.of("error", "unauthorized")))
                        .accessDeniedHandler((request, response, exception) -> writeJson(response, objectMapper, 403,
                                Map.of("error", "forbidden"))));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private AuthenticationSuccessHandler jsonLoginSuccess(ObjectMapper objectMapper) {
        return (request, response, authentication) -> writeJson(response, objectMapper, 200,
                Map.of("username", authentication.getName()));
    }

    private static void writeJson(HttpServletResponse response, ObjectMapper objectMapper, int status,
                                  Map<String, String> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
