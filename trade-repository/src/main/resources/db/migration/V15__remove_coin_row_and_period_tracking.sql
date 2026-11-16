-- Retain V14 unchanged for existing Flyway checksums. Stop imports before upgrading.
-- Preserve lifecycle snapshots and committed progress before removing row/period storage.
ALTER TABLE coin_order_identity ADD COLUMN source_fields TEXT[];
UPDATE coin_order_identity i SET source_fields=ARRAY[r.raw_client_id, r.raw_isin, r.raw_scheme_name, r.raw_plan, r.raw_transaction_mode, r.raw_settlement_id, r.raw_trade_date, r.raw_ordered_at, r.raw_folio_number, r.raw_amount, r.raw_units, r.raw_nav, r.raw_status, r.raw_exchange_order_id, r.raw_remarks, r.raw_tag]
FROM coin_import_row r WHERE r.import_row_id=i.latest_row_id;
ALTER TABLE coin_order_identity ALTER COLUMN source_fields SET NOT NULL;
ALTER TABLE coin_order_identity ADD CONSTRAINT coin_identity_source_fields
 CHECK (cardinality(source_fields)=16 AND array_position(source_fields,NULL) IS NULL);
ALTER TABLE coin_import_file
 ADD COLUMN processed_count BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN inserted_count BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN updated_count BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN unchanged_count BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN posted_count BIGINT NOT NULL DEFAULT 0;
UPDATE coin_import_file f SET processed_count=r.processed, inserted_count=r.inserted,
 updated_count=r.updated, unchanged_count=r.unchanged, posted_count=r.posted
FROM (SELECT import_file_id, count(*) FILTER(WHERE outcome<>'STAGED') processed,
 count(*) FILTER(WHERE outcome='INSERTED') inserted,
 count(*) FILTER(WHERE outcome='UPDATED') updated,
 count(*) FILTER(WHERE outcome='UNCHANGED') unchanged,
 count(*) FILTER(WHERE posted) posted FROM coin_import_row GROUP BY import_file_id) r
WHERE f.import_file_id=r.import_file_id;
-- The sequential checkpoint cannot represent gaps in legacy committed projection.
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM coin_import_row r JOIN coin_import_file f USING(import_file_id)
            WHERE (r.outcome<>'STAGED') <> (r.record_number<=f.processed_count)) THEN
  RAISE EXCEPTION 'Noncontiguous Coin progress; reconcile before migration';
 END IF;
END $$;
ALTER TABLE coin_import_file ADD CONSTRAINT coin_file_progress CHECK (
 processed_count BETWEEN 0 AND record_count AND inserted_count>=0 AND updated_count>=0
 AND unchanged_count>=0 AND posted_count BETWEEN 0 AND processed_count
 AND processed_count=inserted_count+updated_count+unchanged_count);
ALTER TABLE coin_order_identity DROP COLUMN latest_row_id;
DROP TABLE coin_import_row;
DROP TABLE coin_import_file_fund;
DROP TABLE coin_import_period;
