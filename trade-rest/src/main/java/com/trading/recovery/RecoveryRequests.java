package com.trading.recovery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class RecoveryRequests {
    private RecoveryRequests() {}
    public record Answer(@Min(1) @Max(3) int questionId, @NotBlank @Size(max = 256) String answer) {
        @Override public String toString() { return "Answer[redacted]"; }
    }
    public record Enrollment(@NotBlank @Size(max = 72) String currentPassword,
            @NotNull @Size(min = 3, max = 3) List<@NotNull @Valid Answer> answers) {
        @Override public String toString() { return "Enrollment[redacted]"; }
    }
    public record Start(@NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_.-]+") String username) {}
    public record Verify(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String challengeId,
            @NotNull @Size(min = 2, max = 2) List<@NotNull @Valid Answer> answers) {
        @Override public String toString() { return "Verify[redacted]"; }
    }
    public record Complete(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {
        @Override public String toString() { return "Complete[redacted]"; }
    }
}
