# Keeping the service warm

The backend runs on a host that suspends a service after a stretch with no inbound
requests. The first request after that has to wait for the service to start again —
long enough that a browser gives up, or a student decides the site is broken.

The fix is not a setting. It is traffic: something outside this service has to call it
often enough that it never goes idle in the first place.

---

## 1. The health endpoint

```
GET https://engineers-day-backend.onrender.com/health
```

**Response** — `200 OK`, `Content-Type: application/json`:

```json
{"status":"UP"}
```

No authentication. No query parameters. Nothing secret in the URL.

Served by `HealthController`, which holds no dependencies at all — no repository, no
service, no `DataSource`. It cannot reach the database or any business logic even by
accident. Measured locally at **13–54 ms**, and it still answered `200 {"status":"UP"}`
in 13 ms with PostgreSQL stopped entirely.

### Do not give the monitor credentials

`/health` needs no credentials, but it does not *ignore* credentials. Spring Security's
Basic filter runs before authorization, so a request carrying an `Authorization` header
that fails to authenticate is refused with **401** before the permit rule is consulted.

A monitor configured with a stray username and password will therefore report this
service **down while it is perfectly healthy**. Leave authentication switched off in the
monitor. This is pinned by a test, so it will not change silently.

---

## 2. Why this endpoint exists rather than `/actuator/health`

Actuator is installed and `/actuator/health` is exposed, public, and returns the same
body. It is not the same check. Its registry carries `db`, `diskSpace`, `ssl` and `ping`
contributors, so every call opens a pooled connection and runs a validation query
against PostgreSQL.

Measured with the database stopped:

| Endpoint | Status | Time |
| --- | --- | --- |
| `/health` | `200 {"status":"UP"}` | 0.013 s |
| `/actuator/health` | `503 {"status":"DOWN"}` | 5.04 s |
| `/api/events` | `500` | 5.05 s |

That deep check is the right behaviour for a readiness probe and the wrong behaviour for
something polled every five minutes forever. `/actuator/health` is untouched and remains
available for anything that genuinely wants it.

---

## 3. The external monitor — this is the actual keep-alive

**A health endpoint keeps nothing awake by existing.** It is only something to call. The
periodic inbound request is what prevents the service going idle, and it must come from
outside the host, because a suspended service runs no schedulers, no threads and no
`@Scheduled` methods of its own. Nothing inside this codebase can do this job.

Create an HTTP monitor on any external uptime service:

| Setting | Value |
| --- | --- |
| URL | `https://engineers-day-backend.onrender.com/health` |
| Method | `GET` |
| Interval | **5 minutes** |
| Expected status | `200` |
| Authentication | **None** — see the warning above |
| Expected body *(optional)* | contains `"status":"UP"` |
| Follow redirects | not required |
| Request body / headers | none |

Five minutes sits well inside the idle window with room to spare: a single missed check
still leaves the next one in time. It stays light either way — 288 requests a day, each
about 20 ms of work.

Free services that do this well — any one is enough, do not set up several:

- **UptimeRobot** — free tier polls every 5 minutes exactly.
- **cron-job.org** — free, allows an exact 5-minute schedule.
- **Better Stack** — free tier polls every 3 minutes, which also works.

The monitor earns its place twice: it keeps the service warm, and it tells you when the
backend is actually down.

### Cost caveat, worth checking before you switch it on

A service that never idles is a service that runs all month. Free hosting tiers usually
meter monthly instance hours, and a month is about 730 hours. Confirm the current
allowance on your plan before relying on this, especially if the same account runs more
than one free service.

---

## 4. Platform health check

Set the service's **Health Check Path** to:

```
/health
```

This is a different mechanism from the monitor above and they should not be confused:

- The **platform health check** is the host asking "is this instance healthy?" during
  deploys and restarts. It does not produce keep-alive traffic.
- The **external monitor** is the periodic inbound request. It is the keep-alive.

Deliberate tradeoff in pointing the platform at `/health`: because `/health` ignores the
database, the platform will consider the service healthy even if PostgreSQL is
unreachable — a deploy will not flap on a transient database problem, but the platform
will not catch a database outage either. If you would rather it did, point the health
check (**not** the monitor) at `/actuator/health`, which returns 503 when the database is
down. The monitor should stay on `/health` either way.

---

## 5. Changing or replacing the monitoring service

Nothing in this repository names the monitor, so replacing it is entirely an operations
task — there is no code to change and no deploy to make:

1. Create the new monitor with the settings in section 3.
2. Confirm it has recorded at least two successful checks.
3. Delete the old monitor, so a stale one is not still alerting someone.

If the backend URL itself changes, update the monitor's URL, the platform health check
path if the endpoint moved, and this file.

To verify the endpoint by hand at any time:

```bash
curl -i https://engineers-day-backend.onrender.com/health
```

Expect `HTTP/1.1 200` and `{"status":"UP"}`. If the service was asleep, that first call
is the one that wakes it and will be slow; a second call straight after should be fast.

---

## 6. What is covered by tests

`HealthEndpointTest` pins the pieces that would otherwise break quietly:

- `/health` returns `200` with `status` = `UP`, unauthenticated
- the body is exactly `{"status":"UP"}`, as JSON — a monitor may match on it
- credentials that do not authenticate are refused with `401`, even here
- `/api/admin/**` is still protected, with and without wrong credentials
- `/api/events` still serves the public catalogue

No credentials, connection strings or keys appear in this file, and none are needed to
call the endpoint.
