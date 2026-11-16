# Batch architecture

Source baseline: `719606a`, 16 November 2026. Legacy trade/ledger pipelines use JPA;
Coin is an unfinished separate pipeline. Migrations belong to `trade-repository`.

## Entry points and scan boundaries

| Entry point | Current role |
|---|---|
| [com.trading.kite.TradeBatchApplication](src/main/java/com/trading/kite/TradeBatchApplication.java) | Default Boot JAR main; scans `com.trading` excluding Coin; custom runner invokes trade discovery |
| [com.trading.kite.LedgerBalancesBatchApplication](src/main/java/com/trading/kite/LedgerBalancesBatchApplication.java) | Scans `com.trading.kite`; custom runner invokes ledger discovery; no separate ledger executable classifier |
| [CoinOrderHistoryBatchApplication](src/main/java/com/trading/coin/CoinOrderHistoryBatchApplication.java) | Coin configuration only, no `main`; packaged `coin` classifier is not a working importer |

Both legacy application classes/configurations are discoverable in the legacy scan
graph. Starting either should be treated as potentially running both pipelines;
scan-isolation tests do not execute the runners. `spring.batch.job.enabled=false`
only disables Boot auto-launch, not these `CommandLineRunner` methods.

The [POM](pom.xml) also attaches a `coin-library` classifier for REST, excluding
the Coin launcher and legacy classes. REST independently excludes these components
from scanning to protect IDE launches that expose the full batch output.

## Legacy flow and persistence

```text
configured directory → discover CSVs → asynchronous job per file
  → optional table-wide delete step → read/process/write chunks → job listener
```

[BatchJobService](src/main/java/com/trading/kite/service/BatchJobService.java) and
[LedgerBalancesBatchJobService](src/main/java/com/trading/kite/service/LedgerBalancesBatchJobService.java)
select regular lowercase `.csv` files, optionally recursively. Parameters are
`inputFile`, `truncateFlag` and a millisecond `timestamp`; the timestamp makes
reimports new instances. No file order, deduplication, claiming, archive or restart
contract is implemented. Directory traversal streams are not explicitly closed.

| Pipeline | Job / processing |
|---|---|
| Trade | `csvProcessingJob`: delete step → `csvProcessingStep`; mapper creates `TradeRecord`, processor passes through, writer calls JPA `saveAll` |
| Ledger | `ledgerCsvProcessingJob`: delete step → `ledgerCsvProcessingStep`; processor adds source filename/current time, writer calls JPA `saveAll` into `ledger_records` |

Ledger naming does not imply `ledger_balances` is calculated or written. Step-scoped
readers require an existing `inputFile`. The trade executor uses core 5/max 10,
ledger core 8/max 10; both use queue 25 and `CallerRunsPolicy`. Parallelism is per
file. The shared listener logs status, counts and failures; its heading alone is
not proof of successful committed writes. Runners catch discovery/submission
errors, not asynchronous job failures.

Delete methods execute whole-table JPQL DELETE when the flag is true. Deletion
commits separately from chunks. Concurrent files with deletion enabled can erase
each other's imported rows. Chunks can remain committed when a later chunk fails.
Both steps skip broad `Exception` up to their limit, without explicit retry or a
rejected-record artifact. Reimport can duplicate rows because UUIDs are internal
keys, not source identity.

Sources: [BatchConfiguration](src/main/java/com/trading/kite/batch/config/BatchConfiguration.java),
[LedgerBalancesBatchConfiguration](src/main/java/com/trading/kite/batch/config/LedgerBalancesBatchConfiguration.java),
[trade writer](src/main/java/com/trading/kite/batch/writer/TradeRecordItemWriter.java),
[ledger writer](src/main/java/com/trading/kite/batch/writer/LedgerRecordItemWriter.java).

## CSV schemas

| Pipeline | Exact positional fields / conversions |
|---|---|
| Trade | `symbol,isin,trade_date,exchange,segment,series,trade_type,auction,quantity,price,trade_id,order_id,order_execution_time`; text trim-to-null, date `yyyy-MM-dd`, execution `yyyy-MM-dd'T'HH:mm:ss`, auction blank → false, quantity Integer, price BigDecimal, trade ID Long |
| Ledger | `particulars,posting_date,cost_center,voucher_type,debit,credit,net_balance`; text trim-to-null, date `yyyy-MM-dd`, three decimals as BigDecimal |

There is no header skip/validation policy: a header is parsed as data and can consume
a skip. Trade type must match lowercase `buy`/`sell`; fractional quantity fails Java
Integer conversion despite SQL decimal storage. Ledger net balance is parsed even
though SQL permits NULL; voucher type is required by the model but nullable in SQL.
These mappers do not guarantee original text fidelity.

Sources: [TradeFieldSetMapper](src/main/java/com/trading/kite/batch/reader/mapper/TradeFieldSetMapper.java),
[LedgerRecordFieldSetMapper](src/main/java/com/trading/kite/batch/reader/mapper/LedgerRecordFieldSetMapper.java).

## Configuration and verification limits

| Properties | Current defaults |
|---|---|
| `batch.csv.input-file-path`, `batch.csv.ledger-input-file-path` | Separate developer-local directories, but both use the same `CSV_INPUT_FILE` override |
| `batch.truncate-flag`, `batch.ledger-truncate-flag` | FALSE |
| `batch.recursive-flag`, `batch.ledger-recursive-flag` | FALSE |
| `batch.chunk-size`, `batch.ledger-chunk-size` | 1000 |
| `batch.skip-limit`, `batch.ledger-skip-limit` | 100 / 3 |
| Error/archive path properties | Declared but unused; archive overrides also use `CSV_ERROR_FILE` |

See [application.properties](src/main/resources/application.properties). Flyway,
Hibernate DDL, SQL initialization and Batch schema initialization are disabled.
Legacy configuration injects the framework `JobRepository` without explicitly
opting into JDBC metadata; V2 tables alone do not prove durable restart behavior.
The historical Batch 6 dependency inspection found a resourceless default. No
legacy runtime/restart validation was performed for this reconciliation.

The one current [CoinComponentTest](src/test/java/com/trading/coin/CoinComponentTest.java)
checks that legacy scans discover both configurations while excluding Coin. It
provides no CSV persistence, skip/restart, concurrency or deletion coverage.
[Coin status](../docs/COIN_BATCH_RUNBOOK.md) owns replacement-pipeline gaps;
[Verification](../docs/project-understanding/VERIFICATION.md) owns execution history.
