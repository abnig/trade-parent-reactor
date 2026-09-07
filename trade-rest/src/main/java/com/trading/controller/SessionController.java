package com.trading.controller;

import java.util.List;
import java.util.Map;
import com.trading.security.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class SessionController {
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @GetMapping("/me")
    public SessionUser me(@AuthenticationPrincipal AccountPrincipal principal) {
        return new SessionUser(principal.getId(), principal.getUsername(),
                principal.getAuthorities().stream().map(Object::toString).toList());
    }

    public record SessionUser(long id, String username, List<String> roles) {}
}
