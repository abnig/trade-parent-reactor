# macOS per-user deployment

Templates run REST and UI as separate LaunchAgents on loopback 8888/9999. See
[topology and limitations](../../DEPLOYMENT_ARCHITECTURE.md). These steps describe
operator actions; documentation review does not install or restart services.

## Prepare artifacts and configuration

Build from the repository root using [README](../../README.md#build-and-test).
Review the database/migration prerequisites and unresolved Coin feature before
replacing a running deployment. Copy the resulting files into this layout:

```text
~/Applications/trading/
  deploy/macos/launch-trade-rest.sh
  deploy/macos/launch-trade-ui.sh
  trade-rest/trade-rest-0.0.1-SNAPSHOT.jar
  trade-ui/dist/
  trade-ui/serve-production.mjs
```

Sources are the REST JAR under `trade-rest/target`, UI `trade-ui/dist`,
`trade-ui/scripts/serve-production.mjs`, and the two launchers in this directory.
There is no maintained prebuilt deployment archive in Git.

```bash
mkdir -p ~/Library/Logs/trading ~/Library/LaunchAgents
mkdir -p ~/Library/Application\ Support/trading
chmod 700 ~/Applications/trading/deploy/macos/launch-trade-rest.sh \
  ~/Applications/trading/deploy/macos/launch-trade-ui.sh
```

Create `~/Library/Application Support/trading/trade-rest.env` with externally
supplied `DB_USERNAME`, `DB_PASSWORD` and any required SMTP/recovery settings.
Use a shell-compatible file; the REST launcher sources it. Do not put secrets in
source control or plist files.

```bash
chmod 600 ~/Library/Application\ Support/trading/trade-rest.env
```

Adapt absolute home paths in both plist templates, then copy them into
`~/Library/LaunchAgents`. Confirm Java 25 and Node are available to the launcher
scripts. Plists define the production profile, ports, artifact paths and proxy.

## Start, check and update

```bash
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.trading.trade-rest.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.trading.trade-ui.plist
```

The UI waits for REST health before first binding. Open `http://127.0.0.1:9999`
using the configured origin, and inspect `~/Library/Logs/trading` for failures.
Check login/CSRF and the intended portfolio flows, not only process existence.

For an update, stop UI then REST, replace artifacts, and bootstrap REST then UI:

```bash
launchctl bootout gui/$(id -u)/com.trading.trade-ui
launchctl bootout gui/$(id -u)/com.trading.trade-rest
```

Do not infer that restarting applies migrations: Flyway is disabled. Migration
reconciliation and any approved data changes are separate from artifact replacement.
