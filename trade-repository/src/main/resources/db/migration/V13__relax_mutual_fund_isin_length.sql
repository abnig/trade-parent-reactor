-- Accept optional source identifiers such as N/A without a fixed length.
ALTER TABLE mutual_fund DROP CONSTRAINT mutual_fund_isin_length;
ALTER TABLE mutual_fund ALTER COLUMN isin TYPE TEXT;
