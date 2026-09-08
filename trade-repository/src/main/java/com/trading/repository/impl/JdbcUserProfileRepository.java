package com.trading.repository.impl;

import com.trading.model.ProfileChanges;
import com.trading.model.ProfileCredentials;
import com.trading.model.UserProfile;
import com.trading.repository.UserProfileRepository;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcUserProfileRepository implements UserProfileRepository {
    private final JdbcTemplate jdbc;
    public JdbcUserProfileRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<UserProfile> find(long userId) {
        return jdbc.query("""
                SELECT u.id, u.username, u.email, d.first_name, d.last_name, d.phone_number,
                       d.avatar_url, d.hint_question_id, (d.hint_answer_hash IS NOT NULL) AS hint_answer_set
                FROM users u LEFT JOIN user_details d ON d.user_id = u.id
                WHERE u.id = ?
                """, (rs, row) -> new UserProfile(rs.getLong("id"), rs.getString("username"), rs.getString("email"),
                rs.getString("first_name"), rs.getString("last_name"), rs.getString("phone_number"),
                rs.getString("avatar_url"), rs.getObject("hint_question_id", Integer.class), rs.getBoolean("hint_answer_set")),
                userId).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ProfileCredentials> lockCredentials(long userId) {
        return jdbc.query("""
                SELECT password, credential_version, enabled, account_non_expired, account_non_locked, credentials_non_expired
                FROM users WHERE id = ? FOR UPDATE
                """, (rs, row) -> new ProfileCredentials(rs.getString("password"), rs.getLong("credential_version"),
                rs.getBoolean("enabled") && rs.getBoolean("account_non_expired") && rs.getBoolean("account_non_locked")
                        && rs.getBoolean("credentials_non_expired")), userId).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(long userId, ProfileChanges changes, boolean emailChanged) {
        // The service holds the user row lock throughout password confirmation and writes.
        jdbc.update("UPDATE users SET email = ? WHERE id = ?", changes.email(), userId);
        jdbc.update("INSERT INTO user_details(user_id) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM user_details WHERE user_id = ?)", userId, userId);
        boolean changeHint = changes.hintAnswerHash() != null;
        jdbc.update("""
                UPDATE user_details SET first_name = ?, last_name = ?, phone_number = ?, avatar_url = ?,
                    hint_question_id = CASE WHEN ? THEN ? ELSE hint_question_id END,
                    hint_answer_hash = CASE WHEN ? THEN ? ELSE hint_answer_hash END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """, changes.firstName(), changes.lastName(), changes.phoneNumber(), changes.avatarUrl(),
                changeHint, changes.hintQuestion(), changeHint, changes.hintAnswerHash(), userId);
        if (emailChanged) {
            // A link delivered to the previous mailbox must not survive an email change.
            jdbc.update("UPDATE users SET recovery_version = recovery_version + 1 WHERE id = ?", userId);
            jdbc.update("DELETE FROM recovery_challenge WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM password_reset_token WHERE user_id = ?", userId);
        }
    }
}
