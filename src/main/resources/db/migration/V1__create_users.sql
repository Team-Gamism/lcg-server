CREATE TABLE users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE school_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_school_identities_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_school_identities_provider_subject UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_school_identities_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_school_identities_provider CHECK (length(trim(provider)) > 0),
    CONSTRAINT ck_school_identities_subject CHECK (length(trim(provider_user_id)) > 0)
);
