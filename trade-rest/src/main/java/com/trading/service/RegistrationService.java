package com.trading.service;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import com.trading.dto.RegisterRequest;
import com.trading.repository.UserRegistrationRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {
    private final UserRegistrationRepository repository;
    private final com.trading.recovery.PasswordRecoveryService recovery;
    private final com.trading.repository.PasswordRecoveryRepository recoveryRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    public RegistrationService(UserRegistrationRepository repository, com.trading.recovery.PasswordRecoveryService recovery,
            com.trading.repository.PasswordRecoveryRepository recoveryRepository) {
        this.repository = repository; this.recovery = recovery; this.recoveryRepository = recoveryRepository;
    }

    @org.springframework.transaction.annotation.Transactional
    public RegisteredUser register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRegistrationException("Password must not exceed 72 UTF-8 bytes.");
        }
        var answers = request.recoveryAnswers() == null ? null : recovery.hashAnswers(request.recoveryAnswers());
        long id = repository.register(request.username(), request.email(), encoder.encode(request.password()),
                request.firstName(), request.lastName(), request.phoneNumber(), request.avatarUrl());
        if (answers != null && !recoveryRepository.enroll(id, 0, answers))
            throw new IllegalStateException("Unable to enroll recovery answers.");
        return new RegisteredUser(id, request.username(), request.email(), Set.of("ROLE_USER"));
    }

    public record RegisteredUser(long id, String username, String email, Set<String> roles) {}

    public static class InvalidRegistrationException extends RuntimeException {
        public InvalidRegistrationException(String message) { super(message); }
    }
}
