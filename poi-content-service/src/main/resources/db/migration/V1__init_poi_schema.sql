-- POI & Content Service Database Schema
-- Version 1.0

-- POI Type table
CREATE TABLE poi_type (
    id SERIAL PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    icon VARCHAR(120),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- POI table
CREATE TABLE poi (
    id SERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    tags JSONB,
    description TEXT,
    address VARCHAR(300),
    latitude DECIMAL(9,6) NOT NULL,
    longitude DECIMAL(9,6) NOT NULL,
    phone VARCHAR(32),
    site_url VARCHAR(300),
    price_level SMALLINT CHECK (price_level >= 0 AND price_level <= 4),
    average_rating NUMERIC(3,2) DEFAULT 0.00,
    rating_count INTEGER DEFAULT 0,
    is_verified BOOLEAN DEFAULT FALSE,
    is_closed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by INTEGER NOT NULL, -- FK to User in auth service
    city_id INTEGER NOT NULL, -- FK to City in city service
    poi_type_id INTEGER NOT NULL REFERENCES poi_type(id),

    -- Indexes for performance
    CONSTRAINT avg_rating_range CHECK (average_rating >= 0 AND average_rating <= 5),
    INDEX idx_poi_city (city_id),
    INDEX idx_poi_type (poi_type_id),
    INDEX idx_poi_location (latitude, longitude),
    INDEX idx_poi_rating (average_rating DESC),
    INDEX idx_poi_verified (is_verified),
    INDEX idx_poi_slug (slug)
);

-- POI Features table
CREATE TABLE poi_features (
    id SERIAL PRIMARY KEY,
    key VARCHAR(64) NOT NULL,
    value VARCHAR(200),
    poi_id INTEGER NOT NULL REFERENCES poi(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_poi_features_poi (poi_id),
    INDEX idx_poi_features_key (key),
    UNIQUE (poi_id, key)
);

-- POI Hours table
CREATE TABLE poi_hours (
    id SERIAL PRIMARY KEY,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week >= 0 AND day_of_week <= 6),
    open_time TIME,
    close_time TIME,
    around_the_clock BOOLEAN DEFAULT FALSE,
    poi_id INTEGER NOT NULL REFERENCES poi(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_poi_hours_poi (poi_id),
    INDEX idx_poi_hours_day (day_of_week)
);

-- POI Media table
CREATE TABLE poi_media (
    id SERIAL PRIMARY KEY,
    url VARCHAR(500) NOT NULL,
    media_type VARCHAR(32) NOT NULL CHECK (media_type IN ('photo', 'cover', 'menu', 'video')),
    moderation_status VARCHAR(16) DEFAULT 'PENDING' CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER NOT NULL, -- FK to User in auth service
    poi_id INTEGER NOT NULL REFERENCES poi(id) ON DELETE CASCADE,

    INDEX idx_poi_media_poi (poi_id),
    INDEX idx_poi_media_type (media_type),
    INDEX idx_poi_media_status (moderation_status),
    INDEX idx_poi_media_user (user_id)
);

-- POI Source table
CREATE TABLE poi_source (
    id SERIAL PRIMARY KEY,
    source_code VARCHAR(32) NOT NULL,
    source_url VARCHAR(500),
    confidence_score NUMERIC(3,2) CHECK (confidence_score >= 0 AND confidence_score <= 1),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    poi_id INTEGER NOT NULL REFERENCES poi(id) ON DELETE CASCADE,

    INDEX idx_poi_source_poi (poi_id),
    INDEX idx_poi_source_code (source_code)
);

-- Data Import Tasks table
CREATE TABLE data_import_task (
    id SERIAL PRIMARY KEY,
    source_code VARCHAR(32) NOT NULL,
    query VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED')),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    total_poi_found INTEGER DEFAULT 0,
    total_poi_created INTEGER DEFAULT 0,
    total_poi_updated INTEGER DEFAULT 0,
    error_message VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    city_id INTEGER, -- FK to City in city service

    INDEX idx_import_task_status (status),
    INDEX idx_import_task_city (city_id),
    INDEX idx_import_task_created (created_at DESC)
);

-- Enable PostGIS extension for spatial queries
CREATE EXTENSION IF NOT EXISTS postgis;

-- Add spatial index for POI locations
CREATE INDEX idx_poi_geom ON poi USING GIST (
    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)
);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updated_at
CREATE TRIGGER update_poi_updated_at
    BEFORE UPDATE ON poi
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_poi_type_updated_at
    BEFORE UPDATE ON poi_type
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_poi_source_updated_at
    BEFORE UPDATE ON poi_source
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_data_import_task_updated_at
    BEFORE UPDATE ON data_import_task
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();