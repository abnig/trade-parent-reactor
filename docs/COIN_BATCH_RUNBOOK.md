# Coin importer: current status

Source-verified at `719606a`, 16 November 2026. **The current Coin importer cannot
perform a complete import.** This replaces obsolete CLI/upload instructions from
the October implementation; dated results remain in [Verification](project-understanding/VERIFICATION.md).

## What exists

| Component | Current behavior |
|---|---|
| [CoinRecord](../trade-batch/src/main/java/com/trading/coin/batch/model/inbound/CoinRecord.java) | Mutable inbound POJO for the 16 source fields; located in `trade-batch` |
| [CoinBatchConfiguration](../trade-batch/src/main/java/com/trading/coin/batch/config/CoinBatchConfiguration.java) | JDBC Batch infrastructure, transaction manager, synchronous executor and a step-scoped positional reader; no Job or Step bean |
| [CoinRecordFileItemReader](../trade-batch/src/main/java/com/trading/coin/batch/reader/CoinRecordFileItemReader.java) | Uses the `inputFile` job parameter and a `FlatFileItemReader` |
| [CoinRecordFieldSetMapper](../trade-batch/src/main/java/com/trading/coin/batch/reader/mapper/CoinRecordFieldSetMapper.java) | TODO: returns a new, unpopulated `CoinRecord` |
| [CoinRecordProcessor](../trade-batch/src/main/java/com/trading/coin/batch/processor/CoinRecordProcessor.java) | Draft broker/ISIN lookup and outbound-object construction using `LOGGED_IN_USER`; not wired into a job |
| [CoinRecordOutboundClassifier](../trade-batch/src/main/java/com/trading/coin/batch/classifier/CoinRecordOutboundClassifier.java) | Chooses a writer from the first item; no complete multi-output dispatch |
| [Fund writer](../trade-batch/src/main/java/com/trading/coin/batch/writer/CoinRecordMutualFundWriter.java), [transaction writer](../trade-batch/src/main/java/com/trading/coin/batch/writer/CoinRecordMutualFundTxnWriter.java) | Print items; no database writes |
| [CoinOrderHistoryBatchApplication](../trade-batch/src/main/java/com/trading/coin/CoinOrderHistoryBatchApplication.java) | Configuration/import annotations only; no `main` method or CLI parsing |
| [Upload controller](../trade-rest/src/main/java/com/trading/controller/MutualFundTxnController.java) | Multipart `file` route remains, but returns `null`; no staging or import invocation |

The former `CoinImportRuntime`, `CoinImportService`, `CoinCommandLine`, managed-file
store, importer repository, options/result types and integration tests were removed.
Old `VALIDATE`, `DRY_RUN`, `IMPORT`, replay and restart instructions do not apply.

## UI and packaging mismatch

[CoinUploadForm](../trade-ui/src/components/CoinUploadForm.jsx) still collects account,
period, date format, posting policy and fund matches. [api.js](../trade-ui/src/api/api.js)
sends `file` plus optional JSON `options` and expects a JSON receipt. REST currently
accepts only the file parameter and implements no receipt or import result. Frontend
mock tests therefore cannot establish working backend uploads.

The [batch POM](../trade-batch/pom.xml) still produces `coin` and `coin-library`
classifiers. REST consumes `coin-library` plus Batch core and excludes the standalone
components from scanning. A packaged Coin artifact is not runnable proof: its
configured class lacks `main`. Do not use legacy batch startup as a Coin substitute.

The upload-service constructor normally receives the configured 10 MB limit. Its
1000 MB fallback exceeds its own 100 MiB guard when that property is absent;
configuration-only startup still depends on supplying a valid limit.

## Remaining implementation work

The [requirements](COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md) separate retained user
decisions from open policy. Source review also found these draft-code gaps:

- The no-matching-account branch dereferences the empty `matchingAccount` and does
  not add its new broker object to the outbound list.
- Transactions do not receive the resolved/new fund ID; parent persistence and
  generated-key propagation are missing.
- Mode/date/status validation, source fidelity, persistence transactions, replay,
  lifecycle handling and error reporting are not implemented end to end.
- Mapper, processor, classifier, writers and upload integration need meaningful
  tests. The sole current Coin test checks legacy component-scan isolation.

These are source findings, not reproduced live imports. No application database
or service was changed during this documentation review.

## Schema and verification

[V14](../trade-repository/src/main/resources/db/migration/V14__coin_order_history_import.sql)
remains immutable. [V15](../trade-repository/src/main/resources/db/migration/V15__remove_coin_row_and_period_tracking.sql)
removes row/period tracking after preserving snapshots and counters. The retained
SQL tables have no current import repository. See [Coin schema notes](../coin-order-schema.md)
and [migration guidance](../trade-repository/src/main/resources/db/migration/README.md).

The 16 November Maven run passed 206 tests, including one Coin scan test; it does
not verify CSV import, persistence, replay or V15 upgrades in this checkpoint.
