# Backend components

Source baseline: `719606a`, 16 November 2026. See [Architecture](../../ARCHITECTURE.md)
for module boundaries and [Contracts](CONTRACTS_AND_UI.md) for HTTP behavior.

## REST and security

| Component | Responsibility |
|---|---|
| [TradeRestApplication](../../trade-rest/src/main/java/com/trading/TradeRestApplication.java) | Starts REST; excludes legacy Kite and Coin batch/service/launcher classes from scanning |
| [SecurityConfiguration](../../trade-rest/src/main/java/com/trading/security/SecurityConfiguration.java) | Session login/logout, BCrypt, CSRF, public routes and protected requests |
| [CredentialVersionFilter](../../trade-rest/src/main/java/com/trading/security/CredentialVersionFilter.java) | Rechecks account eligibility and credential version; invalidates stale sessions |
| [OwnedPortfolioConfiguration](../../trade-rest/src/main/java/com/trading/security/OwnedPortfolioConfiguration.java) | Four request-scoped primary repository interfaces, fixed to the authenticated principal |
| [SessionController](../../trade-rest/src/main/java/com/trading/controller/SessionController.java) | CSRF token and safe current-user response |
| [RegistrationService](../../trade-rest/src/main/java/com/trading/service/RegistrationService.java) | Validates/hashes credentials; transactionally creates user/profile/ROLE_USER and optional recovery enrollment |
| [UserProfileService](../../trade-rest/src/main/java/com/trading/profile/UserProfileService.java) | Strict profile updates, current-password checks for email/hint changes, locked account/version validation |
| [PasswordRecoveryService](../../trade-rest/src/main/java/com/trading/recovery/PasswordRecoveryService.java) | Enrollment, challenges, answer verification, reset tokens and username reminders |
| [SmtpResetMailSender](../../trade-rest/src/main/java/com/trading/recovery/SmtpResetMailSender.java) | Validates mail/reset-link configuration and sends to the stored address |
| [MutualFundReferenceValidator](../../trade-rest/src/main/java/com/trading/validation/MutualFundReferenceValidator.java) | Requires accessible broker/fund parents before mutations |
| [GlobalExceptionHandler](../../trade-rest/src/main/java/com/trading/exception/GlobalExceptionHandler.java) | Validation, missing-record, integrity, upload and unexpected-error responses; profile/recovery/security also have local handlers |

Portfolio controllers call repositories directly. `MutualFundTxnDto` and
`MutualFundValueDto` validate/convert calendar dates; `PagedResponse` and repository
`PageRequest` enforce paging bounds. Source links and route-level behavior are in
[Contracts and UI](CONTRACTS_AND_UI.md).

## Repository contracts and implementations

All repository contracts and SQL implementations live in `trade-repository`.

| Component family | Data and behavior |
|---|---|
| [JdbcUserPortfolioRepositoryFactory](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcUserPortfolioRepositoryFactory.java) | Constructs four owned repositories; rejects nonpositive owner IDs |
| [OwnedPortfolioJdbc](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedPortfolioJdbc.java) | Shared named JDBC parameters, owner predicates, key/affected-row checks |
| [OwnedBrokerRepository](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedBrokerRepository.java) | Broker CRUD/paging; forces bound owner on insert, prevents reassignment; owner lookup intersects supplied and bound owners |
| [OwnedFundRepository](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedFundRepository.java) | Fund CRUD/paging, parent ownership and optional metadata; ISIN lookup binds owner and ISIN |
| [OwnedTxnRepository](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedTxnRepository.java) | Transactions, scoped summaries, investment overview and date-ordered fund history |
| [OwnedValueRepository](../../trade-repository/src/main/java/com/trading/repository/impl/OwnedValueRepository.java) | Valuation CRUD/paging; latest management row uses full timestamp descending then ID ascending |
| Legacy `MutualFund*RepositoryImpl` classes | Unscoped JDBC implementations remain available outside REST's primary owned beans |
| [JdbcAnalyticsRepository](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcAnalyticsRepository.java) | Explicit owner argument; fund/history reads and one-statement portfolio aggregation |
| [JdbcLoginAccountRepository](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcLoginAccountRepository.java) | Username, password hash, flags, roles and credential-version reads |
| [UserRegistrationRepositoryImpl](../../trade-repository/src/main/java/com/trading/repository/impl/UserRegistrationRepositoryImpl.java) | User/details/ROLE_USER inserts |
| [JdbcUserProfileRepository](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcUserProfileRepository.java) | Locked profile/email/hint changes and recovery invalidation |
| [JdbcPasswordRecoveryRepository](../../trade-repository/src/main/java/com/trading/repository/impl/JdbcPasswordRecoveryRepository.java) | Answers, rate counters, challenge attempts, one-use tokens and versioned password updates |
| [TradeRecordRepository](../../trade-repository/src/main/java/com/trading/repository/impl/TradeRecordRepository.java), [LedgerRecordRepository](../../trade-repository/src/main/java/com/trading/repository/impl/LedgerRecordRepository.java) | JPA CSV persistence and global MCP reads |
| [TradeRecordFastLaneRepositoryImpl](../../trade-repository/src/main/java/com/trading/repository/impl/TradeRecordFastLaneRepositoryImpl.java) | JDBC grouping of consecutive buy/sell records; unweighted price averages |

The manual order model/repository/factory method and `JdbcCoinImportRepository`
were removed. SQL order/audit tables remain; see [Data model](DATA_MODEL.md).

## Build repair and current workspace — 16 November 2026

The published checkpoint retains REST's owner-scoped beans and broker pagination.
`SessionAndOwnershipTest` and its PostgreSQL subclass exercise cross-owner access,
parent reassignment, summaries and the broker-owner/fund-ISIN lookups.
`RestWorkspaceStartupTest` checks that the full batch workspace output does not
register Coin or legacy batch infrastructure in REST. See [dated results](VERIFICATION.md).

The Coin upload implementation was removed during the rewrite:
[MutualFundTxnController.uploadZerodhaTransactions](../../trade-rest/src/main/java/com/trading/controller/MutualFundTxnController.java)
is a TODO returning `null`, and
[ZerodhaTransactionUploadService](../../trade-rest/src/main/java/com/trading/upload/ZerodhaTransactionUploadService.java)
only stores configuration and validates the size limit in its constructor.
Its Javadoc still describes old behavior. The [Coin status guide](../COIN_BATCH_RUNBOOK.md)
is the current reference for the incomplete replacement.

## Batch and MCP

Legacy launchers, discovery services and reader/processor/writer classes now live
under `com.trading.kite`. Their CSV schemas, transactions, deletion hazards and
configuration are documented once in [Batch architecture](../../trade-batch/ARCHITECTURE.md).

[McpConfig](../../trade-mcp-server/src/main/java/com/trading/mcp/conf/McpConfig.java)
provides Hikari/JDBC/tool callback wiring;
[TradeMcpServerTools](../../trade-mcp-server/src/main/java/com/trading/mcp/tools/TradeMcpServerTools.java)
and [TradeMcpServerResources](../../trade-mcp-server/src/main/java/com/trading/mcp/resources/TradeMcpServerResources.java)
define the read interfaces. [MCP contracts](WORKFLOWS.md#mcp-contracts-and-independent-runtime)
record SQL semantics and runtime gaps.
