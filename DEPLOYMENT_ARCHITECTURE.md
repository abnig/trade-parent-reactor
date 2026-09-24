# macOS Deployment Architecture

## Scope

This document describes the production deployment of the two browser-facing
applications as separate, per-user macOS LaunchAgents:

- `trade-rest`: Spring Boot REST API, packaged as an executable JAR.
- `trade-ui`: Vite static bundle, served by a small Node.js process that also
  reverse-proxies API requests to `trade-rest`.

It applies to the production profile and the paths in `deploy/macos/*.plist`.
Those templates are intentionally scoped to the current macOS user,
`abnig19`.

## Runtime topology

```mermaid
flowchart LR
    Browser[Browser] -->|http://127.0.0.1:9999| UI[trade-ui service\nNode static server]
    UI -->|serve HTML, CSS, JS| Assets[trade-ui dist bundle]
    UI -->|reverse proxy /api/*\nhttp://127.0.0.1:8888| REST[trade-rest service\nSpring Boot prod]
    REST -->|JDBC\npostgresp, public schema| DB[(PostgreSQL)]
    UI -. wait until 200 .-> Health[/actuator/health]
    Health --> REST
```

Both HTTP listeners bind only to `127.0.0.1`; neither service is exposed to
the network. The browser must use `http://127.0.0.1:9999` so UI API calls are
same-origin. The Node server forwards `/api/*` responses, including session
cookies, to the browser unchanged.

## Deployables

| Deployable | Contents | Runtime | Listener | Source artifact |
|---|---|---|---|---|
| REST | Spring Boot fat JAR and external credentials file | Java 25 | `127.0.0.1:8888` | `trade-rest/target/trade-rest-0.0.1-SNAPSHOT.jar` |
| UI | Vite `dist` assets and `serve-production.mjs` | Node.js | `127.0.0.1:9999` | `trade-ui/dist`, `trade-ui/scripts/serve-production.mjs` |
| Service config | Two plist files and two launcher scripts | `launchd` | N/A | `deploy/macos/` |

`target/trading-macos-deployment-20260924.tar.gz` stages all of these files
under a `trading/` directory. Extracting it into `~/Applications` creates the
paths referenced by the plist templates.

## LaunchAgent configuration

| LaunchAgent | Start command | Required environment configuration | Lifecycle |
|---|---|---|---|
| `com.trading.trade-rest` | `launch-trade-rest.sh` | `SPRING_PROFILES_ACTIVE=prod`, `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=8888`, JAR and external environment-file paths | `RunAtLoad` and `KeepAlive` |
| `com.trading.trade-ui` | `launch-trade-ui.sh` | `TRADE_UI_PORT=9999`, dist/server paths, `TRADE_REST_HOST=127.0.0.1`, `TRADE_REST_PORT=8888`, `TRADE_REST_HEALTH_PATH=/actuator/health` | `RunAtLoad` and `KeepAlive` |

The UI is launched independently, but `serve-production.mjs` does not bind
port 9999 until REST health returns a successful status. It retries
indefinitely when REST is still starting or has restarted. This health check
is the readiness dependency; it is stronger than relying on LaunchAgent load
order alone.

Only `/actuator/health` is unauthenticated to support this local readiness
check. Other actuator routes and all protected API routes retain the existing
Spring Security policy.

## Configuration and secrets

The REST production datasource is configured by the `prod` profile:

```text
jdbc:postgresql://localhost:5432/postgresp?currentSchema=public
```

The LaunchAgent plist contains ports, profile selection, artifact locations,
and proxy settings. It must never contain database credentials. The REST
launcher loads `DB_USERNAME` and `DB_PASSWORD` from:

```text
~/Library/Application Support/trading/trade-rest.env
```

That user-owned file must have mode `600`. The Node UI service has no database
credentials; it only forwards same-origin HTTP requests to REST.

## Startup and update sequence

1. Extract the archive into `~/Applications` and create the log and credential
   directories described in `deploy/macos/README.md`.
2. Create the external REST credentials file and set mode `600`.
3. Copy the two plist templates to `~/Library/LaunchAgents/`.
4. Bootstrap `com.trading.trade-rest` first, then
   `com.trading.trade-ui`.
5. The UI waits for the REST readiness endpoint before accepting browser
   traffic on port 9999.

For an update, boot out the UI service first, then REST; replace artifacts and
reload REST before UI. This prevents a browser from receiving a new static UI
while requests are being sent to a stopped REST process.

## Verification status

The current deployment implementation has been verified with:

- UI tests, including a loopback server test for REST readiness, SPA fallback,
  `/api` proxying, and session-cookie forwarding.
- The REST `SessionAndOwnershipTest` under the `prod` profile, including the
  unauthenticated health readiness endpoint.
- `plutil -lint` for both plist templates and `zsh -n` for both launchers.
- A rebuilt Spring Boot executable JAR and Vite production build.

The templates have not been installed into `~/Library/LaunchAgents` or started
against the live production database as part of this repository change.
