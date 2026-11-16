# Coin rewrite requirements and decisions

Reconciled with `719606a`, 16 November 2026. This is a completion checklist, not an
implemented contract. [Current status](COIN_BATCH_RUNBOOK.md) describes the scaffold;
[Coin field mapping](../coin-order-schema.md) records all 16 source fields.

## Retained decisions

| Date | Decision and present implication |
|---|---|
| 8 October | User requested a Coin CSV batch job, validation of all input records, component tests and PROCESSING-to-COMPLETE upserts. The former implementation was subsequently removed; these behaviors are not currently delivered. |
| 8 October, superseded 13 October | User/fund period tracking was first requested, then removal of `coin_import_file_fund`, `coin_import_period` and `coin_import_row` was requested. V15 encodes their removal. Do not restore overlap reservations as an assumed requirement. |
| 9 October | User requested integration with `/api/mutual-fund-txns/zerodha-upload`. The current UI retains the old options form, but REST integration is now a TODO. |
| 13 October | User requested a POJO for the 16 fields. The current `CoinRecord` lives under the batch inbound-model package. |

Historical implementation details and executed test counts are summarized in
[Learning log](project-understanding/LEARNING_LOG.md) and
[Verification](project-understanding/VERIFICATION.md). Git history retains the full
superseded specification; its old launch flags and receipts are not current APIs.

## Completion criteria

1. Parse every source field with explicit header, quoting, empty-value, decimal,
   date and time rules. Preserve string identifiers, leading zeros, `N/A`, remarks
   and JSON-shaped tag text; do not silently infer dates or financial facts.
2. Obtain the owner from the authenticated session or an explicitly authorized
   standalone invocation. Constrain account/fund resolution to that owner and
   define behavior for missing or ambiguous masters.
3. Wire the reader, processor, multi-output dispatch, transactional persistence,
   completion reporting and an actual Job/Step. Keep SQL implementations in
   `trade-repository`, and propagate generated parent IDs to children.
4. Define order status transitions, posting eligibility, duplicate identity,
   conflicting updates and restart behavior before claiming replay safety.
5. Reconnect REST and UI with a documented response/error contract. Decide whether
   to preserve the earlier options/receipt/file-only compatibility behavior.
6. Keep Coin and legacy launchers isolated in both directions, including IDE
   workspace resolution. Provide a working entry point only if standalone use
   remains required.
7. Reconcile migration prerequisites before deployment. Do not edit V14/V15 or
   infer applied migration history from table presence.

## Decisions still needed for this rewrite

- Create missing broker/fund masters or require explicit reconciliation? The draft
  processor attempts creation, but does not constitute an approved working policy.
- Which date is authoritative when order/trade/completion dates differ, and what
  timezone/timing assumptions are permitted?
- Which statuses/modes may create portfolio transactions, and how are unknown or
  incomplete facts retained without affecting totals?
- What is the source order identity and conflict/replay contract now that row and
  period tracking have been removed?
- Should the retained order/audit SQL tables be reused, or changed through a
  separately reviewed migration? Their existence is not evidence of active code.

## Required verification

Use synthetic fixtures and disposable PostgreSQL. Cover field fidelity, malformed
headers/quoting, exact decimals/scale, unknown status/mode, missing dates, owner
isolation, ambiguous masters, parent IDs, same-fund links, duplicate/concurrent
requests, lifecycle updates, rollback and restart. Verify both REST multipart/CSRF
behavior and UI response handling. No posting should occur without the chosen
policy; rejected input must not partially mutate data beyond a documented commit
boundary. Distinguish planned tests from the small current suite.
