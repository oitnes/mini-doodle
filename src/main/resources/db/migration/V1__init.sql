-- btree_gist lets the GiST exclusion constraint below combine a scalar
-- equality column (calendar_id) with a range column (tstzrange).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    display_name  VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE calendar (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL UNIQUE REFERENCES users(id),
    timezone   VARCHAR(100) NOT NULL DEFAULT 'UTC',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE time_slot (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id UUID        NOT NULL REFERENCES calendar(id),
    start_at    TIMESTAMPTZ NOT NULL,
    end_at      TIMESTAMPTZ NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'FREE',
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_slot_duration CHECK (end_at > start_at),
    -- ARCHITECTURE DECISION: overlap prevention lives in the database, not in
    -- application code. A check-then-insert in the service would race under
    -- concurrency; the exclusion constraint is atomic and cannot be bypassed
    -- by any code path. Half-open ranges '[)' make touching slots
    -- ([09:00,10:00) + [10:00,11:00)) legal.
    CONSTRAINT no_overlap EXCLUDE USING gist (
        calendar_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
);

CREATE INDEX idx_slot_calendar_range
    ON time_slot USING gist (calendar_id, tstzrange(start_at, end_at, '[)'));
CREATE INDEX idx_slot_status
    ON time_slot (calendar_id, status, start_at);

CREATE TABLE meeting (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id     UUID         NOT NULL UNIQUE REFERENCES time_slot(id),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE participant (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id      UUID        NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    user_id         UUID        REFERENCES users(id),
    email           VARCHAR(255),
    response_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT chk_participant_identity CHECK (user_id IS NOT NULL OR email IS NOT NULL)
);
