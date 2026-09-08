-- Apply after V7. Resolve case-insensitive duplicate emails before execution.
ALTER TABLE user_details ADD COLUMN hint_question_id INTEGER;
ALTER TABLE user_details ADD COLUMN hint_answer_hash VARCHAR(100);
ALTER TABLE user_details ADD CONSTRAINT chk_profile_hint_pair CHECK (
    (hint_question_id IS NULL AND hint_answer_hash IS NULL)
    OR (hint_question_id IS NOT NULL AND hint_question_id BETWEEN 1 AND 3
        AND hint_answer_hash IS NOT NULL)
);
CREATE UNIQUE INDEX idx_users_email_case_insensitive ON users (LOWER(email));
