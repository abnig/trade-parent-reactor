# Coin source fields and retained schema

Source baseline: `719606a`, 16 November 2026. The importer is unfinished; this
mapping describes intended destinations visible in the draft processor, not a
working import. See [current status](docs/COIN_BATCH_RUNBOOK.md).

## Field mapping

[CoinRecord](trade-batch/src/main/java/com/trading/coin/batch/model/inbound/CoinRecord.java)
contains these fields. The configured tokenizer uses camelCase property names in
this positional order; [CoinRecordFieldSetMapper](trade-batch/src/main/java/com/trading/coin/batch/reader/mapper/CoinRecordFieldSetMapper.java)
still returns an empty record.

| CSV field | Java type | Intended destination / unresolved behavior |
|---|---|---|
| `client_id` | String | Broker `accountId`; owner comes from trusted context |
| `isin` | String | Fund `isin`; lookup must be owner/account appropriate |
| `scheme_name` | String | Fund `mutualFundName` |
| `plan` | String | Fund `plan` |
| `transaction_mode` | String | Transaction `BUY`/`SELL`; validate source mapping |
| `settlement_id` | String | Transaction `settlementId` |
| `trade_date` | LocalDate | Draft combines with `orderedAt` for `txnDate`; policy unresolved |
| `ordered_at` | LocalTime | Time component; source date/time convention unresolved |
| `folio_number` | String | Fund `folioNumber` |
| `amount` | BigDecimal | Transaction amount |
| `units` | BigDecimal | Transaction units |
| `nav` | BigDecimal | Transaction average price |
| `status` | String | Transaction status; posting eligibility needs explicit policy |
| `exchange_order_id` | String | Transaction metadata; uniqueness/replay not implemented |
| `remarks` | String | Transaction remarks |
| `tag` | String | Transaction tag; preserve text rather than assuming parsed JSON |

## Schema evolution

- V9 added `mutual_fund_order`, fund ISIN/plan and six-place transaction units/NAV.
- V10 moved folio to the fund, rejecting conflicting source folios.
- V11 moved status, exchange ID, remarks, tag and settlement ID to linked
  transactions. Unlinked metadata/conflicting values reject migration; it does
  not invent transactions.
- V12 enforces exact broker/account/owner uniqueness; NULL owners remain distinct.
- V13 permits arbitrary nullable ISIN text, including `N/A`.
- V14 introduced Coin audit/identity/row/period tables. V15 preserves identity
  snapshots and file counters, then drops the three row/period tracking tables.

The order table, `coin_import_file` and `coin_order_identity` remain in the intended
SQL schema. The Java order model/repositories and Coin import repository were
removed. No order HTTP API or active replay/lifecycle implementation exists.

[Data model](docs/project-understanding/DATA_MODEL.md) owns column types, constraints
and snapshot provenance; [migration guidance](trade-repository/src/main/resources/db/migration/README.md)
owns upgrade rules. Amounts use numeric(18,2), units/NAV numeric(21,6); string IDs
must not be coerced to numbers. Existing manual transaction status text does not
filter portfolio totals. [Rewrite requirements](docs/COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md)
list the remaining policy and verification work.
