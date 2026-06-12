# Mini-Doodle

A scheduling platform prototype — create time slots, query availability, and book meetings.

Built with Java 17, Spring Boot 3.5, PostgreSQL 16, Flyway, and Testcontainers.

---

## Quick start

```bash
docker-compose up
```

The app starts on `http://localhost:8080` after the Postgres healthcheck passes.
The full API contract is documented below.

Database data lives in a named Docker volume and survives `docker-compose down`;
reset everything with `docker-compose down -v`.

---

## API

All timestamps are ISO 8601 (UTC). UUIDs are used for all IDs.

### Users

```
POST /api/v1/users
{
  "email": "alice@example.com",
  "displayName": "Alice",
  "timezone": "Europe/Warsaw"   // optional IANA zone id, defaults to UTC
}
→ 201 UserResponse
```

### Slots

```
POST   /api/v1/users/{userId}/slots
{
  "startAt": "2026-06-15T09:00:00Z",
  "endAt":   "2026-06-15T10:00:00Z"   // exactly one of endAt / durationMinutes
}

GET    /api/v1/users/{userId}/slots?page=0&size=50
→ 200
{
  "content": [ SlotResponse, ... ],
  "page": { "size": 50, "number": 0, "totalElements": 3, "totalPages": 1 }
}

GET    /api/v1/users/{userId}/slots/{slotId}

PATCH  /api/v1/users/{userId}/slots/{slotId}
{
  "startAt": "2026-06-15T09:30:00Z",  // all fields optional
  "endAt":   "2026-06-15T10:30:00Z",
  "status":  "BUSY"                   // mark busy/free by hand
}

DELETE /api/v1/users/{userId}/slots/{slotId}
```

A slot can be marked `BUSY`/`FREE` freely via PATCH as long as no meeting is
attached to it. Once a meeting is booked on the slot, PATCH and DELETE return
409 — the meeting owns the slot, and the only way to free it is to cancel the
meeting.

### Availability (aggregated free/busy view)

```
GET /api/v1/users/{userId}/availability?from=...&to=...&status=FREE

→ [
    {
      "startAt": "2026-06-15T09:00:00Z",
      "endAt":   "2026-06-15T11:00:00Z",
      "status":  "FREE",
      "slotIds": ["…", "…"]    // the merged underlying slots
    },
    ...
  ]
```

Returns contiguous same-status time blocks in `[from, to)`, sorted by start.
Adjacent slots with equal status are merged into one block (four back-to-back
30-minute FREE slots come back as a single 2-hour FREE block); `slotIds`
preserves the link to the underlying slots so a caller can pick one to book.
Slots partially overlapping the window are included with their full range.
The optional `status` filter restricts the view to FREE or BUSY blocks.
The window is capped at 1 year.

### Meetings

```
POST /api/v1/users/{userId}/slots/{slotId}/meeting
{
  "title": "Sprint planning",
  "description": "Q3 kickoff",
  "participants": [{"email": "bob@example.com"}]
}
→ 201 MeetingResponse

GET    /api/v1/users/{userId}/meetings/{meetingId}
DELETE /api/v1/users/{userId}/meetings/{meetingId}    // idempotent, frees the slot
```

Meetings, like slots, are addressed through the owning user — only the slot
owner's path can read or cancel a meeting.

### Errors

