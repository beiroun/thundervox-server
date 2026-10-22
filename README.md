# ThunderVox Server

Provisioning service of the [ThunderVox](https://github.com/beiroun/thundervox)
platform. It owns the data the SIP core needs to be closed and manageable:
tenants, sites, devices, app clients and their SIP accounts — and exposes an
admin API for the [web console](https://github.com/beiroun/thundervox-web) and
a service API for the operator's own backend.

> Status: skeleton (T1 of the provisioning layer): Gradle project, Flyway schema
> (Kamailio tables, provisioning domain, the core's database role), response
> envelope, error taxonomy with the global handler, health, OpenAPI. The domain
> (SIP accounts, devices, app clients) comes next; see the platform roadmap in
> the umbrella repository.

---

## What it does

- **SIP accounts.** Issues and rotates passwords for devices (intercom panels)
  and app clients; stores them only as digest `HA1` hashes and keeps the
  `subscriber` table the core authenticates against (`auth_db`) in sync in the
  same transaction. Blocking an account removes it from the core and evicts its
  live registration.
- **Registration journal.** Samples the `location` table written by the core
  (`usrloc` write-through) every 10 s and keeps its own memory of every number:
  online now or not, since when, last seen either way (`registration_state`, one
  row per number), plus one row per transition (`registration_event`). A
  REGISTER refresh writes nothing; a number that expired keeps its last-seen
  time instead of vanishing. A number is offline only after 60 s missing from
  `location` (a softphone whose TCP connection drops reconnects within that and
  never leaves the journal); ONLINE carries the contact's own `last_modified`,
  so it is the registration's time to the second. Presence counts are sampled
  every 30 s for the charts, with the same notion of online.
- **Call journal.** The core reports every call it handles (`started` /
  `answered` / `ended` over the internal API); the server keeps one row per call
  with its outcome (`call_log`).
- **Dashboard.** Counters (online, offline, subscribers, pushes, registrations,
  calls) and time series over 1 h … 1 month for the console's landing page.
- **Schema owner.** All database migrations live here (Flyway), including the
  standard Kamailio tables (`version`, `subscriber`, `location`). The core only
  uses the database; it never creates anything.
- **Service API.** The operator's backend provisions SIP credentials for an app
  client by its external id and checks whether the client is online — the hook
  the mobile register-on-push flow needs. Access is by named tokens the super
  administrator issues on the console's Integration page.
- **Wake push.** When the core parks a call to a sleeping callee it tells this
  server (loopback, `X-CORE-TOKEN`); the server turns the numbers into the
  operator's own ids, makes up the call id and delivers the push to the URL
  configured on the Integration page, asynchronously, with a delivery log.

## Stack

| | |
|---|---|
| Language / runtime | Kotlin 2.4, Java 25 (LTS) |
| Framework | Spring Boot 4.1 — Spring MVC, Spring Data JPA, Spring Security (crypto, JWT) |
| Database | PostgreSQL 18, Flyway migrations |
| Build | Gradle 9.8 (Kotlin DSL), `group = "com.ef_softworks"` |
| Package | `com.ef_softworks.thundervox_server` |
| Image | multi-stage Gradle → Eclipse Temurin 25 JRE, published as `ghcr.io/beiroun/thundervox-server:<version>` on a tagged release |

Code conventions: self-describing packages by domain (no catch-all
`service/` or `*Manager` classes), typed error taxonomy with a global handler,
every API error carries a body, logs on every point where data can be lost.
Comments and log messages in English.

## API outline (`/api/v1`)

| Area | Endpoints |
|---|---|
| Platform | `GET /info` — server name, version, license; `GET /system/health` (actuator); `GET /openapi` (OpenAPI document), `GET /docs` (Swagger UI) |
| Auth | `POST /auth/login` → JWT for the console |
| SIP numbers (console) | `GET/POST /sip-accounts`, `PUT /sip-accounts/{id}` (name and external id), `POST …/password` (shows the password once), `POST …/block`, `/unblock`, `DELETE`; every row carries its registration journal (`online`, `contacts`, last device, `online_since`, `last_online_at`, `last_offline_at`; `null` = never seen registered) |
| Dashboard (console) | `GET /dashboard?range=1h\|6h\|1d\|1w\|1m` — counters now (online, offline, subscribers) and over the range (pushes, registrations, calls, answered), plus one point per bucket (1 min … 6 h) for the charts |
| Console users | `GET/POST/PUT /console-users`, `POST …/password` — three roles, the super administrator comes from the environment |
| Service (`X-SERVICE-TOKEN`) | `PUT /service/sip-accounts/{kind}/{external_id}` (same id → same number, creates on first call, `rotate_password`), `GET …` (the number as it is, never a password), `DELETE …` (block, never delete), `GET …/registration` — the operator's backend addresses endpoints by its own id: a panel by its device id (for Modus the `host:port` of `controls/devices`), an app client by the subscriber account. Tokens are issued on the console's Integration page and sign the audit trail by name. Every call, refused ones included, goes to `service_call_log` (token, method, path, endpoint, status, duration, error of a failed call - never the body of a success) |
| Integration (console) | `GET /integration` (public API address, SIP domain, push gateway settings), `PUT /integration/push` (super administrator), `POST /integration/push/test`; `GET/POST /integration/tokens`, `DELETE /integration/tokens/{id}` |
| Internal (`X-CORE-TOKEN`) | `POST /internal/push/wake` — the core reports a call to a sleeping callee by the two numbers; the server resolves the operator's ids, makes up the `call_id` (UUID), answers at once and delivers the wake push asynchronously (contract v2: `call_id`, `sip_call_id`, `caller_id`/`callee_id` = external ids, numbers, names, `sip_domain`, `occurred_at`). `POST /internal/call-events` — `started` (numbers, `mode` relay\|wake) / `answered` / `ended` (`reason` bye\|cancel\|final\|window, `code`) keyed by `sip_call_id`; queued and answered at once, written to `call_log` behind the request. Loopback only: the edge proxy and the console's nginx answer 404 for `/api/v1/internal/*` |
| Later | `GET /calls/active`, `POST /calls/{callid}/terminate` (need the core's JSON-RPC) |
| Event log (console) | `GET /events?page=&size=&category=&problems=&query=` — what happens on the server, newest first, read from four journals side by side: numbers online/offline (`registration_event`), calls (`call_log`, one line per call), wake pushes (`push_delivery`) and service API calls (`service_call_log`). `category` = `REGISTRATION` \| `CALL` \| `PUSH` \| `SERVICE_CALL`; `problems=true` keeps what did not get through (calls nobody answered or that failed, pushes not delivered, API calls answered ≥ 400, panels gone offline). Without a category and a search the log takes registrations of panels only - app clients come online on every wake push, and the call's line carries `callee_registered_at` (when the callee came online during a `WAKE` call) instead; a search or `category=REGISTRATION` shows every number. Readers get registrations and calls only; `categories` in the answer lists what the viewer may see |
| Audit | `GET /audit?page=&size=&query=` — who issued a password, blocked a device, evicted a registration, terminated a call (append-only log, shown in the console) |
| Smart search | `query` of the audit trail and the event log follows the console's rule (`search/SmartQuery.kt`): lower case and `ё → е`, every word must be part of the row's document (its fields glued, plus a digits-only copy so `178.74` finds `178.74.67.30:5060`), at least one word of 3 characters, at most 100 (otherwise 511 with the reason); a one-word query equal to a key (record id, login, number, call id) puts those rows first |

The OpenAPI document generated by the server is the source of the console's
TypeScript types.

## Run

The server is deployed as an image from the umbrella repository's
`docker-compose.yml`, on the same host as the core, bound to `127.0.0.1:8080`.
Two things reach it there: the console's nginx (same-origin `/api/v1` for the
browser) and the edge proxy, which terminates TLS for the server's public name
(`server.<domain>`) and forwards the scheme in `X-Forwarded-Proto`. It never
listens on a public interface itself. Configuration comes from environment
variables (`.env` on the host):

| Variable | Default | Meaning |
|---|---|---|
| `TVX_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/thundervox` | PostgreSQL the schema lives in |
| `TVX_DB_USER` / `TVX_DB_PASSWORD` | `thundervox` / — | schema owner; Flyway runs as it and needs `CREATEROLE` for the core's role |
| `TVX_SIP_DB_PASSWORD` | — | password of the `tvx_sip` role the core connects with (created by migration `V3`) |
| `TVX_SERVER_BIND` / `TVX_SERVER_PORT` | `127.0.0.1` / `8080` | listen address and port |
| `TVX_CORS_ORIGINS` | — (none) | comma-separated browser origins allowed to call the API cross-site, e.g. `https://console.example.com`. Empty is correct while the console is same-origin through its own nginx; it is needed for a browser client on another name (the console calling `server.<domain>` directly, the Swagger UI on the server's own name, a local vite server). Wildcards are rejected on purpose - the policy allows credentials |
| `TVX_LOG_LEVEL` | `INFO` | log level of the server's own packages |
| `TVX_SIP_REALM` | — | digest realm of every issued password = the SIP domain devices register to (`TVX_SIP_DOMAIN` of the core); hashed into each stored HA1 |
| `TVX_JWT_SECRET`, `TVX_SUPERADMIN_LOGIN`, `TVX_SUPERADMIN_PASSWORD`, `TVX_CONSOLE_TOKEN_TTL` | — / — / — / `PT8H` | console access: token signing key (≥ 32 characters), the one super administrator, login lifetime |
| `TVX_CORE_TOKEN` | — (internal API off) | shared secret of the SIP core for `/internal/**`, header `X-CORE-TOKEN`, ≥ 32 characters; the same value is `TVX_CORE_TOKEN` in the core's `local.cfg` |
| `TVX_PUBLIC_API_URL` | — | address the operator's backend reaches this server at (`https://server.example.com/api/v1`), shown on the Integration page next to the endpoint links |
| `TVX_PUSH_LOG_RETENTION` | `P7D` | how long the wake push delivery log (the pushes of the event log) is kept |
| `TVX_SERVICE_CALL_LOG_RETENTION` | `P30D` | how long the service API calls of the event log are kept |
| `TVX_REGISTRATION_EVENT_RETENTION`, `TVX_CALL_LOG_RETENTION`, `TVX_PRESENCE_SAMPLE_RETENTION` | `P90D` / `P90D` / `P400D` | retention of the journals behind the dashboard (online/offline transitions, calls, presence samples - the last one at least 31 days for the month view); `registration_state` is never purged |

Schema: Flyway owns it. `V1` creates the standard Kamailio tables (`version`,
`subscriber`, `location`) exactly as `kamdbctl` would, `V2` the provisioning
domain (`tenant`, `site`, `device`, `app_client`, `sip_account`, `admin_user`,
`admin_action_log`), `V3` the least-privilege database role for the core, `V4` the console MVP (`kind`,
`name`, console roles, number sequences), `V5` the `external_id` of a number - the endpoint's id in the
operator's system, unique per kind, the key of the service API, `V6` the integration tables (`service_token`,
`integration_push_settings`, `push_delivery`), `V7` the journals (`registration_state`, `registration_event`,
`presence_sample`, `call_log`), `V8` the journal of service API calls (`service_call_log`).

Local build and run against a PostgreSQL of your own:

```bash
./gradlew bootJar                                   # JDK 25 is provisioned by the toolchain resolver if missing
TVX_DB_PASSWORD=… TVX_SIP_DB_PASSWORD=… java -jar build/libs/thundervox-server.jar
curl -s http://127.0.0.1:8080/api/v1/info           # {"data":{"name":"thundervox-server",…},"message":"OK"}
```

Local build with Docker Compose - the image from this working tree plus its own
PostgreSQL, nothing else on the laptop:

```bash
cp .env.example .env                    # then fill the secrets - compose has no defaults for them
docker compose up --build               # builds the jar in Docker, starts postgres + server on 127.0.0.1:8080
docker compose logs -f server           # migrations, then "Started ThundervoxServerApplication"
docker compose down                     # stop; the database stays in ./.local (gitignored)
rm -rf .local && docker compose up      # start from an empty database
```

Secrets (database passwords, JWT secret, super administrator password, core
token) live only in `.env` (gitignored, template `.env.example`); the rest has
local defaults in `docker-compose.yml` (database and user `thundervox`, super
administrator login `admin`, realm `sip.thundervox.local`), and every `TVX_*`
can be overridden from the shell or `.env`. Swagger UI: `http://127.0.0.1:8080/api/v1/docs`;
`npm run dev` in `thundervox-web` proxies `/api` to this server.

Image: `docker build -t thundervox-server:dev .` (the Dockerfile runs the same
Gradle build; CI publishes `ghcr.io/beiroun/thundervox-server:<version>` on a
`vX.Y.Z` tag, the version comes from the tag).

## License

**Business Source License 1.1** — see [`LICENSE`](LICENSE). Non-production use
is free; production use beyond the Additional Use Grant requires a commercial
license from the Licensor. Third-party components keep their own licenses
(`NOTICE` in the umbrella repository).
