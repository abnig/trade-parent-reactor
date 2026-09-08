package com.trading.repository.impl;

import com.trading.model.RecoveryAccount;
import com.trading.model.RecoveryChallenge;
import com.trading.repository.PasswordRecoveryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPasswordRecoveryRepository implements PasswordRecoveryRepository {
    private final JdbcTemplate jdbc;
    private static final String ACCOUNT = "SELECT id, email, password, enabled, account_non_locked, account_non_expired, credentials_non_expired, credential_version, recovery_version FROM users";
    private static final RowMapper<RecoveryAccount> ACCOUNT_MAPPER = (rs, row) -> new RecoveryAccount(
            rs.getLong("id"), rs.getString("email"), rs.getString("password"),
            rs.getBoolean("enabled") && rs.getBoolean("account_non_locked") && rs.getBoolean("account_non_expired")
                && rs.getBoolean("credentials_non_expired"), rs.getLong("credential_version"), rs.getLong("recovery_version"));

    public JdbcPasswordRecoveryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<RecoveryAccount> account(String username) {
        return jdbc.query(ACCOUNT + " WHERE username = ?", ACCOUNT_MAPPER, username).stream().findFirst();
    }
    public Optional<RecoveryAccount> account(long id) {
        return jdbc.query(ACCOUNT + " WHERE id = ?", ACCOUNT_MAPPER, id).stream().findFirst();
    }
    private Optional<RecoveryAccount> lock(long id) {
        return jdbc.query(ACCOUNT + " WHERE id = ? FOR UPDATE", ACCOUNT_MAPPER, id).stream().findFirst();
    }
    public Map<Integer, String> answers(long userId) {
        Map<Integer, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT question_id, answer_hash FROM recovery_answer WHERE user_id = ?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(rs.getInt(1), rs.getString(2)), userId);
        return result;
    }
    @Transactional
    public boolean enroll(long userId, long expectedCredentialVersion, Map<Integer, String> hashes) {
        var account = lock(userId).orElse(null);
        if (account == null || !account.eligible() || account.credentialVersion() != expectedCredentialVersion) return false;
        jdbc.update("DELETE FROM recovery_answer WHERE user_id = ?", userId);
        hashes.forEach((question, hash) -> jdbc.update("INSERT INTO recovery_answer(user_id,question_id,answer_hash) VALUES(?,?,?)", userId, question, hash));
        jdbc.update("UPDATE users SET recovery_version = recovery_version + 1 WHERE id = ?", userId);
        clearRecovery(userId);
        return true;
    }
    // This method runs outside a surrounding transaction: a conflicting first insert must
    // not poison a PostgreSQL transaction. The conditional UPDATE is the atomic permit.
    public boolean takeRatePermit(String bucket, Instant now, Instant until, int maximum) {
        try {
            jdbc.update("INSERT INTO recovery_rate_limit(bucket_key,attempts,expires_at) VALUES(?,0,?)", bucket, Timestamp.from(until));
        } catch (DuplicateKeyException ignored) { /* Existing bucket. */ }
        return jdbc.update("""
                UPDATE recovery_rate_limit
                SET attempts = CASE WHEN expires_at <= ? THEN 1 ELSE attempts + 1 END,
                    expires_at = CASE WHEN expires_at <= ? THEN ? ELSE expires_at END
                WHERE bucket_key = ? AND (expires_at <= ? OR attempts < ?)
                """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(until), bucket, Timestamp.from(now), maximum) == 1;
    }
    public void createChallenge(RecoveryChallenge c, Instant expires) {
        jdbc.update("""
                INSERT INTO recovery_challenge(challenge_hash,user_id,account_key,recovery_version,question_one,question_two,expires_at)
                VALUES(?,?,?,?,?,?,?)
                """, c.hash(), c.userId(), c.accountKey(), c.recoveryVersion(), c.questionOne(), c.questionTwo(), Timestamp.from(expires));
    }
    @Transactional
    public Optional<RecoveryChallenge> attempt(String hash, Instant now) {
        if (jdbc.update("UPDATE recovery_challenge SET attempts = attempts + 1 WHERE challenge_hash = ? AND expires_at > ? AND consumed = FALSE AND attempts < 5",
                hash, Timestamp.from(now)) != 1) return Optional.empty();
        return jdbc.query("SELECT * FROM recovery_challenge WHERE challenge_hash = ?", (rs, row) ->
                new RecoveryChallenge(rs.getString("challenge_hash"), rs.getObject("user_id", Long.class), rs.getString("account_key"),
                        rs.getLong("recovery_version"), rs.getInt("question_one"), rs.getInt("question_two")), hash).stream().findFirst();
    }
    @Transactional
    public boolean issueToken(RecoveryChallenge c, String tokenHash, Instant now, Instant expires) {
        if (c.userId() == null) return false;
        var account = lock(c.userId()).orElse(null);
        if (account == null || !account.eligible() || account.recoveryVersion() != c.recoveryVersion()) return false;
        if (jdbc.update("UPDATE recovery_challenge SET consumed = TRUE WHERE challenge_hash = ? AND consumed = FALSE AND expires_at > ?",
                c.hash(), Timestamp.from(now)) != 1) return false;
        jdbc.update("DELETE FROM password_reset_token WHERE user_id = ?", c.userId());
        jdbc.update("INSERT INTO password_reset_token(token_hash,user_id,expires_at) VALUES(?,?,?)", tokenHash, c.userId(), Timestamp.from(expires));
        return true;
    }
    public void revokeToken(String tokenHash) {
        jdbc.update("DELETE FROM password_reset_token WHERE token_hash = ?", tokenHash);
    }
    @Transactional
    public boolean complete(String tokenHash, String passwordHash, Instant now) {
        var ids = jdbc.queryForList("SELECT user_id FROM password_reset_token WHERE token_hash = ?", Long.class, tokenHash);
        if (ids.isEmpty()) return false;
        long id = ids.getFirst();
        var account = lock(id).orElse(null);
        if (account == null || !account.eligible()) return false;
        if (jdbc.update("DELETE FROM password_reset_token WHERE token_hash = ? AND expires_at > ?", tokenHash, Timestamp.from(now)) != 1) return false;
        jdbc.update("UPDATE users SET password = ?, credential_version = credential_version + 1, recovery_version = recovery_version + 1 WHERE id = ?", passwordHash, id);
        clearRecovery(id);
        return true;
    }
    private void clearRecovery(long id) {
        jdbc.update("DELETE FROM recovery_challenge WHERE user_id = ?", id);
        jdbc.update("DELETE FROM password_reset_token WHERE user_id = ?", id);
    }
    public void purgeExpired(Instant now) {
        jdbc.update("DELETE FROM recovery_challenge WHERE expires_at <= ?", Timestamp.from(now));
        jdbc.update("DELETE FROM password_reset_token WHERE expires_at <= ?", Timestamp.from(now));
        jdbc.update("DELETE FROM recovery_rate_limit WHERE expires_at <= ?", Timestamp.from(now.minusSeconds(3600)));
    }
}
