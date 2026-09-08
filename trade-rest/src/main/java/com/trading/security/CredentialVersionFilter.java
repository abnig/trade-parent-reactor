package com.trading.security;

import com.trading.repository.PasswordRecoveryRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects stored sessions after password changes or account disablement. */
final class CredentialVersionFilter extends OncePerRequestFilter {
    private final PasswordRecoveryRepository repository;
    CredentialVersionFilter(PasswordRecoveryRepository repository) { this.repository = repository; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            var account = repository.account(principal.getId()).orElse(null);
            if (account == null || !account.eligible() || account.credentialVersion() != principal.getCredentialVersion()) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                response.setStatus(401);
                response.setContentType("application/json");
                response.getWriter().write("{\"status\":401,\"message\":\"Session expired. Please log in again.\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