All 4xx responses share one body shape:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Slot is already booked: 6f1d…",
  "timestamp": "2026-06-15T09:00:00Z"
}
```

| Code | When |
|------|------|
| 400 | Validation failure, malformed body/params, invalid time range or timezone, unknown sort property, window > 1 year |
| 404 | Unknown user/slot/meeting, or reading another user's slot/meeting (foreign DELETE of a meeting is an idempotent 204 no-op) |
| 409 | Overlapping slot, booking a BUSY slot, modifying/deleting a booked slot, concurrent booking lost, duplicate email |

---

## Design decisions

### Calendar is domain-only, not a REST resource

A `Calendar` is created automatically when a user is created. It's the internal container
for a user's time slots. It is never exposed as a top-level API path; all slot and
availability operations are addressed through the owning user.

### Preventing overlapping slots: exclusion constraint

```sql
CONSTRAINT no_overlap EXCLUDE USING gist (
    calendar_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
)
```

This makes overlapping slots *impossible at the storage layer*. Half-open `[)` intervals
mean touching boundaries (e.g. `[09:00,10:00)` and `[10:00,11:00)`) are allowed —
adjacent slots don't overlap.

A GiST index on the range supports both the constraint and range queries for the
availability endpoint.

### Slot lifecycle: who may mark a slot busy or free

A slot's status can be toggled by hand (`PATCH … "status": "BUSY"|"FREE"`) —
e.g. to block out time without a meeting. The moment a meeting is attached,
the slot becomes immutable: the meeting is the source of truth for why the
time is busy, and freeing it must go through meeting cancellation so slot
status and meeting existence can never disagree.

### Aggregated availability

The availability endpoint merges adjacent same-status slots into contiguous
blocks in memory (single O(n) pass over the window's slots, which the DB
returns already sorted and non-overlapping). Aggregation in SQL
(gaps-and-islands) was considered and rejected: with the 1-year window cap a
single calendar yields at most a few thousand rows, so the in-memory merge is
simpler and fast enough.

### Preventing double-booking: optimistic locking

`TimeSlot` carries a `@Version` column. The booking flow is:

```
1. Load slot (version = N)
2. Assert status == FREE        → 409 if already BUSY
3. Set status = BUSY
4. Insert Meeting row
5. Commit                       → flush runs UPDATE ... WHERE version = N
```

Two concurrent requests racing for the same FREE slot both pass step 2. One
commits first; the loser then fails on whichever defence its flush hits first —
in practice the `meeting.slot_id` UNIQUE constraint (Hibernate flushes the
meeting INSERT before the version-checked slot UPDATE), with the stale
`@Version` check as the second line. Either way the loser gets a 409.

The two independent defences:

| Layer | Mechanism | Handles |
|-------|-----------|---------|
| Application | `@Version` optimistic lock | Concurrent booking of same slot |
| Database | `meeting.slot_id UNIQUE` | Concurrent insert after lock bypass |

### Timestamps and timezones

All timestamps are stored as `timestamptz` (UTC). The user's preferred timezone lives on the
`Calendar` entity and is used only for display purposes. All range queries and comparisons
operate in UTC.

### Pagination

`GET /slots` is paginated (default size 50, sorted by `startAt`) to avoid unbounded
result sets on active calendars.

---

## Testing

### How the tests work

All tests are black-box integration tests: they boot the full Spring context
on a random port, talk to the real REST API via `TestRestTemplate`, and run
against a **real PostgreSQL 16** started by Testcontainers (one container,
started once for the whole suite). No mocks, no H2 — the exclusion
constraint, GiST indexes, and locking behaviour under test only exist in
real Postgres.

### Running them

Requirements: JDK 17 and a running Docker daemon (Testcontainers pulls
`postgres:16` on first run).

```bash
./gradlew test
```

Reports land in `build/reports/tests/test/index.html`.

### Covered scenarios

- Slot creation with `endAt` and with `durationMinutes`
- Overlapping slot rejected at the DB level (exclusion constraint → 409)
- Fetching a single slot
- Marking a slot BUSY and back to FREE by hand (no meeting attached)
- Deleting a manually-busied slot succeeds
- Updating or deleting a slot with a meeting returns 409
- Booking a FREE slot succeeds; booking a BUSY slot returns 409
- **Concurrent booking: 8 threads race; exactly one wins, the rest get 409**
- Fetching a meeting (incl. participants); missing meeting returns 404
- Cancel meeting frees the slot; re-booking succeeds; cancel is idempotent
- Availability: window queries, status filter, partial overlaps included
- Availability aggregation: adjacent same-status slots merge into one block
  (with their slot ids); a status change breaks the block

---

## What would come next

**Recurring slots** — RRULE-style recurrence. Trade-off: expand eagerly (simpler queries,
larger table) vs. expand on read (smaller storage, more complex availability logic).

**Multi-participant availability** — find times when all N participants are free. The hard
Doodle problem; requires intersecting free/busy ranges across N calendars.

**Caching availability** — Redis keyed by `(calendar, window)`, invalidated on slot mutation.
Right for read-heavy load; complex cache invalidation is the main risk.

**Pessimistic locking** — `SELECT … FOR UPDATE` instead of `@Version` if booking contention
on hot slots turns out to be high.

**Outbox + events** — emit `MeetingBooked`/`MeetingCancelled` for notifications and calendar
sync without coupling the booking transaction to delivery.

**AuthN/AuthZ** — the prototype trusts `userId` in the path. Production needs JWT and
ownership checks so users can only mutate their own calendar. Ownership scoping is
already uniform across all endpoints, so this means validating one identity.

**Rate limiting** — registration and slot creation are unauthenticated and unmetered.
Per-request work is bounded (page size, availability window, participant count), but
nothing limits request volume.

**Enumeration trade-offs** — duplicate-email registration returns 409, and participant
responses expose `userId` for registered emails; both deliberately favour API clarity
over hiding which emails have accounts.

**Participant double-booking** — flag (or block) when a participant is already in a
conflicting meeting. Currently modelled but not enforced at the application layer.
