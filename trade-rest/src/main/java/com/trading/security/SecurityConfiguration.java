package com.trading.security;

import com.trading.repository.LoginAccountRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(LoginAccountRepository repository) {
        return username -> new AccountPrincipal(repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password")));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, com.trading.repository.PasswordRecoveryRepository recovery) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auth/recovery/questions").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/password-reset/challenges",
                    "/api/auth/password-reset/verify", "/api/auth/password-reset/complete").permitAll()
                .requestMatchers("/api/**", "/actuator/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(new CredentialVersionFilter(recovery), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    error(response, 401, "Login required."))
                .accessDeniedHandler((request, response, exception) ->
                    error(response, !hasAuthenticatedUser() ? 401 : 403,
                        !hasAuthenticatedUser() ? "Login required." : "Access denied or session token expired. Refresh and try again.")))
            .formLogin(login -> login.loginPage("/login").loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> response.setStatus(204))
                .failureHandler((request, response, exception) -> error(response, 401, "Invalid username or password.")))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .build();
    }

    private static boolean hasAuthenticatedUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    private static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
