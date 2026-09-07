-- Run manually while Flyway is disabled, after the V5 authentication schema.
-- Existing rows intentionally remain unassigned and invisible to REST users.
ALTER TABLE mutual_fund_broker_account
    ADD COLUMN owner_user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_broker_account_owner ON mutual_fund_broker_account(owner_user_id);
CREATE INDEX idx_mutual_fund_broker ON mutual_fund(broker_account_id);
CREATE INDEX idx_mutual_fund_txn_fund ON mutual_fund_txn(mutual_fund_id);
CREATE INDEX idx_mutual_fund_value_fund ON mutual_fund_value(mutual_fund_id);
