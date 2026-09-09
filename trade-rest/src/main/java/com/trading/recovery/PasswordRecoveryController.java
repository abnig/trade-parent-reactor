package com.trading.recovery;

import com.trading.exception.ApiError;
import com.trading.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {
    private final PasswordRecoveryService service;
    public PasswordRecoveryController(PasswordRecoveryService service) { this.service = service; }

    @PostMapping("/forgot-username")
    public ResponseEntity<Map<String, String>> remindUsername(
            @Valid @RequestBody RecoveryRequests.ForgotUsername request, HttpServletRequest http) {
        service.remindUsername(request.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().body(Map.of("message",
                "If an eligible account matches that email address, its username will be sent to it."));
    }

    @GetMapping("/recovery/questions")
    public List<PasswordRecoveryService.Question> questions() { return service.questions(); }

    @PutMapping("/recovery/answers")
    public ResponseEntity<Void> enroll(@AuthenticationPrincipal AccountPrincipal user,
            @Valid @RequestBody RecoveryRequests.Enrollment request, HttpServletRequest http) {
        service.enroll(user.getId(), request, http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/password-reset/challenges")
    public PasswordRecoveryService.ChallengeResponse start(@Valid @RequestBody RecoveryRequests.Start request, HttpServletRequest http) {
        return service.start(request.username(), http.getRemoteAddr());
    }
    @PostMapping("/password-reset/verify")
    public ResponseEntity<Map<String, String>> verify(@Valid @RequestBody RecoveryRequests.Verify request, HttpServletRequest http) {
        service.verify(request, http.getRemoteAddr());
        return ResponseEntity.accepted().body(Map.of("message", "If the details are correct and the account is eligible, a password reset link will be sent to its registered email."));
    }
    @PostMapping("/password-reset/complete")
    public ResponseEntity<Void> complete(@Valid @RequestBody RecoveryRequests.Complete request, HttpServletRequest http) {
        service.complete(request, http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler(RecoveryException.class)
    public ResponseEntity<ApiError> failure(RecoveryException exception, HttpServletRequest request) {
        var response = ResponseEntity.status(exception.status());
        if (exception.status().value() == 429) response.header("Retry-After", "900");
        return response.body(new ApiError(exception.status().value(), exception.status().getReasonPhrase(),
                exception.getMessage(), request.getRequestURI(), Map.of()));
    }
}
