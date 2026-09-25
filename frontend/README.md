# Engineers' Day 2026 — Frontend

The practical event discovery and registration site for MIT TECH KERNEL's Engineers' Day 2026.

**Zero 3D.** No Three.js, no WebGL, no models. The cyberpunk experience layer is built
entirely from DOM, CSS and SVG, choreographed with GSAP + ScrollTrigger and smoothed by
Lenis. Every effect — scanlines, grain, chromatic split, glitch, CRT sweep — is a composited
2D layer, which is why the whole thing holds 60fps on hardware that would stall on a scene
graph.

The site is two layers that meet at the router: the **experience** (boot sequence, hero, and
the scroll campaign that follows) and the **practical platform** (event discovery,
eligibility, registration). The second works completely without the first.

---

## Run it

The frontend needs the backend running. From two terminals:

```bash
# terminal 1 — database + API
cd backend
docker compose up -d
$env:DB_URL="jdbc:postgresql://localhost:55432/engineers_day"   # PowerShell
$env:DB_USERNAME="engineers_day"
$env:DB_PASSWORD="engineers_day"
java -jar target\registration-service-0.1.0-SNAPSHOT.jar
```

```bash
# terminal 2 — frontend
cd frontend
npm install
npm run dev          # http://localhost:5173
```

Vite proxies `/api` to `http://localhost:8080`, so the browser sees a same-origin request and
CORS never enters the development loop. Point it elsewhere with `VITE_API_TARGET`, or set
`VITE_API_BASE_URL` for a production build served from a different origin.

| Script | Does |
|---|---|
| `npm run dev` | Dev server on :5173 with the API proxy |
| `npm run build` | Type-check, then production build to `dist/` |
| `npm run preview` | Serve the production build |

---

## Architecture

The organising rule: **the experience layer may depend on the practical one, never the
reverse.** Deleting `boot/` and `sections/` must leave a working, registerable site.

```
src/
  app/          shell — layout, header, footer, router, experience route
  boot/         initialization sequence
  sections/     experience-layer sections (hero, and the campaign to follow)
  animations/   GSAP setup and reusable motion helpers
  pages/        one file per route, thin
  features/     composed product surfaces (events, registration)
  components/   design-system primitives, zero domain knowledge
  domain/       PURE TypeScript — types + presentation rules. No React, no fetch.
  services/     the HTTP boundary. The only place fetch() appears.
  hooks/        async state
  data/         static content: site copy, boot log, per-event visual identity
  lib/          framework-agnostic helpers
  styles/       design tokens and per-surface stylesheets
  styles/       design tokens + base layer
```

**`domain/` imports nothing.** It holds the types mirroring the API wire format and the
derived helpers (`teamSizeLabel`, `availabilityOf`, `hasPublishedCapacity`). It is importable
by the experience layer without dragging any UI along with it.

**`services/` is the only module that calls `fetch`.** Everything failure-shaped — a 422, a
502, a dropped connection, an HTML error page from a misconfigured proxy — becomes one
`ApiError` with a code the UI branches on. No component ever sees a `Response`.

### The backend is authoritative

Client-side checks exist to give immediate feedback and to avoid requests that are certain to
be refused. They are never the decision. Eligibility, team size, capacity, duplicates and the
clock are all re-checked server-side, and when the two disagree the student sees the server's
answer.

Concretely: the form can tell a second-year that BuildX is first-year only, but only the
server knows whether the roll number they typed is *already on record* as a second year.

### Event rules are data

There is no `if (eventId === 'tech-debate')` anywhere in this codebase, and no per-event JSX.
One `EventCard`, one `RegistrationForm`, one `ParticipantFields` — all driven by the `Event`
the API returns. Adding an eighth event is a backend migration plus one entry in
`data/siteContent.ts` for its accent colour.

The seven are Chess, Tech Debate, FIX IT, Ideathon, BuildX, Debugging and Rapid Research —
six open to first years, five to second years, with FIX IT common to both and Rapid Research
exclusive to second year.

