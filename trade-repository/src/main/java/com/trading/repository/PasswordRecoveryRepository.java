package com.trading.repository;

import com.trading.model.RecoveryAccount;
import com.trading.model.RecoveryChallenge;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public interface PasswordRecoveryRepository {
    record UsernameReminder(String email, String username) {}
    Optional<UsernameReminder> usernameByEmail(String email);
    Optional<RecoveryAccount> account(String username);
    Optional<RecoveryAccount> account(long userId);
    Map<Integer, String> answers(long userId);
    boolean enroll(long userId, long expectedCredentialVersion, Map<Integer, String> hashes);
    boolean takeRatePermit(String bucket, Instant now, Instant until, int maximum);
    void createChallenge(RecoveryChallenge challenge, Instant expires);
    Optional<RecoveryChallenge> attempt(String hash, Instant now);
    boolean issueToken(RecoveryChallenge challenge, String tokenHash, Instant now, Instant expires);
    void revokeToken(String tokenHash);
    boolean complete(String tokenHash, String passwordHash, Instant now);
    void purgeExpired(Instant now);
}
