# macOS per-user deployment

`trade-rest` and `trade-ui` run as separate LaunchAgents. The UI is available
only at `http://127.0.0.1:9999`; it serves the Vite `dist` directory and
reverse-proxies `/api/*` to REST on `127.0.0.1:8888`.

The UI server waits until `http://127.0.0.1:8888/actuator/health` returns a
successful status before it binds its port. It retries indefinitely, so the UI
also waits after either LaunchAgent is restarted.

## Deploy files

Copy these files outside the source checkout:

```
~/Applications/trading/
  deploy/macos/launch-trade-rest.sh
  deploy/macos/launch-trade-ui.sh
  trade-rest/trade-rest-0.0.1-SNAPSHOT.jar
  trade-ui/dist/
  trade-ui/serve-production.mjs
```

The deployment archive already has this layout. Extract it into
`~/Applications` to create `~/Applications/trading`, then create the log and
credential directories:

```bash
mkdir -p ~/Library/Logs/trading ~/Library/Application\ Support/trading
```

Make the two shell launchers executable:

```bash
chmod 700 ~/Applications/trading/deploy/macos/launch-trade-rest.sh \
  ~/Applications/trading/deploy/macos/launch-trade-ui.sh
```

Create `~/Library/Application Support/trading/trade-rest.env` with only
deployment credentials, for example:

```bash
DB_USERNAME=postgres
DB_PASSWORD=replace-with-the-production-password
```

Protect that file before loading the services:

```bash
chmod 600 ~/Library/Application\ Support/trading/trade-rest.env
```

Copy the two plist templates to `~/Library/LaunchAgents/` and load REST before
UI. Their `EnvironmentVariables` configure the production profile, both ports,
and the UI reverse-proxy target. Do not put database credentials in either
plist.

```bash
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.trading.trade-rest.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.trading.trade-ui.plist
```

To stop them later:

```bash
launchctl bootout gui/$(id -u)/com.trading.trade-ui
launchctl bootout gui/$(id -u)/com.trading.trade-rest
```
