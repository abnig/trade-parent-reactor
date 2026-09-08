package com.trading.profile;

import com.trading.model.ProfileChanges;
import com.trading.model.ProfileHintQuestion;
import com.trading.model.UserProfile;
import com.trading.repository.UserProfileRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private final UserProfileRepository repository;
    private final PasswordEncoder encoder;
    public UserProfileService(UserProfileRepository repository, PasswordEncoder encoder) {
        this.repository = repository; this.encoder = encoder;
    }
    public UserProfile get(long userId) {
        return repository.find(userId).orElseThrow(() -> new ProfileException(HttpStatus.NOT_FOUND, null, "Profile not found."));
    }

    @Transactional
    public UserProfile update(long userId, long credentialVersion, ProfileUpdateRequest request) {
        validate(request);
        var credentials = repository.lockCredentials(userId)
                .orElseThrow(() -> new ProfileException(HttpStatus.UNAUTHORIZED, null, "Please log in again."));
        if (!credentials.eligible() || credentials.credentialVersion() != credentialVersion)
            throw new ProfileException(HttpStatus.UNAUTHORIZED, null, "Please log in again.");
        var current = get(userId);
        boolean emailChanged = !current.email().equals(request.email());
        boolean hintChanged = request.hintQuestion() != null || request.hintAnswer() != null;
        if (emailChanged || hintChanged) {
            if (request.currentPassword() == null || request.currentPassword().isBlank())
                throw invalid("currentPassword", "Current password is required to change email or hint details.");
            if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                    || !encoder.matches(request.currentPassword(), credentials.passwordHash()))
                throw new ProfileException(HttpStatus.FORBIDDEN, "currentPassword", "Current password is incorrect.");
        }
        String answerHash = hintChanged ? encoder.encode(answerDigest(request.hintAnswer())) : null;
        repository.update(userId, new ProfileChanges(request.email(), optional(request.firstName()), optional(request.lastName()),
                optional(request.phoneNumber()), optional(request.avatarUrl()), request.hintQuestion(), answerHash), emailChanged);
        return get(userId);
    }
    private void validate(ProfileUpdateRequest request) {
        String phone = optional(request.phoneNumber());
        if (phone != null) {
            long digits = phone.chars().filter(c -> c >= '0' && c <= '9').count();
            if (!phone.matches("\\+?[0-9][0-9 ()-]*[0-9]") || digits < 7 || digits > 15)
                throw invalid("phoneNumber", "Use 7–15 digits, with an optional leading +, spaces, parentheses, or hyphens.");
        }
        String avatar = optional(request.avatarUrl());
        if (avatar != null) {
            try {
                URI uri = URI.create(avatar);
                if (uri.getHost() == null || uri.getUserInfo() != null
                        || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException failure) {
                throw invalid("avatarUrl", "Use an absolute HTTP or HTTPS URL without embedded credentials.");
            }
        }
        if (request.hintQuestion() != null || request.hintAnswer() != null) {
            if (request.hintQuestion() == null || ProfileHintQuestion.find(request.hintQuestion()).isEmpty())
                throw invalid("hintQuestion", "Select an approved hint question.");
            if (request.hintAnswer() == null || request.hintAnswer().isBlank())
                throw invalid("hintAnswer", "Provide a nonblank hint answer when changing hint details.");
        }
    }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    // Prehashing avoids truncating long UTF-8 answers at BCrypt's 72-byte boundary.
    // Profile hints have no authentication or account-recovery verification endpoint.
    static String answerDigest(String answer) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(answer.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private ProfileException invalid(String field, String message) { return new ProfileException(HttpStatus.BAD_REQUEST, field, message); }
}
