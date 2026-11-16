# Coin analysis task template

Use this checklist for a future Coin design review. It does not authorize running
imports, changing a database or treating the old importer as available.

1. Read [current Coin status](COIN_BATCH_RUNBOOK.md),
   [retained decisions](COIN_ORDER_HISTORY_BATCH_REQUIREMENTS.md),
   [field mapping](../coin-order-schema.md), and [project handoff](PROJECT_UNDERSTANDING.md).
   Record the current commit and relevant uncommitted changes.
2. If a source CSV is supplied, inspect its header, quoting, encoding, date/time,
   decimals, blank values and status/mode vocabulary. Retain aggregate findings
   and synthetic examples only; do not copy private records into documentation.
3. Reconcile all 16 fields with current Java types and migrations. Identify loss,
   ambiguity, ownership and parent-link requirements explicitly. Live schema access
   is a separate authorization; a dated snapshot is not current evidence.
4. Separate user decisions, implemented behavior and proposals. In particular,
   row/period tracking was removed at the user's request; do not revive it as an
   assumed requirement. Do not silently choose posting, master creation or date policy.
5. Update the existing requirements/mapping documents with the smallest design,
   component changes, validation/restart/error contracts and synthetic PostgreSQL
   test plan. Link concrete source symbols instead of duplicating source listings.
6. Check documentation links and record verification limits in the learning log.
   Deliver the analysis with unresolved decisions clearly marked.
