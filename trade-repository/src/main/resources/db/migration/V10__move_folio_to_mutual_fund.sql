-- Apply atomically with order/fund writers stopped, after V9.
-- ISIN and plan already belong to mutual_fund as of V9.
ALTER TABLE mutual_fund ADD COLUMN folio_number TEXT;

-- A scalar subquery deliberately fails if a fund has multiple distinct folios.
-- Resolve those funds explicitly before retrying, never choose an arbitrary folio.
-- Preserve identifiers verbatim, including leading zeros, slashes and blanks.
UPDATE mutual_fund
SET folio_number = (
    SELECT DISTINCT o.folio_number
    FROM mutual_fund_order o
    WHERE o.mutual_fund_id = mutual_fund.mutual_fund_id
      AND o.folio_number IS NOT NULL
);

ALTER TABLE mutual_fund_order DROP COLUMN folio_number;
