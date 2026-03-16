-- =========================
-- POI Content Service schema (v1)
-- Based on current JPA Entities
-- =========================

-- -------------------------
-- poi_type
-- -------------------------
CREATE TABLE IF NOT EXISTS poi_type (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(32) NOT NULL UNIQUE,
    name        VARCHAR(64) NOT NULL,
    icon        VARCHAR(120),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------
-- poi
-- -------------------------
CREATE TABLE IF NOT EXISTS poi (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    tags            JSONB,
    description     TEXT,
    address         VARCHAR(300),

    latitude        DECIMAL(9,6) NOT NULL,
    longitude       DECIMAL(9,6) NOT NULL,

    phone           VARCHAR(32),
    site_url        VARCHAR(300),

    price_level     SMALLINT CHECK (price_level >= 0 AND price_level <= 4),

    is_verified     BOOLEAN DEFAULT FALSE,
    is_closed       BOOLEAN DEFAULT FALSE,

    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    created_by      BIGINT NOT NULL,
    city_id         BIGINT NOT NULL,

    poi_type_id     BIGINT NOT NULL REFERENCES poi_type(id)

);

-- -------------------------
-- poi_feature  (PoiFeature entity)
-- -------------------------
CREATE TABLE IF NOT EXISTS poi_feature (
    id          BIGSERIAL PRIMARY KEY,
    poi_id      BIGINT NOT NULL REFERENCES poi(id) ON DELETE CASCADE,

    key         VARCHAR(64) NOT NULL,
    value       VARCHAR(200),

    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- (Опционально) Если хочешь уникальность ключа для POI как раньше:
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_poi_feature_poi_key ON poi_feature(poi_id, key);

-- -------------------------
-- poi_hours
-- -------------------------
CREATE TABLE IF NOT EXISTS poi_hours (
    id              BIGSERIAL PRIMARY KEY,
    poi_id          BIGINT NOT NULL REFERENCES poi(id) ON DELETE CASCADE,

    day_of_week     SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    open_time       TIME,
    close_time      TIME,
    around_the_clock BOOLEAN DEFAULT FALSE,

    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- (Опционально) Если хочешь 1 запись на день:
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_poi_hours_poi_day ON poi_hours(poi_id, day_of_week);

-- -------------------------
-- poi_media
-- -------------------------
CREATE TABLE IF NOT EXISTS poi_media (
    id                  BIGSERIAL PRIMARY KEY,
    poi_id              BIGINT NOT NULL REFERENCES poi(id) ON DELETE CASCADE,

    url                 VARCHAR(500) NOT NULL,

    media_type          VARCHAR(32) NOT NULL,         -- enum MediaType
    moderation_status   VARCHAR(16) DEFAULT 'PENDING', -- enum ModerationStatus

    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    user_id             BIGINT NOT NULL
);

-- -------------------------
-- poi_source
-- -------------------------
CREATE TABLE IF NOT EXISTS poi_source (
    id                  BIGSERIAL PRIMARY KEY,
    poi_id              BIGINT NOT NULL REFERENCES poi(id) ON DELETE CASCADE,

    source_code         VARCHAR(32) NOT NULL,
    source_url          VARCHAR(500),

    confidence_score    DECIMAL(3,2),

    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- -------------------------
-- data_import_task
-- -------------------------
CREATE TABLE IF NOT EXISTS data_import_task (
    id                  BIGSERIAL PRIMARY KEY,

    source_code         VARCHAR(32) NOT NULL,
    query               VARCHAR(255) NOT NULL,

    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- enum ImportStatus

    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,

    total_poi_found     INTEGER DEFAULT 0,
    total_poi_created   INTEGER DEFAULT 0,
    total_poi_updated   INTEGER DEFAULT 0,

    error_message       VARCHAR(1000),

    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    city_id             BIGINT
);

-- =========================
-- Indexes
-- =========================

-- poi
CREATE INDEX IF NOT EXISTS idx_poi_city_id         ON poi(city_id);
CREATE INDEX IF NOT EXISTS idx_poi_type_id         ON poi(poi_type_id);
CREATE INDEX IF NOT EXISTS idx_poi_location        ON poi(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_poi_verified        ON poi(is_verified);
CREATE INDEX IF NOT EXISTS idx_poi_created_at      ON poi(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_poi_updated_at      ON poi(updated_at DESC);

-- jsonb tags
CREATE INDEX IF NOT EXISTS idx_poi_tags_gin        ON poi USING GIN(tags);

-- children
CREATE INDEX IF NOT EXISTS idx_poi_feature_poi_id  ON poi_feature(poi_id);
CREATE INDEX IF NOT EXISTS idx_poi_hours_poi_id    ON poi_hours(poi_id);
CREATE INDEX IF NOT EXISTS idx_poi_media_poi_id    ON poi_media(poi_id);
CREATE INDEX IF NOT EXISTS idx_poi_source_poi_id   ON poi_source(poi_id);

-- data import task
CREATE INDEX IF NOT EXISTS idx_import_task_status  ON data_import_task(status);
CREATE INDEX IF NOT EXISTS idx_import_task_city_id ON data_import_task(city_id);
CREATE INDEX IF NOT EXISTS idx_import_task_created ON data_import_task(created_at DESC);

-- =========================
-- Seed data (poi_type)
-- =========================
INSERT INTO poi_type (code, name, icon) VALUES
    ('restaurant', 'Ресторан', 'restaurant-icon'),
    ('cafe',       'Кафе',     'cafe-icon'),
    ('museum',     'Музей',    'museum-icon'),
    ('park',       'Парк',     'park-icon'),
    ('hotel',      'Отель',    'hotel-icon'),
    ('shop',       'Магазин',  'shop-icon'),
    ('atm',        'Банкомат', 'atm-icon'),
    ('pharmacy',   'Аптека',   'pharmacy-icon'),
    ('hospital',   'Больница', 'hospital-icon'),
    ('school',     'Школа',    'school-icon')
ON CONFLICT (code) DO NOTHING;