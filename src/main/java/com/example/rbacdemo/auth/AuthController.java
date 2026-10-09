package com.example.rbacdemo.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    @GetMapping("/api/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @GetMapping("/api/me")
    public AuthSummary me(Authentication authentication) {
        return new AuthSummary(authentication.getName());
    }

    public record CsrfResponse(String headerName, String parameterName, String token) { }
    public record AuthSummary(String username) { }
}
