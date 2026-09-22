-- Existing duplicate owned accounts must be resolved before applying this migration.
-- Standard UNIQUE semantics retain support for unassigned (NULL owner) accounts.
ALTER TABLE mutual_fund_broker_account
    ADD CONSTRAINT uq_broker_account_name_account_owner
    UNIQUE (broker_name, account_id, owner_user_id);
