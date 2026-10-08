# Verification, coverage and investigation checklist

[Main report](../PROJECT_UNDERSTANDING.md). Investigated6–7October2026; commit `70b9f2c2e8621ed396019c6378c1a61b019cf701`, main. The distinctions between **C source**, **T tests**, **R observed runtime/artifact/catalog**, **I inferred consequence**, **U unresolved** apply throughout.

## Scope and safeguards actually used

Read root AGENTS, tracked first-party sources/configuration/scripts/tests/documents. No nested AGENTS was found. There are254 tracked files at the investigated commit, including107 production Java sources,24 Java test/helper sources,43 frontend files and historical Eclipse metadata. Third-party package-lock entries were inspected as dependency configuration, not treated as first-party source to read exhaustively. Generated Maven/npm reports, jars, node_modules and dist were excluded from source coverage; selected fresh outputs were inspected for verification. Ignored personal exports/secret files were not source inputs or published evidence.

Before executing tests, inspected test configuration and fixtures: H2 uses in-memory schemas; PostgresTestDatabase supplies fixed-purpose disposable `postgres:16-alpine` containers; PostgreSQL suites drop/create only their container DB/schema. No `*_TEST_POSTGRES_URL` override is used. Email delivery is mocked. Upload tests use JUnit TempDir. Frontend proxy test starts temporary loopback servers and static directory. Tests do not start the application's import runners or apply application database migrations. [PostgresTestDatabase.java:1](../../trade-rest/src/test/java/com/trading/support/PostgresTestDatabase.java#L1), [PostgresMutualFundOrderPersistenceTest.java:1](../../trade-rest/src/test/java/com/trading/orders/PostgresMutualFundOrderPersistenceTest.java#L1), [ZerodhaTransactionUploadServiceTest.java:1](../../trade-rest/src/test/java/com/trading/upload/ZerodhaTransactionUploadServiceTest.java#L1), [serve-production.test.mjs:1](../../trade-ui/scripts/serve-production.test.mjs#L1), [test-ui.mjs:1](../../trade-ui/scripts/test-ui.mjs#L1)

No commits/pushes or app/dependency/migration changes were made during this investigation. Builds update Git-ignored target/dist outputs. Initial sandbox attempts failed and were rerun with authorized access. Database inspection enforced read-only sessions and queried metadata only; an additional read-only SQL probe used synthetic VALUES shadowing the trade table. No production/dev business records were read, changed or used as report examples. No service restart/deployment/batch launch occurred.

## Fresh execution results

| Check | Date / result / exact scope |
|---|---|
| `mvn -o verify -Pdev` initial sandbox |6Oct: failed;211 attempted,150 errors from Mockito agent attachment/context failure and inaccessible Docker. Not counted as a product assertion failure or pass. |
| `mvn -o verify -Pdev` authorized rerun |6Oct20:26:49 IST finish,91s; all7 reactor projects SUCCESS; **211 tests /0 failures /0 errors /0 skips**.23 test suites. Cached Maven dependencies used; no clean invoked. |
| `npm --prefix trade-ui test` initial sandbox |6Oct: proxy test failed listen EPERM127.0.0.1; not a successful full run. |
| Same frontend test authorized rerun |6Oct: **114 tests /114pass /0fail /0skipped**. Node test runner, bundled React static-render/pure-helper tests, mocked fetch contract tests, actual temporary loopback proxy test. |
| `npm --prefix trade-ui run build` |6Oct: Vite7.3.6 built55modules successfully; dist output generated; not browser verification. |
| `plutil -lint` both deploy plists | Passed; syntax only, not installed LaunchAgents. |
| `zsh -n` both deploy launchers and report helper | Passed; no script executed to start/deploy/clean. |
| Artifact manifest/class inspection | REST Start-Class com.trading.TradeRestApplication present. Batch Start-Class com.trading.TradeBatchApplication present; ledger class also present. MCP Start-Class com.trading.mcp.server.TradeMcpServerApplication **absent**; actual com.trading.TradeMcpServerApplication present. |
| Listener check |6Oct and7Oct: no listeners on project ports6666,7777,8888,9999,8081. No active project artifact/check-out could be attributed; PostgreSQL loopback5432 was available. |
| Read-only PostgreSQL catalog |7Oct02:24 IST and repeat02:46: PostgreSQL18.4, both postgresd/postgresp/public; differences in Data Model; full sanitized catalog saved. |
| Read-only synthetic fast-lane SQL |7Oct02:46: copied repository query, synthetic CTE named trade_records, EXAMPLE symbol. Both databases returned buy count2,quantity10,avg15 and sell count1,quantity3,avg30. `COUNT(grouped_trades.*)` **is accepted on this PostgreSQL version**. No Java mapper/MCP envelope/real table behavior asserted by this probe. |
| Documentation validation | Relative evidence files/line references, dictionary table/column coverage and route/file checklist checked; final Git status confirms only docs changed. |

Local logs (ephemeral, not committed) are `/tmp/trade-understanding-maven.log`, `/tmp/trade-understanding-maven-unrestricted.log`, `/tmp/trade-understanding-ui-tests.log`, `/tmp/trade-understanding-ui-tests-unrestricted.log`, `/tmp/trade-understanding-ui-build.log`. Fresh Surefire XML is under trade-rest/target/surefire-reports. Safe summary is retained below and in [verification evidence](VERIFICATION_RESULTS.json); do not substitute older README counts or old target reports for this run.

The verify phase generated JaCoCo reports, including trade-coverage/target/site/jacoco-aggregate. Since `clean` was deliberately not run, previous jacoco.exec data may be accumulated; **no clean coverage percentage is claimed**. Tests above are counted from fresh XML matching this run's final log. No coverage threshold or application exclusions are configured. Report-only HTML generation was not rerun; previous HTML cannot be treated as fresh test evidence. Maven module-local tests are all in REST; model/repository behavior is nevertheless exercised by its integration tests. Batch/MCP have no behavior tests. [pom.xml:1](../../pom.xml#L1), [pom.xml:1](../../trade-coverage/pom.xml#L1), [verify-with-reports.sh:1](../../scripts/verify-with-reports.sh#L1)

## Test suites and meaningful limits

The following table records every executed Java suite; all failures/errors/skips are0. PostgreSQL subclasses inherit base test cases, so their counts are real repeated scenarios on PostgreSQL, not merely context compilation.

