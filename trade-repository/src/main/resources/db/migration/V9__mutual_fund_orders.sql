-- Unknown security metadata stays null for existing funds.
ALTER TABLE mutual_fund ADD COLUMN isin VARCHAR(12);
ALTER TABLE mutual_fund ADD COLUMN plan TEXT;
ALTER TABLE mutual_fund ADD CONSTRAINT mutual_fund_isin_length
    CHECK (isin IS NULL OR CHAR_LENGTH(isin) = 12);

-- Preserve the existing 15 integer digits while retaining six fractional digits.
ALTER TABLE mutual_fund_txn ALTER COLUMN units TYPE NUMERIC(21, 6);
ALTER TABLE mutual_fund_txn ALTER COLUMN avg_price TYPE NUMERIC(21, 6);

-- This key supports a same-fund transaction reference, not source deduplication.
ALTER TABLE mutual_fund_txn ADD CONSTRAINT uq_mutual_fund_txn_fund
    UNIQUE (mutual_fund_txn_id, mutual_fund_id);

-- Source order facts are independent of posted transactions and valuations.
-- Zero units/NAV can mean unavailable allotment details, irrespective of status.
CREATE TABLE mutual_fund_order (
    mutual_fund_order_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL PRIMARY KEY,
    mutual_fund_id BIGINT NOT NULL REFERENCES mutual_fund(mutual_fund_id),
    mutual_fund_txn_id BIGINT,
    txn_type VARCHAR,
    settlement_id TEXT,
    trade_date DATE,
    ordered_at TIME WITHOUT TIME ZONE,
    folio_number TEXT,
    amount NUMERIC(18, 2),
    units NUMERIC(21, 6),
    avg_price NUMERIC(21, 6),
    status TEXT,
    exchange_order_id TEXT,
    remarks TEXT,
    tag TEXT,
    create_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_mutual_fund_order_txn FOREIGN KEY (mutual_fund_txn_id, mutual_fund_id)
        REFERENCES mutual_fund_txn(mutual_fund_txn_id, mutual_fund_id)
);

-- Owner-scoped per-fund pagination and transaction foreign-key lookups.
CREATE INDEX idx_mutual_fund_order_fund ON mutual_fund_order(mutual_fund_id, mutual_fund_order_id);
CREATE INDEX idx_mutual_fund_order_txn ON mutual_fund_order(mutual_fund_txn_id, mutual_fund_id);
