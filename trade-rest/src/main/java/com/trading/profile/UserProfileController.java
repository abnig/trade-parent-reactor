package com.trading.profile;

import com.trading.exception.ApiError;
import com.trading.model.ProfileHintQuestion;
import com.trading.model.UserProfile;
import com.trading.security.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/profile")
public class UserProfileController {
    private final UserProfileService service;
    public UserProfileController(UserProfileService service) { this.service = service; }

    @GetMapping
    public UserProfile get(@AuthenticationPrincipal AccountPrincipal user, HttpServletRequest request) {
        rejectQueryParameters(request);
        return service.get(user.getId());
    }
    @PutMapping
    public UserProfile update(@AuthenticationPrincipal AccountPrincipal user, @Valid @RequestBody ProfileUpdateRequest body,
            HttpServletRequest request) {
        rejectQueryParameters(request);
        return service.update(user.getId(), user.getCredentialVersion(), body);
    }
    @GetMapping("/hint-questions")
    public List<Question> questions() {
        return Arrays.stream(ProfileHintQuestion.values()).map(q -> new Question(q.id(), q.text())).toList();
    }
    private void rejectQueryParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty())
            throw new ProfileException(org.springframework.http.HttpStatus.BAD_REQUEST, null, "Profile endpoints do not accept query parameters.");
    }
    @ExceptionHandler(ProfileException.class)
    public ResponseEntity<ApiError> failure(ProfileException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.status().value(), exception.status().getReasonPhrase(),
                exception.getMessage(), request.getRequestURI(), exception.fieldErrors()));
    }
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiError> duplicate(HttpServletRequest request) {
        return ResponseEntity.status(409).body(new ApiError(409, "Conflict", "Email address is already registered.",
                request.getRequestURI(), Map.of("email", "Email address is already registered.")));
    }
    public record Question(int id, String text) {}
}
