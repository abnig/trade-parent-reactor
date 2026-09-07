package com.trading.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_.-]+") String username,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Size(max = 20) String phoneNumber,
        @Size(max = 255) String avatarUrl) {
    @Override
    public String toString() { return "RegisterRequest[redacted]"; }
}