---

## Routes

| Route | Page |
|---|---|
| `/` | Experience — boot sequence, then the hero and scroll campaign |
| `/events` | Discovery grid with year and entry filters (filter state in the URL) |
| `/events/:eventId` | Full detail, requirements, availability, register action |
| `/register/:eventId` | Registration form — solo or team |
| `/registration/success` | Confirmation with registration IDs |
| `/my-registrations` | A participant's own registrations |
| `*` | Not found |

`/` is the only route outside the practical shell: the experience owns the full viewport and
carries its own HUD navigation instead of the standard header. It links into the practical
site through plain `/events/:eventId` URLs, so the two layers never share state — only
routes.

---

## API consumed

| Endpoint | Used by |
|---|---|
| `GET /api/events` | Events grid |
| `GET /api/events/{eventId}` | Event detail, registration page |
| `POST /api/registrations` | Registration form |
| `GET /api/registrations/{participantId}` | My registrations (remembered device) |
| `GET /api/registrations?rollNo=` | My registrations (lookup) |

Every backend error code is mapped to a titled, actionable state in `services/apiError.ts`:
`CAPACITY_FULL`, `REGISTRATION_CLOSED`, `DUPLICATE_REGISTRATION`, `INELIGIBLE_YEAR`,
`INVALID_TEAM_SIZE`, `SOLO_EVENT_REJECTS_TEAM`, `TEAM_NAME_REQUIRED`,
`PARTICIPANT_IDENTITY_CONFLICT`, `VALIDATION_FAILED`, plus client-side `NETWORK` and
`UNAVAILABLE`. Raw messages and stack traces are never shown.

`VALIDATION_FAILED` details are keyed `participants[0].email` — exactly how the roster form
addresses its own inputs — so per-field server errors attach to the right control with no
translation.

### Idempotency

One key per registration *attempt*, held across retries of the same payload. Editing the
roster generates a new key; pressing "try again" unchanged reuses it. Regenerating on every
click would defeat the mechanism — if the server committed and the response was lost, a fresh
key creates a second registration.

---

## Identity

There is no authentication on the backend yet. This frontend does not invent any.

Everything goes through `services/identitySession.ts`, an interface with one device-local
implementation. It remembers who registered in this browser (a convenience, never a
credential, never sent as one) so `/my-registrations` self-populates. The roll-number lookup
is marked in the UI as a temporary mechanism.

When email OTP or SSO arrives: implement `IdentitySession` against it and swap the single
exported instance. No page, form or component changes.

---

## Design

Dark near-black ground, one brand signal (sodium amber), one technical voice (cold cyan), and
semantic status colours kept deliberately separate from the accent so "open" never looks like
"button". Angular geometry via `clip-path` corner notches rather than border radius. The
ambient grid and scanlines are texture, not pattern.

Type: **Chakra Petch** display, **Barlow** body, **JetBrains Mono** for technical labels.

Status is never carried by colour alone — each state has a distinct label and indicator.

---

## Accessibility

- Semantic landmarks, one `<h1>` per page, real heading order
- Skip link as the first focusable element
- Every control has a `<label for>`; hints and errors wired via `aria-describedby`
- Visible focus rings on the amber signal, never removed
- Filters are real radio groups — arrow-key navigable, announced as "1 of 3"
- `role="alert"` on error states, `role="status"` on loading
- Full `prefers-reduced-motion` support; the site is completely usable with animation off
- Registration requires no animation, no canvas and no pointer

---

## Known gaps

- **Ideathon capacity is unset.** The dev database has `capacity = NULL` (renders as "No
  published limit"); the seed migration still contains the earlier placeholder of 40. Settle
  the number, then add a `V3` migration — `V2` has already run and Flyway checksums it.
- **No instructions field.** The API carries none, so the event page derives "How to enter"
  strictly from the entry rules it does send. The section grows when the field lands.
- **`/my-registrations` is unauthenticated**, as above.
