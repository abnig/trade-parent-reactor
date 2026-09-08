ALTER TABLE users ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN recovery_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE recovery_answer (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    question_id INTEGER NOT NULL CHECK (question_id BETWEEN 1 AND 3),
    answer_hash VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, question_id)
);
CREATE TABLE recovery_challenge (
    challenge_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    account_key VARCHAR(64) NOT NULL,
    recovery_version BIGINT NOT NULL,
    question_one INTEGER NOT NULL CHECK (question_one BETWEEN 1 AND 3),
    question_two INTEGER NOT NULL CHECK (question_two BETWEEN 1 AND 3),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (question_one <> question_two)
);
CREATE INDEX idx_recovery_challenge_user ON recovery_challenge(user_id);
CREATE INDEX idx_recovery_challenge_expiry ON recovery_challenge(expires_at);
CREATE TABLE password_reset_token (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_password_reset_expiry ON password_reset_token(expires_at);
CREATE TABLE recovery_rate_limit (
    bucket_key VARCHAR(100) PRIMARY KEY,
    attempts INTEGER NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_recovery_rate_expiry ON recovery_rate_limit(expires_at);
