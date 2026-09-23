CREATE TABLE user_profiles (
    user_id UUID PRIMARY KEY,
    riot_id VARCHAR(22),
    primary_position VARCHAR(16),
    secondary_position VARCHAR(16),
    introduction VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_profiles_riot_id CHECK (
        riot_id IS NULL OR (
            char_length(riot_id) BETWEEN 7 AND 22
            AND riot_id = btrim(riot_id)
            AND position('#' IN riot_id) BETWEEN 4 AND 17
            AND position('#' IN substring(riot_id FROM position('#' IN riot_id) + 1)) = 0
            AND char_length(substring(riot_id FROM 1 FOR position('#' IN riot_id) - 1)) BETWEEN 3 AND 16
            AND char_length(substring(riot_id FROM position('#' IN riot_id) + 1)) BETWEEN 3 AND 5
        )
    ),
    CONSTRAINT ck_user_profiles_primary_position CHECK (
        primary_position IS NULL OR primary_position IN ('TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT')
    ),
    CONSTRAINT ck_user_profiles_secondary_position CHECK (
        secondary_position IS NULL OR secondary_position IN ('TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT')
    ),
    CONSTRAINT ck_user_profiles_positions CHECK (
        secondary_position IS NULL OR (primary_position IS NOT NULL AND primary_position <> secondary_position)
    ),
    CONSTRAINT ck_user_profiles_introduction CHECK (
        introduction IS NULL OR char_length(introduction) <= 500
    )
);

INSERT INTO user_profiles (user_id, created_at, updated_at)
SELECT id, created_at, updated_at
FROM users;
