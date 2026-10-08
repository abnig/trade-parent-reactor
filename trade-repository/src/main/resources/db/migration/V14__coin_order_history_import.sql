-- New Coin importer only. Existing migration history must be reconciled before deployment.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE coin_import_file (
 import_file_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 owner_user_id BIGINT NOT NULL REFERENCES users(id),
 broker_account_id BIGINT NOT NULL REFERENCES mutual_fund_broker_account(broker_account_id),
 source_system TEXT NOT NULL DEFAULT 'ZERODHA_COIN' CHECK (source_system = 'ZERODHA_COIN'),
 content_sha256 VARCHAR(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
 byte_count BIGINT NOT NULL CHECK (byte_count >= 0),
 raw_header BYTEA NOT NULL,
 managed_file_ref TEXT NOT NULL,
 parser_version TEXT NOT NULL, policy_version TEXT NOT NULL,
 date_format TEXT NOT NULL, posting_policy TEXT NOT NULL,
 period_start DATE NOT NULL, period_end DATE NOT NULL,
 configuration_snapshot JSONB NOT NULL,
 fund_mapping JSONB NOT NULL,
 import_status TEXT NOT NULL CHECK (import_status IN ('RESERVED','STAGED','COMPLETED','FAILED')),
 record_count BIGINT NOT NULL CHECK (record_count > 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at TIMESTAMPTZ,
 UNIQUE (owner_user_id, broker_account_id, source_system, content_sha256),
 UNIQUE (import_file_id, owner_user_id),
 CHECK (isfinite(period_start) AND isfinite(period_end) AND period_start <= period_end)
);

CREATE TABLE coin_import_period (
 import_period_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 owner_user_id BIGINT NOT NULL REFERENCES users(id),
 mutual_fund_id BIGINT NOT NULL REFERENCES mutual_fund(mutual_fund_id),
 import_file_id BIGINT NOT NULL,
 period_start DATE NOT NULL, period_end DATE NOT NULL,
 period_status TEXT NOT NULL CHECK (period_status IN ('RESERVED','LOADED','BLOCKED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 loaded_at TIMESTAMPTZ,
 FOREIGN KEY (import_file_id, owner_user_id) REFERENCES coin_import_file(import_file_id, owner_user_id),
 UNIQUE (import_file_id, mutual_fund_id),
 UNIQUE (import_period_id, mutual_fund_id, owner_user_id),
 CHECK (isfinite(period_start) AND isfinite(period_end) AND period_start <= period_end),
 CHECK (period_status <> 'LOADED' OR loaded_at IS NOT NULL),
 CONSTRAINT coin_import_period_no_overlap EXCLUDE USING gist (
   owner_user_id WITH =, mutual_fund_id WITH =,
   daterange(period_start, period_end, '[]') WITH &&)
);

-- Later completion snapshots reuse coverage; they cannot reserve overlapping coverage anew.
CREATE TABLE coin_import_file_fund (
 import_file_id BIGINT NOT NULL,
 mutual_fund_id BIGINT NOT NULL,
 owner_user_id BIGINT NOT NULL,
 import_period_id BIGINT NOT NULL,
 reconciliation BOOLEAN NOT NULL,
 PRIMARY KEY (import_file_id, mutual_fund_id),
 FOREIGN KEY (import_file_id, owner_user_id) REFERENCES coin_import_file(import_file_id, owner_user_id),
 FOREIGN KEY (import_period_id, mutual_fund_id, owner_user_id)
   REFERENCES coin_import_period(import_period_id, mutual_fund_id, owner_user_id)
);

CREATE TABLE coin_import_row (
 import_row_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 import_file_id BIGINT NOT NULL REFERENCES coin_import_file(import_file_id),
 record_number BIGINT NOT NULL CHECK (record_number > 0),
 resolved_fund_id BIGINT NOT NULL,
 line_start BIGINT NOT NULL, line_end BIGINT NOT NULL,
 byte_start BIGINT NOT NULL, byte_end BIGINT NOT NULL,
 raw_record BYTEA NOT NULL,
 row_sha256 VARCHAR(64) NOT NULL CHECK (row_sha256 ~ '^[0-9a-f]{64}$'),
 raw_client_id TEXT NOT NULL,
 raw_isin TEXT NOT NULL,
 raw_scheme_name TEXT NOT NULL,
 raw_plan TEXT NOT NULL,
 raw_transaction_mode TEXT NOT NULL,
 raw_settlement_id TEXT NOT NULL,
 raw_trade_date TEXT NOT NULL,
 raw_ordered_at TEXT NOT NULL,
 raw_folio_number TEXT NOT NULL,
 raw_amount TEXT NOT NULL,
 raw_units TEXT NOT NULL,
 raw_nav TEXT NOT NULL,
 raw_status TEXT NOT NULL,
 raw_exchange_order_id TEXT NOT NULL,
 raw_remarks TEXT NOT NULL,
 raw_tag TEXT NOT NULL,
 outcome TEXT NOT NULL DEFAULT 'STAGED' CHECK (outcome IN ('STAGED','INSERTED','UPDATED','UNCHANGED')),
 mutual_fund_order_id BIGINT REFERENCES mutual_fund_order(mutual_fund_order_id),
 posted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE (import_file_id, record_number),
 FOREIGN KEY (import_file_id, resolved_fund_id) REFERENCES coin_import_file_fund(import_file_id, mutual_fund_id),
 CHECK (line_start > 0 AND line_end >= line_start AND byte_start >= 0 AND byte_end > byte_start),
 CHECK ((outcome = 'STAGED') = (mutual_fund_order_id IS NULL))
);

-- A bounded digest is indexed; every lookup also compares exact source text.
CREATE TABLE coin_order_identity (
 owner_user_id BIGINT NOT NULL REFERENCES users(id),
 broker_account_id BIGINT NOT NULL REFERENCES mutual_fund_broker_account(broker_account_id),
 exchange_id_sha256 VARCHAR(64) NOT NULL,
 exchange_order_id TEXT NOT NULL CHECK (exchange_order_id <> ''),
 mutual_fund_id BIGINT NOT NULL REFERENCES mutual_fund(mutual_fund_id),
 mutual_fund_order_id BIGINT NOT NULL UNIQUE REFERENCES mutual_fund_order(mutual_fund_order_id),
 latest_row_id BIGINT NOT NULL REFERENCES coin_import_row(import_row_id),
 source_status TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (owner_user_id, broker_account_id, exchange_id_sha256),
 CHECK (exchange_id_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE INDEX coin_import_file_owner_status ON coin_import_file(owner_user_id, broker_account_id, import_status);
CREATE INDEX coin_import_row_outcome ON coin_import_row(import_file_id, outcome, record_number);
CREATE INDEX coin_import_period_file ON coin_import_period(import_file_id);
CREATE INDEX coin_import_file_fund_period ON coin_import_file_fund(import_period_id);