| Suite/source | Tests | Type / main scope |
|---|---:|---|
| [PortfolioAnalyticsTest.java:1](../../trade-rest/src/test/java/com/trading/analytics/PortfolioAnalyticsTest.java#L1) | 14 | H2 owner aggregate, ranges, missing/negative data and invalid types |
| [PostgresPortfolioAnalyticsTest.java:1](../../trade-rest/src/test/java/com/trading/analytics/PostgresPortfolioAnalyticsTest.java#L1) | 14 | PostgreSQL same aggregate scenarios |
| [MutualFundControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/MutualFundControllerTest.java#L1) | 6 | MockMvc fund metadata/validation/errors |
| [MutualFundTxnControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/MutualFundTxnControllerTest.java#L1) | 22 | MockMvc metadata, summaries, dates, numeric/enum validation and staging adapter |
| [MutualFundValueControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/MutualFundValueControllerTest.java#L1) | 13 | MockMvc dates/references/value validation |
| [PaginationControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/PaginationControllerTest.java#L1) | 9 | MockMvc all paged contracts/defaults/bounds |
| [RegistrationControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/RegistrationControllerTest.java#L1) | 4 | MockMvc registration/hash/duplicate/privilege field behavior |
| [PagedResponseTest.java:1](../../trade-rest/src/test/java/com/trading/dto/PagedResponseTest.java#L1) | 3 | Unit paging metadata and overflow |
| [TransactionSummaryTest.java:1](../../trade-rest/src/test/java/com/trading/dto/TransactionSummaryTest.java#L1) | 1 | Unit BigDecimal value retention |
| [MutualFundOrderPersistenceTest.java:1](../../trade-rest/src/test/java/com/trading/orders/MutualFundOrderPersistenceTest.java#L1) | 9 | H2 owner/order/source metadata persistence |
| [PostgresFundFolioMigrationTest.java:1](../../trade-rest/src/test/java/com/trading/orders/PostgresFundFolioMigrationTest.java#L1) | 2 | PostgreSQL V10 transfer/conflict rollback |
| [PostgresMutualFundOrderPersistenceTest.java:1](../../trade-rest/src/test/java/com/trading/orders/PostgresMutualFundOrderPersistenceTest.java#L1) | 12 | PostgreSQL order tests plus Flyway repeat/validation and legacy repository |
| [PostgresOrderMetadataMigrationTest.java:1](../../trade-rest/src/test/java/com/trading/orders/PostgresOrderMetadataMigrationTest.java#L1) | 11 | PostgreSQL V11 transfer, each unlinked/conflicting field rollback |
| [PostgresUserProfileTest.java:1](../../trade-rest/src/test/java/com/trading/profile/PostgresUserProfileTest.java#L1) | 16 | PostgreSQL inherited profile/locking/rollback |
| [UserProfileTest.java:1](../../trade-rest/src/test/java/com/trading/profile/UserProfileTest.java#L1) | 16 | H2 profile validation/privacy/ownership/current password/locking |
| [PasswordRecoveryTest.java:1](../../trade-rest/src/test/java/com/trading/recovery/PasswordRecoveryTest.java#L1) | 16 | H2 enrollment/challenge/rates/replay/expiry/concurrency/session invalidation |
| [PostgresPasswordRecoveryTest.java:1](../../trade-rest/src/test/java/com/trading/recovery/PostgresPasswordRecoveryTest.java#L1) | 16 | PostgreSQL inherited recovery scenarios |
| [SmtpResetMailSenderTest.java:1](../../trade-rest/src/test/java/com/trading/recovery/SmtpResetMailSenderTest.java#L1) | 3 | Mock mail validation/reset URL/recipient/body; no actual SMTP delivery |
| [PostgresBrokerAccountUniquenessTest.java:1](../../trade-rest/src/test/java/com/trading/repository/PostgresBrokerAccountUniquenessTest.java#L1) | 3 | PostgreSQL V12 key, NULL-owner semantics, migration rollback |
| [RegistrationPersistenceTest.java:1](../../trade-rest/src/test/java/com/trading/repository/RegistrationPersistenceTest.java#L1) | 1 | H2 registration role transaction rollback |
| [SessionAndOwnershipTest.java:1](../../trade-rest/src/test/java/com/trading/security/SessionAndOwnershipTest.java#L1) | 15 | H2 real security filters/two-owner CRUD/CSRF/flags/health/analytics |
| [ZerodhaTransactionUploadServiceTest.java:1](../../trade-rest/src/test/java/com/trading/upload/ZerodhaTransactionUploadServiceTest.java#L1) | 2 | TempDir filename/owner containment and empty/type rejection |
| [MutualFundReferenceValidatorTest.java:1](../../trade-rest/src/test/java/com/trading/validation/MutualFundReferenceValidatorTest.java#L1) | 3 | Mocked reference validation |

**Total211**, including74 PostgreSQL checks. The24th Java test-side source, [PostgresTestDatabase.java:1](../../trade-rest/src/test/java/com/trading/support/PostgresTestDatabase.java#L1), is the shared container helper, not a test suite.

Frontend tests reviewed/executed: App.test.jsx imports AnalyticsDetails,PortfolioAnalytics,AdvancedReturns,MutualFunds,TransactionMetadata tests; api.test.js mocks fetch and exercises contracts; serve-production.test.mjs creates actual temporary upstream/UI servers. They cover formatting, date/range/empty/error gates, hand-worked analytics, metadata preservation/escaping, auth/profile payloads and proxy cookies/readiness. SSR does not run useEffect, browser fetch/cookie policy, user navigation races, focus/accessibility/layout or real authentication end-to-end. No browser test was performed because no project application was running and startup was out of scope.

Fresh full V1–V13 migration is **not** covered. H2 fixtures adapt PostgreSQL syntax and commonly omit V1/V2/V4; some omit V8. PostgreSQL profile/recovery/analytics inherit manually initialized fixture schemas; order migrations baseline an existing V8 fixture then migrateV9–V13; specific V10/V11/V12 tests exercise upgrade/rollback. PostgreSQL16 tests do not by themselves prove PostgreSQL18 deployment history, extensions or privileges. The synthetic SQL probe proves query acceptance/expected aggregate for its supplied rows only. It does not exercise JPA loading, null timestamp mapping, ties, transport, auth or broad real-data performance.

## Unresolved verification and policy checklist

- [ ] Approved fresh-install/upgrade reconciliation with real deployment Flyway history, V1/V4 extension/dimension requirements and V12 live uniqueness. No migration was applied here.
- [ ] Live REST startup/artifact identity and authenticated browser flows on the intended database; no project listener existed.
- [ ] Real SMTP delivery and reset-link origin/cookie behavior; source config and mocked tests only.
- [ ] Batch context/dual-runner wiring, framework job repository/transaction manager behavior and isolated import/restart/skip/concurrency tests; no batch execution allowed here.
- [ ] MCP corrected executable start-class, actual tool/resource discovery/schema/envelopes and blocking/concurrency behavior; no MCP service started.
- [ ] Product decisions: order source matching/deduplication/lifecycle, owner backfill, admin/auditing policy, weighted versus unweighted MCP semantics, batch append/replace/archive/reject policy, benchmark provider.

These are explicit readiness limits; they do not conceal unreviewed first-party components or unanswered repository facts. Future changes can use the source map and known contracts without repeating basic discovery.

## First-party file coverage checklist

Every entry below was inspected for its role in this report. Application/test/config/docs were read; repetitive IDE descriptors were inspected as metadata, not runtime behavior. Lockfile review covers root/resolved versions/engines rather than exhaustive third-party dependency implementation. Package outputs and installed dependencies are intentionally absent. This manifest is from `git ls-files` at the recorded commit; newly created report files are not part of the investigated application source count.

### .settings

- [x] [.settings/org.eclipse.core.resources.prefs:1](../../.settings/org.eclipse.core.resources.prefs#L1) — IDE metadata; 2 lines.
- [x] [.settings/org.eclipse.m2e.core.prefs:1](../../.settings/org.eclipse.m2e.core.prefs#L1) — IDE metadata; 4 lines.

### Root

- [x] [.gitattributes:1](../../.gitattributes#L1) — build/config/operations; 2 lines.
- [x] [.gitignore:1](../../.gitignore#L1) — build/config/operations; 67 lines.
- [x] [.project:1](../../.project#L1) — IDE metadata; 17 lines.
- [x] [AGENTS.md:1](../../AGENTS.md#L1) — instructions/documentation; 115 lines.
- [x] [ANALYTICS_PLAN.md:1](../../ANALYTICS_PLAN.md#L1) — instructions/documentation; 379 lines.
- [x] [ARCHITECTURE.md:1](../../ARCHITECTURE.md#L1) — instructions/documentation; 585 lines.
- [x] [DEPLOYMENT_ARCHITECTURE.md:1](../../DEPLOYMENT_ARCHITECTURE.md#L1) — instructions/documentation; 108 lines.
- [x] [README.md:1](../../README.md#L1) — instructions/documentation; 569 lines.
- [x] [coin-order-schema.md:1](../../coin-order-schema.md#L1) — instructions/documentation; 226 lines.
- [x] [multi-user-support-gap-analysis.md:1](../../multi-user-support-gap-analysis.md#L1) — instructions/documentation; 173 lines.
- [x] [pom.xml:7](../../pom.xml#L7) — build/config/operations; 194 lines.

### deploy

- [x] [deploy/macos/README.md:1](../../deploy/macos/README.md#L1) — instructions/documentation; 68 lines.
- [x] [deploy/macos/com.trading.trade-rest.plist:1](../../deploy/macos/com.trading.trade-rest.plist#L1) — build/config/operations; 35 lines.
- [x] [deploy/macos/com.trading.trade-ui.plist:1](../../deploy/macos/com.trading.trade-ui.plist#L1) — build/config/operations; 37 lines.
- [x] [deploy/macos/launch-trade-rest.sh:1](../../deploy/macos/launch-trade-rest.sh#L1) — build/config/operations; 17 lines.
- [x] [deploy/macos/launch-trade-ui.sh:1](../../deploy/macos/launch-trade-ui.sh#L1) — build/config/operations; 6 lines.

### scripts

- [x] [scripts/verify-with-reports.sh:1](../../scripts/verify-with-reports.sh#L1) — source; 10 lines.

### trade-batch

- [x] [trade-batch/.classpath:1](../../trade-batch/.classpath#L1) — IDE metadata; 40 lines.
- [x] [trade-batch/.gitignore:1](../../trade-batch/.gitignore#L1) — build/config/operations; 1 lines.
- [x] [trade-batch/.project:1](../../trade-batch/.project#L1) — IDE metadata; 23 lines.
- [x] [trade-batch/.settings/org.eclipse.core.resources.prefs:1](../../trade-batch/.settings/org.eclipse.core.resources.prefs#L1) — IDE metadata; 6 lines.
- [x] [trade-batch/.settings/org.eclipse.jdt.core.prefs:1](../../trade-batch/.settings/org.eclipse.jdt.core.prefs#L1) — IDE metadata; 9 lines.
- [x] [trade-batch/.settings/org.eclipse.m2e.core.prefs:1](../../trade-batch/.settings/org.eclipse.m2e.core.prefs#L1) — IDE metadata; 4 lines.
- [x] [trade-batch/ARCHITECTURE.md:1](../../trade-batch/ARCHITECTURE.md#L1) — instructions/documentation; 185 lines.
- [x] [trade-batch/pom.xml:7](../../trade-batch/pom.xml#L7) — build/config/operations; 55 lines.
- [x] [trade-batch/src/main/java/com/trading/LedgerBalancesBatchApplication.java:16](../../trade-batch/src/main/java/com/trading/LedgerBalancesBatchApplication.java#L16) — source; 38 lines.
- [x] [trade-batch/src/main/java/com/trading/TradeBatchApplication.java:15](../../trade-batch/src/main/java/com/trading/TradeBatchApplication.java#L15) — source; 36 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/config/BatchConfiguration.java:36](../../trade-batch/src/main/java/com/trading/batch/config/BatchConfiguration.java#L36) — source; 155 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/config/LedgerBalancesBatchConfiguration.java:36](../../trade-batch/src/main/java/com/trading/batch/config/LedgerBalancesBatchConfiguration.java#L36) — source; 159 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/listener/JobExecutionListener.java:10](../../trade-batch/src/main/java/com/trading/batch/listener/JobExecutionListener.java#L10) — source; 51 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/processor/LedgerRecordItemProcessor.java:18](../../trade-batch/src/main/java/com/trading/batch/processor/LedgerRecordItemProcessor.java#L18) — source; 38 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/processor/TradeRecordItemProcessor.java:10](../../trade-batch/src/main/java/com/trading/batch/processor/TradeRecordItemProcessor.java#L10) — source; 21 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/reader/LedgerFileItemReader.java:18](../../trade-batch/src/main/java/com/trading/batch/reader/LedgerFileItemReader.java#L18) — source; 66 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/reader/TradeFileItemReader.java:18](../../trade-batch/src/main/java/com/trading/batch/reader/TradeFileItemReader.java#L18) — source; 63 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/reader/mapper/LedgerRecordFieldSetMapper.java:12](../../trade-batch/src/main/java/com/trading/batch/reader/mapper/LedgerRecordFieldSetMapper.java#L12) — source; 38 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/reader/mapper/TradeFieldSetMapper.java:13](../../trade-batch/src/main/java/com/trading/batch/reader/mapper/TradeFieldSetMapper.java#L13) — source; 51 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/writer/LedgerRecordItemWriter.java:14](../../trade-batch/src/main/java/com/trading/batch/writer/LedgerRecordItemWriter.java#L14) — source; 33 lines.
- [x] [trade-batch/src/main/java/com/trading/batch/writer/TradeRecordItemWriter.java:14](../../trade-batch/src/main/java/com/trading/batch/writer/TradeRecordItemWriter.java#L14) — source; 33 lines.
- [x] [trade-batch/src/main/java/com/trading/service/BatchJobService.java:20](../../trade-batch/src/main/java/com/trading/service/BatchJobService.java#L20) — source; 84 lines.
- [x] [trade-batch/src/main/java/com/trading/service/LedgerBalancesBatchJobService.java:20](../../trade-batch/src/main/java/com/trading/service/LedgerBalancesBatchJobService.java#L20) — source; 93 lines.
- [x] [trade-batch/src/main/resources/application-dev.properties:1](../../trade-batch/src/main/resources/application-dev.properties#L1) — build/config/operations; 2 lines.
- [x] [trade-batch/src/main/resources/application-prod.properties:1](../../trade-batch/src/main/resources/application-prod.properties#L1) — build/config/operations; 2 lines.
- [x] [trade-batch/src/main/resources/application.properties:1](../../trade-batch/src/main/resources/application.properties#L1) — build/config/operations; 56 lines.

### trade-coverage

- [x] [trade-coverage/pom.xml:7](../../trade-coverage/pom.xml#L7) — build/config/operations; 69 lines.

### trade-mcp-server

- [x] [trade-mcp-server/.classpath:1](../../trade-mcp-server/.classpath#L1) — IDE metadata; 40 lines.
- [x] [trade-mcp-server/.gitignore:1](../../trade-mcp-server/.gitignore#L1) — build/config/operations; 1 lines.
- [x] [trade-mcp-server/.project:1](../../trade-mcp-server/.project#L1) — IDE metadata; 23 lines.
- [x] [trade-mcp-server/.settings/org.eclipse.core.resources.prefs:1](../../trade-mcp-server/.settings/org.eclipse.core.resources.prefs#L1) — IDE metadata; 6 lines.
- [x] [trade-mcp-server/.settings/org.eclipse.jdt.core.prefs:1](../../trade-mcp-server/.settings/org.eclipse.jdt.core.prefs#L1) — IDE metadata; 9 lines.
- [x] [trade-mcp-server/.settings/org.eclipse.m2e.core.prefs:1](../../trade-mcp-server/.settings/org.eclipse.m2e.core.prefs#L1) — IDE metadata; 4 lines.
- [x] [trade-mcp-server/pom.xml:7](../../trade-mcp-server/pom.xml#L7) — build/config/operations; 66 lines.
- [x] [trade-mcp-server/src/main/java/com/trading/TradeMcpServerApplication.java:7](../../trade-mcp-server/src/main/java/com/trading/TradeMcpServerApplication.java#L7) — source; 12 lines.
- [x] [trade-mcp-server/src/main/java/com/trading/mcp/conf/CorsConfig.java:12](../../trade-mcp-server/src/main/java/com/trading/mcp/conf/CorsConfig.java#L12) — source; 30 lines.
- [x] [trade-mcp-server/src/main/java/com/trading/mcp/conf/McpConfig.java:14](../../trade-mcp-server/src/main/java/com/trading/mcp/conf/McpConfig.java#L14) — source; 59 lines.
- [x] [trade-mcp-server/src/main/java/com/trading/mcp/resources/TradeMcpServerResources.java:14](../../trade-mcp-server/src/main/java/com/trading/mcp/resources/TradeMcpServerResources.java#L14) — source; 25 lines.
- [x] [trade-mcp-server/src/main/java/com/trading/mcp/tools/TradeMcpServerTools.java:20](../../trade-mcp-server/src/main/java/com/trading/mcp/tools/TradeMcpServerTools.java#L20) — source; 58 lines.
- [x] [trade-mcp-server/src/main/resources/application-dev.properties:1](../../trade-mcp-server/src/main/resources/application-dev.properties#L1) — build/config/operations; 2 lines.
- [x] [trade-mcp-server/src/main/resources/application-prod.properties:1](../../trade-mcp-server/src/main/resources/application-prod.properties#L1) — build/config/operations; 2 lines.
- [x] [trade-mcp-server/src/main/resources/application.properties:1](../../trade-mcp-server/src/main/resources/application.properties#L1) — build/config/operations; 40 lines.

### trade-model

- [x] [trade-model/.classpath:1](../../trade-model/.classpath#L1) — IDE metadata; 40 lines.
- [x] [trade-model/.gitignore:1](../../trade-model/.gitignore#L1) — build/config/operations; 1 lines.
- [x] [trade-model/.project:1](../../trade-model/.project#L1) — IDE metadata; 23 lines.
- [x] [trade-model/.settings/org.eclipse.core.resources.prefs:1](../../trade-model/.settings/org.eclipse.core.resources.prefs#L1) — IDE metadata; 6 lines.
- [x] [trade-model/.settings/org.eclipse.jdt.core.prefs:1](../../trade-model/.settings/org.eclipse.jdt.core.prefs#L1) — IDE metadata; 9 lines.
- [x] [trade-model/.settings/org.eclipse.m2e.core.prefs:1](../../trade-model/.settings/org.eclipse.m2e.core.prefs#L1) — IDE metadata; 4 lines.
- [x] [trade-model/pom.xml:7](../../trade-model/pom.xml#L7) — build/config/operations; 39 lines.
- [x] [trade-model/src/main/java/com/trading/model/LedgerBalances.java:19](../../trade-model/src/main/java/com/trading/model/LedgerBalances.java#L19) — source; 105 lines.
- [x] [trade-model/src/main/java/com/trading/model/LedgerRecord.java:20](../../trade-model/src/main/java/com/trading/model/LedgerRecord.java#L20) — source; 164 lines.
- [x] [trade-model/src/main/java/com/trading/model/LoginAccount.java:5](../../trade-model/src/main/java/com/trading/model/LoginAccount.java#L5) — source; 13 lines.
- [x] [trade-model/src/main/java/com/trading/model/MutualFund.java:9](../../trade-model/src/main/java/com/trading/model/MutualFund.java#L9) — source; 86 lines.
- [x] [trade-model/src/main/java/com/trading/model/MutualFundBrokerAccount.java:5](../../trade-model/src/main/java/com/trading/model/MutualFundBrokerAccount.java#L5) — source; 64 lines.
- [x] [trade-model/src/main/java/com/trading/model/MutualFundOrder.java:14](../../trade-model/src/main/java/com/trading/model/MutualFundOrder.java#L14) — source; 59 lines.
- [x] [trade-model/src/main/java/com/trading/model/MutualFundTxn.java:12](../../trade-model/src/main/java/com/trading/model/MutualFundTxn.java#L12) — source; 154 lines.
- [x] [trade-model/src/main/java/com/trading/model/MutualFundValue.java:10](../../trade-model/src/main/java/com/trading/model/MutualFundValue.java#L10) — source; 69 lines.
- [x] [trade-model/src/main/java/com/trading/model/ProfileChanges.java:4](../../trade-model/src/main/java/com/trading/model/ProfileChanges.java#L4) — source; 7 lines.
- [x] [trade-model/src/main/java/com/trading/model/ProfileCredentials.java:3](../../trade-model/src/main/java/com/trading/model/ProfileCredentials.java#L3) — source; 5 lines.
- [x] [trade-model/src/main/java/com/trading/model/ProfileHintQuestion.java:7](../../trade-model/src/main/java/com/trading/model/ProfileHintQuestion.java#L7) — source; 20 lines.
- [x] [trade-model/src/main/java/com/trading/model/RecoveryAccount.java:3](../../trade-model/src/main/java/com/trading/model/RecoveryAccount.java#L3) — source; 6 lines.
- [x] [trade-model/src/main/java/com/trading/model/RecoveryChallenge.java:3](../../trade-model/src/main/java/com/trading/model/RecoveryChallenge.java#L3) — source; 6 lines.
- [x] [trade-model/src/main/java/com/trading/model/TradeRecord.java:21](../../trade-model/src/main/java/com/trading/model/TradeRecord.java#L21) — source; 227 lines.
- [x] [trade-model/src/main/java/com/trading/model/UserProfile.java:4](../../trade-model/src/main/java/com/trading/model/UserProfile.java#L4) — source; 7 lines.
- [x] [trade-model/src/main/java/com/trading/model/enums/TransactionType.java:3](../../trade-model/src/main/java/com/trading/model/enums/TransactionType.java#L3) — source; 7 lines.
- [x] [trade-model/src/main/java/com/trading/model/result/FundInvestmentSummary.java:6](../../trade-model/src/main/java/com/trading/model/result/FundInvestmentSummary.java#L6) — source; 6 lines.
- [x] [trade-model/src/main/java/com/trading/model/result/PortfolioAnalytics.java:8](../../trade-model/src/main/java/com/trading/model/result/PortfolioAnalytics.java#L8) — source; 23 lines.
- [x] [trade-model/src/main/java/com/trading/model/result/TradeDetailsResult.java:7](../../trade-model/src/main/java/com/trading/model/result/TradeDetailsResult.java#L7) — source; 120 lines.
- [x] [trade-model/src/main/java/com/trading/model/result/TransactionSummary.java:5](../../trade-model/src/main/java/com/trading/model/result/TransactionSummary.java#L5) — source; 22 lines.

### trade-repository

- [x] [trade-repository/.classpath:1](../../trade-repository/.classpath#L1) — IDE metadata; 40 lines.
- [x] [trade-repository/.gitignore:1](../../trade-repository/.gitignore#L1) — build/config/operations; 1 lines.
- [x] [trade-repository/.project:1](../../trade-repository/.project#L1) — IDE metadata; 23 lines.
- [x] [trade-repository/.settings/org.eclipse.core.resources.prefs:1](../../trade-repository/.settings/org.eclipse.core.resources.prefs#L1) — IDE metadata; 6 lines.
- [x] [trade-repository/.settings/org.eclipse.jdt.core.prefs:1](../../trade-repository/.settings/org.eclipse.jdt.core.prefs#L1) — IDE metadata; 9 lines.
- [x] [trade-repository/.settings/org.eclipse.m2e.core.prefs:1](../../trade-repository/.settings/org.eclipse.m2e.core.prefs#L1) — IDE metadata; 4 lines.
- [x] [trade-repository/pom.xml:7](../../trade-repository/pom.xml#L7) — build/config/operations; 56 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/AnalyticsRepository.java:9](../../trade-repository/src/main/java/com/trading/repository/AnalyticsRepository.java#L9) — source; 13 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/InvalidPaginationException.java:3](../../trade-repository/src/main/java/com/trading/repository/InvalidPaginationException.java#L3) — source; 10 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/LoginAccountRepository.java:6](../../trade-repository/src/main/java/com/trading/repository/LoginAccountRepository.java#L6) — source; 8 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/MutualFundBrokerAccountRepository.java:7](../../trade-repository/src/main/java/com/trading/repository/MutualFundBrokerAccountRepository.java#L7) — source; 21 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/MutualFundOrderRepository.java:7](../../trade-repository/src/main/java/com/trading/repository/MutualFundOrderRepository.java#L7) — source; 13 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/MutualFundRepository.java:7](../../trade-repository/src/main/java/com/trading/repository/MutualFundRepository.java#L7) — source; 31 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/MutualFundTxnRepository.java:9](../../trade-repository/src/main/java/com/trading/repository/MutualFundTxnRepository.java#L9) — source; 38 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/MutualFundValueRepository.java:7](../../trade-repository/src/main/java/com/trading/repository/MutualFundValueRepository.java#L7) — source; 31 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/PageRequest.java:6](../../trade-repository/src/main/java/com/trading/repository/PageRequest.java#L6) — source; 34 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/PasswordRecoveryRepository.java:9](../../trade-repository/src/main/java/com/trading/repository/PasswordRecoveryRepository.java#L9) — source; 23 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/TradeRecordFastLaneRepository.java:7](../../trade-repository/src/main/java/com/trading/repository/TradeRecordFastLaneRepository.java#L7) — source; 46 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/UserPortfolioRepositoryFactory.java:4](../../trade-repository/src/main/java/com/trading/repository/UserPortfolioRepositoryFactory.java#L4) — source; 10 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/UserProfileRepository.java:8](../../trade-repository/src/main/java/com/trading/repository/UserProfileRepository.java#L8) — source; 12 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/UserRegistrationRepository.java:3](../../trade-repository/src/main/java/com/trading/repository/UserRegistrationRepository.java#L3) — source; 6 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/JdbcAnalyticsRepository.java:22](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcAnalyticsRepository.java#L22) — source; 168 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/JdbcLoginAccountRepository.java:11](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcLoginAccountRepository.java#L11) — source; 28 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/JdbcPasswordRecoveryRepository.java:18](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcPasswordRecoveryRepository.java#L18) — source; 121 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/JdbcUserPortfolioRepositoryFactory.java:8](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcUserPortfolioRepositoryFactory.java#L8) — source; 16 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/JdbcUserProfileRepository.java:14](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcUserProfileRepository.java#L14) — source; 63 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/LedgerRecordRepository.java:17](../../trade-repository/src/main/java/com/trading/repository/impl/LedgerRecordRepository.java#L17) — source; 34 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/MutualFundBrokerAccountRepositoryImpl.java:14](../../trade-repository/src/main/java/com/trading/repository/impl/MutualFundBrokerAccountRepositoryImpl.java#L14) — source; 109 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/MutualFundRepositoryImpl.java:14](../../trade-repository/src/main/java/com/trading/repository/impl/MutualFundRepositoryImpl.java#L14) — source; 200 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/MutualFundTxnRepositoryImpl.java:17](../../trade-repository/src/main/java/com/trading/repository/impl/MutualFundTxnRepositoryImpl.java#L17) — source; 244 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/MutualFundValueRepositoryImpl.java:14](../../trade-repository/src/main/java/com/trading/repository/impl/MutualFundValueRepositoryImpl.java#L14) — source; 181 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/OwnedBrokerRepository.java:1](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedBrokerRepository.java#L1) — source; 70 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/OwnedFundRepository.java:1](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedFundRepository.java#L1) — source; 102 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/OwnedOrderRepository.java:1](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedOrderRepository.java#L1) — source; 97 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/OwnedPortfolioJdbc.java:1](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedPortfolioJdbc.java#L1) — source; 39 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/OwnedTxnRepository.java:1](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedTxnRepository.java#L1) — source; 141 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/OwnedValueRepository.java:1](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedValueRepository.java#L1) — source; 94 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/TradeRecordFastLaneRepositoryImpl.java:20](../../trade-repository/src/main/java/com/trading/repository/impl/TradeRecordFastLaneRepositoryImpl.java#L20) — source; 50 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/TradeRecordRepository.java:18](../../trade-repository/src/main/java/com/trading/repository/impl/TradeRecordRepository.java#L18) — source; 45 lines.
- [x] [trade-repository/src/main/java/com/trading/repository/impl/UserRegistrationRepositoryImpl.java:10](../../trade-repository/src/main/java/com/trading/repository/impl/UserRegistrationRepositoryImpl.java#L10) — source; 35 lines.
- [x] [trade-repository/src/main/resources/db/migration/README.md:1](../../trade-repository/src/main/resources/db/migration/README.md#L1) — instructions/documentation; 81 lines.
- [x] [trade-repository/src/main/resources/db/migration/V10__move_folio_to_mutual_fund.sql:1](../../trade-repository/src/main/resources/db/migration/V10__move_folio_to_mutual_fund.sql#L1) — migration; 16 lines.
- [x] [trade-repository/src/main/resources/db/migration/V11__move_order_metadata_to_transactions.sql:1](../../trade-repository/src/main/resources/db/migration/V11__move_order_metadata_to_transactions.sql#L1) — migration; 53 lines.
- [x] [trade-repository/src/main/resources/db/migration/V12__unique_broker_account_per_owner.sql:1](../../trade-repository/src/main/resources/db/migration/V12__unique_broker_account_per_owner.sql#L1) — migration; 5 lines.
- [x] [trade-repository/src/main/resources/db/migration/V13__relax_mutual_fund_isin_length.sql:1](../../trade-repository/src/main/resources/db/migration/V13__relax_mutual_fund_isin_length.sql#L1) — migration; 3 lines.
- [x] [trade-repository/src/main/resources/db/migration/V1__extensions.sql:1](../../trade-repository/src/main/resources/db/migration/V1__extensions.sql#L1) — migration; 2 lines.
- [x] [trade-repository/src/main/resources/db/migration/V2__spring_batch_schema.sql:1](../../trade-repository/src/main/resources/db/migration/V2__spring_batch_schema.sql#L1) — migration; 76 lines.
- [x] [trade-repository/src/main/resources/db/migration/V3__application_schema.sql:1](../../trade-repository/src/main/resources/db/migration/V3__application_schema.sql#L1) — migration; 81 lines.
- [x] [trade-repository/src/main/resources/db/migration/V4__spring_ai_schema.sql:1](../../trade-repository/src/main/resources/db/migration/V4__spring_ai_schema.sql#L1) — migration; 18 lines.
- [x] [trade-repository/src/main/resources/db/migration/V5__create_app_user.sql:2](../../trade-repository/src/main/resources/db/migration/V5__create_app_user.sql#L2) — migration; 30 lines.
- [x] [trade-repository/src/main/resources/db/migration/V6__portfolio_ownership.sql:1](../../trade-repository/src/main/resources/db/migration/V6__portfolio_ownership.sql#L1) — migration; 8 lines.
- [x] [trade-repository/src/main/resources/db/migration/V7__password_recovery.sql:4](../../trade-repository/src/main/resources/db/migration/V7__password_recovery.sql#L4) — migration; 35 lines.
- [x] [trade-repository/src/main/resources/db/migration/V8__user_profile_hint.sql:1](../../trade-repository/src/main/resources/db/migration/V8__user_profile_hint.sql#L1) — migration; 9 lines.
- [x] [trade-repository/src/main/resources/db/migration/V9__mutual_fund_orders.sql:17](../../trade-repository/src/main/resources/db/migration/V9__mutual_fund_orders.sql#L17) — migration; 41 lines.

### trade-rest

- [x] [trade-rest/.classpath:1](../../trade-rest/.classpath#L1) — IDE metadata; 40 lines.
- [x] [trade-rest/.gitignore:1](../../trade-rest/.gitignore#L1) — build/config/operations; 1 lines.
- [x] [trade-rest/.project:1](../../trade-rest/.project#L1) — IDE metadata; 23 lines.
- [x] [trade-rest/.settings/org.eclipse.core.resources.prefs:1](../../trade-rest/.settings/org.eclipse.core.resources.prefs#L1) — IDE metadata; 6 lines.
- [x] [trade-rest/.settings/org.eclipse.jdt.core.prefs:1](../../trade-rest/.settings/org.eclipse.jdt.core.prefs#L1) — IDE metadata; 9 lines.
- [x] [trade-rest/.settings/org.eclipse.m2e.core.prefs:1](../../trade-rest/.settings/org.eclipse.m2e.core.prefs#L1) — IDE metadata; 4 lines.
- [x] [trade-rest/pom.xml:7](../../trade-rest/pom.xml#L7) — build/config/operations; 92 lines.
- [x] [trade-rest/src/main/java/com/trading/TradeRestApplication.java:7](../../trade-rest/src/main/java/com/trading/TradeRestApplication.java#L7) — source; 12 lines.
- [x] [trade-rest/src/main/java/com/trading/controller/AnalyticsController.java:21](../../trade-rest/src/main/java/com/trading/controller/AnalyticsController.java#L21) — source; 64 lines.
- [x] [trade-rest/src/main/java/com/trading/controller/MutualFundBrokerAccountController.java:22](../../trade-rest/src/main/java/com/trading/controller/MutualFundBrokerAccountController.java#L22) — source; 79 lines.
- [x] [trade-rest/src/main/java/com/trading/controller/MutualFundController.java:28](../../trade-rest/src/main/java/com/trading/controller/MutualFundController.java#L28) — source; 101 lines.
- [x] [trade-rest/src/main/java/com/trading/controller/MutualFundTxnController.java:40](../../trade-rest/src/main/java/com/trading/controller/MutualFundTxnController.java#L40) — source; 158 lines.
- [x] [trade-rest/src/main/java/com/trading/controller/MutualFundValueController.java:29](../../trade-rest/src/main/java/com/trading/controller/MutualFundValueController.java#L29) — source; 106 lines.
- [x] [trade-rest/src/main/java/com/trading/controller/RegistrationController.java:14](../../trade-rest/src/main/java/com/trading/controller/RegistrationController.java#L14) — source; 35 lines.
- [x] [trade-rest/src/main/java/com/trading/controller/SessionController.java:12](../../trade-rest/src/main/java/com/trading/controller/SessionController.java#L12) — source; 25 lines.
- [x] [trade-rest/src/main/java/com/trading/dto/MutualFundTxnDto.java:18](../../trade-rest/src/main/java/com/trading/dto/MutualFundTxnDto.java#L18) — source; 84 lines.
- [x] [trade-rest/src/main/java/com/trading/dto/MutualFundValueDto.java:17](../../trade-rest/src/main/java/com/trading/dto/MutualFundValueDto.java#L17) — source; 56 lines.
- [x] [trade-rest/src/main/java/com/trading/dto/PagedResponse.java:7](../../trade-rest/src/main/java/com/trading/dto/PagedResponse.java#L7) — source; 43 lines.
- [x] [trade-rest/src/main/java/com/trading/dto/RegisterRequest.java:7](../../trade-rest/src/main/java/com/trading/dto/RegisterRequest.java#L7) — source; 18 lines.
- [x] [trade-rest/src/main/java/com/trading/exception/ApiError.java:6](../../trade-rest/src/main/java/com/trading/exception/ApiError.java#L6) — source; 48 lines.
- [x] [trade-rest/src/main/java/com/trading/exception/GlobalExceptionHandler.java:25](../../trade-rest/src/main/java/com/trading/exception/GlobalExceptionHandler.java#L25) — source; 127 lines.
- [x] [trade-rest/src/main/java/com/trading/exception/InvalidReferenceException.java:3](../../trade-rest/src/main/java/com/trading/exception/InvalidReferenceException.java#L3) — source; 10 lines.
- [x] [trade-rest/src/main/java/com/trading/profile/ProfileException.java:6](../../trade-rest/src/main/java/com/trading/profile/ProfileException.java#L6) — source; 15 lines.
- [x] [trade-rest/src/main/java/com/trading/profile/ProfileUpdateRequest.java:6](../../trade-rest/src/main/java/com/trading/profile/ProfileUpdateRequest.java#L6) — source; 20 lines.
- [x] [trade-rest/src/main/java/com/trading/profile/UserProfileController.java:19](../../trade-rest/src/main/java/com/trading/profile/UserProfileController.java#L19) — source; 53 lines.
- [x] [trade-rest/src/main/java/com/trading/profile/UserProfileService.java:18](../../trade-rest/src/main/java/com/trading/profile/UserProfileService.java#L18) — source; 83 lines.
- [x] [trade-rest/src/main/java/com/trading/recovery/PasswordRecoveryController.java:15](../../trade-rest/src/main/java/com/trading/recovery/PasswordRecoveryController.java#L15) — source; 57 lines.
- [x] [trade-rest/src/main/java/com/trading/recovery/PasswordRecoveryService.java:17](../../trade-rest/src/main/java/com/trading/recovery/PasswordRecoveryService.java#L17) — source; 146 lines.
- [x] [trade-rest/src/main/java/com/trading/recovery/RecoveryConfiguration.java:8](../../trade-rest/src/main/java/com/trading/recovery/RecoveryConfiguration.java#L8) — source; 10 lines.
- [x] [trade-rest/src/main/java/com/trading/recovery/RecoveryException.java:5](../../trade-rest/src/main/java/com/trading/recovery/RecoveryException.java#L5) — source; 9 lines.
- [x] [trade-rest/src/main/java/com/trading/recovery/RecoveryRequests.java:7](../../trade-rest/src/main/java/com/trading/recovery/RecoveryRequests.java#L7) — source; 26 lines.
- [x] [trade-rest/src/main/java/com/trading/recovery/ResetMailSender.java:3](../../trade-rest/src/main/java/com/trading/recovery/ResetMailSender.java#L3) — source; 7 lines.
- [x] [trade-rest/src/main/java/com/trading/recovery/SmtpResetMailSender.java:12](../../trade-rest/src/main/java/com/trading/recovery/SmtpResetMailSender.java#L12) — source; 56 lines.
- [x] [trade-rest/src/main/java/com/trading/security/AccountPrincipal.java:7](../../trade-rest/src/main/java/com/trading/security/AccountPrincipal.java#L7) — source; 22 lines.
- [x] [trade-rest/src/main/java/com/trading/security/CredentialVersionFilter.java:1](../../trade-rest/src/main/java/com/trading/security/CredentialVersionFilter.java#L1) — source; 34 lines.
- [x] [trade-rest/src/main/java/com/trading/security/OwnedPortfolioConfiguration.java:11](../../trade-rest/src/main/java/com/trading/security/OwnedPortfolioConfiguration.java#L11) — source; 40 lines.
- [x] [trade-rest/src/main/java/com/trading/security/SecurityConfiguration.java:17](../../trade-rest/src/main/java/com/trading/security/SecurityConfiguration.java#L17) — source; 67 lines.
- [x] [trade-rest/src/main/java/com/trading/service/RegistrationService.java:11](../../trade-rest/src/main/java/com/trading/service/RegistrationService.java#L11) — source; 40 lines.
- [x] [trade-rest/src/main/java/com/trading/upload/ZerodhaTransactionUpload.java:6](../../trade-rest/src/main/java/com/trading/upload/ZerodhaTransactionUpload.java#L6) — source; 7 lines.
- [x] [trade-rest/src/main/java/com/trading/upload/ZerodhaTransactionUploadService.java:15](../../trade-rest/src/main/java/com/trading/upload/ZerodhaTransactionUploadService.java#L15) — source; 52 lines.
- [x] [trade-rest/src/main/java/com/trading/validation/MutualFundReferenceValidator.java:11](../../trade-rest/src/main/java/com/trading/validation/MutualFundReferenceValidator.java#L11) — source; 40 lines.
- [x] [trade-rest/src/main/resources/application-dev.properties:1](../../trade-rest/src/main/resources/application-dev.properties#L1) — build/config/operations; 12 lines.
- [x] [trade-rest/src/main/resources/application-flyway-baseline.properties:1](../../trade-rest/src/main/resources/application-flyway-baseline.properties#L1) — build/config/operations; 3 lines.
- [x] [trade-rest/src/main/resources/application-prod.properties:1](../../trade-rest/src/main/resources/application-prod.properties#L1) — build/config/operations; 11 lines.
- [x] [trade-rest/src/main/resources/application.properties:1](../../trade-rest/src/main/resources/application.properties#L1) — build/config/operations; 50 lines.
- [x] [trade-rest/src/test/java/com/trading/analytics/PortfolioAnalyticsTest.java:1](../../trade-rest/src/test/java/com/trading/analytics/PortfolioAnalyticsTest.java#L1) — test/helper; 276 lines.
- [x] [trade-rest/src/test/java/com/trading/analytics/PostgresPortfolioAnalyticsTest.java:1](../../trade-rest/src/test/java/com/trading/analytics/PostgresPortfolioAnalyticsTest.java#L1) — test/helper; 17 lines.
- [x] [trade-rest/src/test/java/com/trading/controller/MutualFundControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/MutualFundControllerTest.java#L1) — test/helper; 147 lines.
- [x] [trade-rest/src/test/java/com/trading/controller/MutualFundTxnControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/MutualFundTxnControllerTest.java#L1) — test/helper; 305 lines.
- [x] [trade-rest/src/test/java/com/trading/controller/MutualFundValueControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/MutualFundValueControllerTest.java#L1) — test/helper; 128 lines.
- [x] [trade-rest/src/test/java/com/trading/controller/PaginationControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/PaginationControllerTest.java#L1) — test/helper; 196 lines.
- [x] [trade-rest/src/test/java/com/trading/controller/RegistrationControllerTest.java:1](../../trade-rest/src/test/java/com/trading/controller/RegistrationControllerTest.java#L1) — test/helper; 66 lines.
- [x] [trade-rest/src/test/java/com/trading/dto/PagedResponseTest.java:1](../../trade-rest/src/test/java/com/trading/dto/PagedResponseTest.java#L1) — test/helper; 42 lines.
- [x] [trade-rest/src/test/java/com/trading/dto/TransactionSummaryTest.java:1](../../trade-rest/src/test/java/com/trading/dto/TransactionSummaryTest.java#L1) — test/helper; 20 lines.
- [x] [trade-rest/src/test/java/com/trading/orders/MutualFundOrderPersistenceTest.java:1](../../trade-rest/src/test/java/com/trading/orders/MutualFundOrderPersistenceTest.java#L1) — test/helper; 278 lines.
- [x] [trade-rest/src/test/java/com/trading/orders/PostgresFundFolioMigrationTest.java:1](../../trade-rest/src/test/java/com/trading/orders/PostgresFundFolioMigrationTest.java#L1) — test/helper; 69 lines.
- [x] [trade-rest/src/test/java/com/trading/orders/PostgresMutualFundOrderPersistenceTest.java:1](../../trade-rest/src/test/java/com/trading/orders/PostgresMutualFundOrderPersistenceTest.java#L1) — test/helper; 85 lines.
- [x] [trade-rest/src/test/java/com/trading/orders/PostgresOrderMetadataMigrationTest.java:1](../../trade-rest/src/test/java/com/trading/orders/PostgresOrderMetadataMigrationTest.java#L1) — test/helper; 107 lines.
- [x] [trade-rest/src/test/java/com/trading/profile/PostgresUserProfileTest.java:1](../../trade-rest/src/test/java/com/trading/profile/PostgresUserProfileTest.java#L1) — test/helper; 17 lines.
- [x] [trade-rest/src/test/java/com/trading/profile/UserProfileTest.java:1](../../trade-rest/src/test/java/com/trading/profile/UserProfileTest.java#L1) — test/helper; 244 lines.
- [x] [trade-rest/src/test/java/com/trading/recovery/PasswordRecoveryTest.java:1](../../trade-rest/src/test/java/com/trading/recovery/PasswordRecoveryTest.java#L1) — test/helper; 270 lines.
- [x] [trade-rest/src/test/java/com/trading/recovery/PostgresPasswordRecoveryTest.java:1](../../trade-rest/src/test/java/com/trading/recovery/PostgresPasswordRecoveryTest.java#L1) — test/helper; 17 lines.
- [x] [trade-rest/src/test/java/com/trading/recovery/SmtpResetMailSenderTest.java:1](../../trade-rest/src/test/java/com/trading/recovery/SmtpResetMailSenderTest.java#L1) — test/helper; 48 lines.
- [x] [trade-rest/src/test/java/com/trading/repository/PostgresBrokerAccountUniquenessTest.java:1](../../trade-rest/src/test/java/com/trading/repository/PostgresBrokerAccountUniquenessTest.java#L1) — test/helper; 93 lines.
- [x] [trade-rest/src/test/java/com/trading/repository/RegistrationPersistenceTest.java:32](../../trade-rest/src/test/java/com/trading/repository/RegistrationPersistenceTest.java#L32) — test/helper; 52 lines.
- [x] [trade-rest/src/test/java/com/trading/security/SessionAndOwnershipTest.java:1](../../trade-rest/src/test/java/com/trading/security/SessionAndOwnershipTest.java#L1) — test/helper; 286 lines.
- [x] [trade-rest/src/test/java/com/trading/support/PostgresTestDatabase.java:15](../../trade-rest/src/test/java/com/trading/support/PostgresTestDatabase.java#L15) — test/helper; 44 lines.
- [x] [trade-rest/src/test/java/com/trading/upload/ZerodhaTransactionUploadServiceTest.java:1](../../trade-rest/src/test/java/com/trading/upload/ZerodhaTransactionUploadServiceTest.java#L1) — test/helper; 44 lines.
- [x] [trade-rest/src/test/java/com/trading/validation/MutualFundReferenceValidatorTest.java:1](../../trade-rest/src/test/java/com/trading/validation/MutualFundReferenceValidatorTest.java#L1) — test/helper; 56 lines.

### trade-ui

- [x] [trade-ui/index.html:1](../../trade-ui/index.html#L1) — UI presentation; 12 lines.
- [x] [trade-ui/package-lock.json:1](../../trade-ui/package-lock.json#L1) — dependency lock metadata; 1839 lines.
- [x] [trade-ui/package.json:1](../../trade-ui/package.json#L1) — build/config/operations; 24 lines.
- [x] [trade-ui/scripts/serve-production.mjs:1](../../trade-ui/scripts/serve-production.mjs#L1) — build/config/operations; 138 lines.
- [x] [trade-ui/scripts/serve-production.test.mjs:1](../../trade-ui/scripts/serve-production.test.mjs#L1) — test/helper; 57 lines.
- [x] [trade-ui/scripts/test-ui.mjs:1](../../trade-ui/scripts/test-ui.mjs#L1) — build/config/operations; 27 lines.
- [x] [trade-ui/src/App.jsx:20](../../trade-ui/src/App.jsx#L20) — source; 156 lines.
- [x] [trade-ui/src/App.test.jsx:1](../../trade-ui/src/App.test.jsx#L1) — test/helper; 455 lines.
- [x] [trade-ui/src/api/api.js:64](../../trade-ui/src/api/api.js#L64) — source; 150 lines.
- [x] [trade-ui/src/api/api.test.js:1](../../trade-ui/src/api/api.test.js#L1) — test/helper; 266 lines.
- [x] [trade-ui/src/components/ActivityCharts.jsx:28](../../trade-ui/src/components/ActivityCharts.jsx#L28) — source; 94 lines.
- [x] [trade-ui/src/components/AdvancedReturns.jsx:8](../../trade-ui/src/components/AdvancedReturns.jsx#L8) — source; 71 lines.
- [x] [trade-ui/src/components/AdvancedReturns.test.jsx:1](../../trade-ui/src/components/AdvancedReturns.test.jsx#L1) — test/helper; 231 lines.
- [x] [trade-ui/src/components/Analytics.jsx:37](../../trade-ui/src/components/Analytics.jsx#L37) — source; 598 lines.
- [x] [trade-ui/src/components/AnalyticsDetails.jsx:9](../../trade-ui/src/components/AnalyticsDetails.jsx#L9) — source; 112 lines.
- [x] [trade-ui/src/components/AnalyticsDetails.test.jsx:1](../../trade-ui/src/components/AnalyticsDetails.test.jsx#L1) — test/helper; 230 lines.
- [x] [trade-ui/src/components/AnalyticsSummary.jsx:4](../../trade-ui/src/components/AnalyticsSummary.jsx#L4) — source; 27 lines.
- [x] [trade-ui/src/components/AuthLinks.jsx:1](../../trade-ui/src/components/AuthLinks.jsx#L1) — source; 15 lines.
- [x] [trade-ui/src/components/BrokerAccounts.jsx:9](../../trade-ui/src/components/BrokerAccounts.jsx#L9) — source; 99 lines.
- [x] [trade-ui/src/components/DateInput.jsx:3](../../trade-ui/src/components/DateInput.jsx#L3) — source; 37 lines.
- [x] [trade-ui/src/components/ForgotUsername.jsx:4](../../trade-ui/src/components/ForgotUsername.jsx#L4) — source; 35 lines.
- [x] [trade-ui/src/components/Login.jsx:4](../../trade-ui/src/components/Login.jsx#L4) — source; 40 lines.
- [x] [trade-ui/src/components/MutualFundTransactions.jsx:10](../../trade-ui/src/components/MutualFundTransactions.jsx#L10) — source; 228 lines.
- [x] [trade-ui/src/components/MutualFundValues.jsx:10](../../trade-ui/src/components/MutualFundValues.jsx#L10) — source; 118 lines.
- [x] [trade-ui/src/components/MutualFunds.jsx:8](../../trade-ui/src/components/MutualFunds.jsx#L8) — source; 104 lines.
- [x] [trade-ui/src/components/MutualFunds.test.jsx:1](../../trade-ui/src/components/MutualFunds.test.jsx#L1) — test/helper; 39 lines.
- [x] [trade-ui/src/components/Pagination.jsx:3](../../trade-ui/src/components/Pagination.jsx#L3) — source; 57 lines.
- [x] [trade-ui/src/components/PortfolioAnalytics.jsx:7](../../trade-ui/src/components/PortfolioAnalytics.jsx#L7) — source; 98 lines.
- [x] [trade-ui/src/components/PortfolioAnalytics.test.jsx:1](../../trade-ui/src/components/PortfolioAnalytics.test.jsx#L1) — test/helper; 102 lines.
- [x] [trade-ui/src/components/Profile.jsx:4](../../trade-ui/src/components/Profile.jsx#L4) — source; 117 lines.
- [x] [trade-ui/src/components/Register.jsx:4](../../trade-ui/src/components/Register.jsx#L4) — source; 67 lines.
- [x] [trade-ui/src/components/ResetPassword.jsx:4](../../trade-ui/src/components/ResetPassword.jsx#L4) — source; 59 lines.
- [x] [trade-ui/src/components/TransactionMetadata.test.jsx:1](../../trade-ui/src/components/TransactionMetadata.test.jsx#L1) — test/helper; 50 lines.
- [x] [trade-ui/src/components/advancedReturnMetrics.js:37](../../trade-ui/src/components/advancedReturnMetrics.js#L37) — source; 201 lines.
- [x] [trade-ui/src/components/analyticsActivity.js:16](../../trade-ui/src/components/analyticsActivity.js#L16) — source; 80 lines.
- [x] [trade-ui/src/components/analyticsMetrics.js:5](../../trade-ui/src/components/analyticsMetrics.js#L5) — source; 40 lines.
- [x] [trade-ui/src/components/chartTicks.js:2](../../trade-ui/src/components/chartTicks.js#L2) — source; 18 lines.
- [x] [trade-ui/src/components/holdingsBalances.js:7](../../trade-ui/src/components/holdingsBalances.js#L7) — source; 45 lines.
- [x] [trade-ui/src/components/investmentHistory.js:4](../../trade-ui/src/components/investmentHistory.js#L4) — source; 33 lines.
- [x] [trade-ui/src/main.jsx:1](../../trade-ui/src/main.jsx#L1) — source; 10 lines.
- [x] [trade-ui/src/styles/app.css:1](../../trade-ui/src/styles/app.css#L1) — UI presentation; 632 lines.
- [x] [trade-ui/src/utils/date.js:3](../../trade-ui/src/utils/date.js#L3) — source; 43 lines.
- [x] [trade-ui/vite.config.js:1](../../trade-ui/vite.config.js#L1) — build/config/operations; 15 lines.


## Coin batch implementation — 8 October 2026

Checkout `main`, `70b9f2c2e8621ed396019c6378c1a61b019cf701`, plus uncommitted Coin
implementation/tests/migration and documentation. Earlier results above keep their
original dates and scope. Application DBs/services were not used or changed.

| Check | Current result |
|---|---|
| `mvn -o clean verify -Pdev` | PASS, all seven reactor projects; 258 tests (47 Coin + 211 REST), zero failures/errors/skips; finished 20:20 IST. |
| Coin suites | CoinCsvReaderTest 8; CoinRecordValidatorTest 16; CoinComponentTest 6; CoinImportIntegrationTest 17. |
| Isolated source copy: `mvn -o verify -pl trade-batch -am` | PASS, 47 tests, zero failures/errors/skips; source/POM hashes matched the workspace; finished 20:22 IST. No JaCoCo class mismatch warning. |
| PostgreSQL runtime tests | Disposable PostgreSQL16.14, V14 Flyway upgrade/validate/replay from prepared V13 portfolio schema; period exclusion SQLSTATE23P01 and adjacent bounds, concurrent claims, owner isolation, completion/posting/replay, rollback and restart in a new JVM. |
| Standalone modes | Separate JVM tests proved DRY_RUN makes no managed copies/business/audit/Batch writes; IMPORT checks all files before reservation and launches only Coin. |
| Packaging | Default JAR Start-Class remains TradeBatchApplication; classifier `coin` has CoinOrderHistoryBatchApplication. Packaged VALIDATE smoke check returned0 on synthetic input and left bytes unchanged. |
| Documentation/hygiene | Relative file links and `git diff --check` passed. No commit/push, deployment, live migration/import or service restart. |

**Build troubleshooting:** the first non-clean compile used inconsistent generated
class artifacts. Later shared-workspace runs passed tests but JaCoCo reported
mismatches. File timestamps and SHA-256 comparisons proved `target/classes` was
rewritten after Maven packaged the corresponding classes, including after a clean
build. A background compiler is inferred; its owning process was not identified or
stopped. This limits the shared-workspace coverage report, not the executed test
counts. The final Coin verification used an isolated source-identical copy at
`/private/tmp/coin-isolated-verify-ixykm3u8`; its class data was stable and coverage
rendering had no mismatch warning. No aggregate coverage percentage is claimed.

**Implementation fixes verified:** use the proxied `JobOperator` interface rather
than lookup of TaskExecutorJobOperator; allow unchanged funds alongside another
fund's completion; preflight all input files before first reservation; preserve
source details outside Batch exception chains; retain failed coverage and restart
committed chunks without duplicates. CLI/test fixture paths use canonical private
directories because macOS `/var` and `/tmp` aliases are symlinks.

Logs: `/tmp/coin-full-reactor-clean-verify.log`, `/tmp/coin-batch-isolated-verify.log`.
The isolated copy contains `source-hashes.json` for source correspondence.
No new frontend tests/build were needed or run (no UI changes). Fresh V1–V14
installation, deployed master data matching, live migration-history/V12 repair,
REST upload-to-job integration, ungraceful-kill recovery and reversal/correction
workflows remain unverified or out of scope. See the [runbook](../COIN_BATCH_RUNBOOK.md).

## Coin upload integration — 9 October 2026

Checkout `main`, `70b9f2c2e8621ed396019c6378c1a61b019cf701`, with the existing
uncommitted Coin implementation plus the REST/runtime/UI integration. This entry
supersedes the earlier upload-integration gap; other historical boundaries remain.

| Check | Current result |
|---|---|
| Isolated source copy: `mvn -o clean verify -Pdev` | PASS, all seven reactor projects; **270 tests: 47 batch + 223 REST**, zero failures/errors/skips; finished 03:24 IST. |
| Focused upload/controller suites | PASS, 36 tests: CoinUploadPostgresIntegrationTest 9, ZerodhaTransactionUploadServiceTest 5, MutualFundTxnControllerTest 22. Subsequently included in the clean full build. |
| `npm --prefix trade-ui test` | PASS, **119 tests**, zero failures/skips. |
| `npm --prefix trade-ui run build` | PASS, Vite production build. Generated output remains ignored. |
| Real HTTP and PostgreSQL | Authenticated, CSRF-protected multipart upload through the random-port test server completed the actual job and persisted its order; missing CSRF rejected. Disposable PostgreSQL16.14 only. |
| Import lifecycle and failures | PROCESSING→COMPLETE updates stable order identity; exact replay is a no-op; source metadata, owner isolation, aggregate validation, file-only202, options/size validation, unavailable schema503, conflict409, partial-chunk500 and same-file restart verified. |
| Context and packaging | Private context closure leaves the REST datasource usable; root context has no Job/JobRepository. Packaged REST includes only the Coin library and Batch core/infrastructure, no Batch Boot auto-configuration or legacy runners. Default/coin executable entry points are preserved. |

The isolated build at `/private/tmp/coin-upload-verify-nt2rgk1y` avoids the
shared-workspace class rewriting documented above. All **197 source/POM manifest
entries** matched both workspace and copied files by SHA-256 after verification;
the full build had no JaCoCo class mismatch warning. The Coin library's class
entries were inspected: all are under `com/trading/coin`, excluding standalone main
and command-line classes. REST's packaged dependencies contain the Coin library,
`spring-batch-core` and `spring-batch-infrastructure`; the Batch Boot starter and
auto-configuration are absent. No coverage percentage is claimed.

Tests apply the real V14 migration to a prepared V13 test portfolio schema. HTTP
integration uses synthetic data; it does not target either configured application
database or a running user service. The initial sandbox attempts were blocked by
Docker access/Mockito agent attachment (backend) and local-port binding (frontend
proxy test); the successful reruns used approved execution outside the sandbox.
The frontend tests cover options, rendered fields, pagination and multipart API
construction; browser events and visual layout were not exercised.

Logs: `/tmp/coin-upload-focused.log`, `/tmp/coin-upload-full-verify.log`,
`/tmp/coin-upload-ui-tests.log`, `/tmp/coin-upload-ui-build.log`. Source evidence:
[HTTP/PostgreSQL tests](../../trade-rest/src/test/java/com/trading/upload/CoinUploadPostgresIntegrationTest.java),
[service tests](../../trade-rest/src/test/java/com/trading/upload/ZerodhaTransactionUploadServiceTest.java),
[form tests](../../trade-ui/src/components/CoinUploadForm.test.jsx) and
[API tests](../../trade-ui/src/api/api.test.js).

No live migration, import, service restart, deployment, commit or push occurred.
V14 and the preflight prerequisites must be reconciled/applied through deployment
before live uploads can import. Fresh V1–V14 installation, actual broker exports
against deployed master data and ungraceful-kill recovery remain unverified.
