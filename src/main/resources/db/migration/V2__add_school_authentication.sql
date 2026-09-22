ALTER TABLE users
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    ADD CONSTRAINT ck_users_role CHECK (role IN ('MEMBER', 'ADMIN')),
    ADD CONSTRAINT ck_users_session_version CHECK (session_version >= 0);

-- Existing identities require a fresh school login before receiving a session.
ALTER TABLE school_identities
    ADD COLUMN school_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN verified_grade INTEGER,
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_school_identities_grade CHECK (verified_grade BETWEEN 1 AND 3),
    ADD CONSTRAINT ck_school_identities_verification CHECK (
        NOT school_eligible OR (verified_grade IS NOT NULL AND verified_at IS NOT NULL)
    );
