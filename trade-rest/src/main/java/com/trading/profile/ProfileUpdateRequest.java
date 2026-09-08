package com.trading.profile;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;

public record ProfileUpdateRequest(
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Size(max = 20) String phoneNumber,
        @Size(max = 255) String avatarUrl,
        @Min(1) @Max(3) Integer hintQuestion,
        @Size(max = 256) String hintAnswer,
        @Size(max = 72) String currentPassword) {
    // Local strictness preserves older endpoints that intentionally ignore extra fields.
    @JsonAnySetter public void rejectUnsupportedField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported profile field.");
    }
    @Override public String toString() { return "ProfileUpdateRequest[redacted]"; }
}
