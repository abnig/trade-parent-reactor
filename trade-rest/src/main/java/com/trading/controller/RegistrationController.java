package com.trading.controller;

import java.util.Map;
import com.trading.dto.RegisterRequest;
import com.trading.exception.ApiError;
import com.trading.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    private final RegistrationService service;

    public RegistrationController(RegistrationService service) { this.service = service; }

    @PostMapping("/register")
    public ResponseEntity<RegistrationService.RegisteredUser> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(service.register(request));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> duplicate() {
        return ResponseEntity.status(409).body(new ApiError(409, "Conflict",
                "Username or email is already registered.", "/api/auth/register", Map.of()));
    }

    @ExceptionHandler(RegistrationService.InvalidRegistrationException.class)
    public ResponseEntity<ApiError> invalid(RegistrationService.InvalidRegistrationException exception) {
        return ResponseEntity.badRequest().body(new ApiError(400, "Validation failed",
                exception.getMessage(), "/api/auth/register", Map.of("password", exception.getMessage())));
    }
}
