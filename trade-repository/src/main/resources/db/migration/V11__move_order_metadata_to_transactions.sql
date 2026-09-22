-- Apply after V10 in one transaction with order/transaction writers stopped.
-- Never invent posted transactions for unlinked orders just to store metadata.
ALTER TABLE mutual_fund_order ADD CONSTRAINT order_metadata_requires_transaction
    CHECK (mutual_fund_txn_id IS NOT NULL OR
           (status IS NULL AND exchange_order_id IS NULL AND remarks IS NULL
            AND tag IS NULL AND settlement_id IS NULL));

ALTER TABLE mutual_fund_txn ADD COLUMN status TEXT;
ALTER TABLE mutual_fund_txn ADD COLUMN exchange_order_id TEXT;
ALTER TABLE mutual_fund_txn ADD COLUMN remarks TEXT;
ALTER TABLE mutual_fund_txn ADD COLUMN tag TEXT;
ALTER TABLE mutual_fund_txn ADD COLUMN settlement_id TEXT;

-- Preserve text verbatim. Each scalar subquery rejects conflicting non-null
-- values for one transaction rather than arbitrarily selecting a source order.
UPDATE mutual_fund_txn
SET status = (
        SELECT DISTINCT o.status FROM mutual_fund_order o
        WHERE o.mutual_fund_txn_id = mutual_fund_txn.mutual_fund_txn_id
          AND o.mutual_fund_id = mutual_fund_txn.mutual_fund_id
          AND o.status IS NOT NULL
    ),
    exchange_order_id = (
        SELECT DISTINCT o.exchange_order_id FROM mutual_fund_order o
        WHERE o.mutual_fund_txn_id = mutual_fund_txn.mutual_fund_txn_id
          AND o.mutual_fund_id = mutual_fund_txn.mutual_fund_id
          AND o.exchange_order_id IS NOT NULL
    ),
    remarks = (
        SELECT DISTINCT o.remarks FROM mutual_fund_order o
        WHERE o.mutual_fund_txn_id = mutual_fund_txn.mutual_fund_txn_id
          AND o.mutual_fund_id = mutual_fund_txn.mutual_fund_id
          AND o.remarks IS NOT NULL
    ),
    tag = (
        SELECT DISTINCT o.tag FROM mutual_fund_order o
        WHERE o.mutual_fund_txn_id = mutual_fund_txn.mutual_fund_txn_id
          AND o.mutual_fund_id = mutual_fund_txn.mutual_fund_id
          AND o.tag IS NOT NULL
    ),
    settlement_id = (
        SELECT DISTINCT o.settlement_id FROM mutual_fund_order o
        WHERE o.mutual_fund_txn_id = mutual_fund_txn.mutual_fund_txn_id
          AND o.mutual_fund_id = mutual_fund_txn.mutual_fund_id
          AND o.settlement_id IS NOT NULL
    );

ALTER TABLE mutual_fund_order DROP CONSTRAINT order_metadata_requires_transaction;
ALTER TABLE mutual_fund_order DROP COLUMN status;
ALTER TABLE mutual_fund_order DROP COLUMN exchange_order_id;
ALTER TABLE mutual_fund_order DROP COLUMN remarks;
ALTER TABLE mutual_fund_order DROP COLUMN tag;
ALTER TABLE mutual_fund_order DROP COLUMN settlement_id;
