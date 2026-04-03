-- Создание схемы для сервиса маршрутов и планирования

CREATE EXTENSION IF NOT EXISTS postgis;

-- =========================
-- Таблица маршрутов
-- =========================
CREATE TABLE IF NOT EXISTS routes (
                                      id BIGSERIAL PRIMARY KEY,
                                      name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    cover_photo_url VARCHAR(500),

    transport_mode VARCHAR(16) NOT NULL
    CHECK (transport_mode IN ('WALK', 'PUBLIC_TRANSPORT', 'CAR', 'MIXED')),

    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
    CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED')),

    is_optimized BOOLEAN DEFAULT FALSE,
    optimization_mode VARCHAR(255),

    distance_km NUMERIC(8,2),
    duration_min INTEGER,

    start_point VARCHAR(300),
    end_point VARCHAR(300),

    user_id BIGINT NOT NULL,
    city_id BIGINT NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_routes_user_id      ON routes(user_id);
CREATE INDEX IF NOT EXISTS idx_routes_city_id      ON routes(city_id);
CREATE INDEX IF NOT EXISTS idx_routes_created_at   ON routes(created_at);
CREATE INDEX IF NOT EXISTS idx_routes_status       ON routes(status);

-- =========================
-- Таблица дней маршрута
-- =========================
CREATE TABLE IF NOT EXISTS route_days (
                                          id BIGSERIAL PRIMARY KEY,
                                          day_number SMALLINT NOT NULL,
                                          planned_start TIMESTAMP,
                                          planned_end TIMESTAMP,
                                          description VARCHAR(500),

    route_id BIGINT NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_route_days_route
    FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_route_days_route_id    ON route_days(route_id);
CREATE INDEX IF NOT EXISTS idx_route_days_day_number  ON route_days(day_number);

-- =========================
-- Таблица точек маршрута
-- =========================
CREATE TABLE IF NOT EXISTS route_points (
                                            id BIGSERIAL PRIMARY KEY,
                                            order_index SMALLINT NOT NULL,

                                            poi_id BIGINT NOT NULL,

                                            poi_name VARCHAR(255),
    poi_address VARCHAR(500),
    poi_latitude DOUBLE PRECISION,
    poi_longitude DOUBLE PRECISION,
    poi_type VARCHAR(100),

    estimated_visit_minutes INTEGER NOT NULL DEFAULT 60,
    planned_arrival_at TIMESTAMP,
    planned_departure_at TIMESTAMP,

    route_day_id BIGINT NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_route_points_route_day
    FOREIGN KEY (route_day_id) REFERENCES route_days(id) ON DELETE CASCADE,

    CONSTRAINT uq_route_point_order
    UNIQUE (route_day_id, order_index)
    );

CREATE INDEX IF NOT EXISTS idx_route_points_route_day_id ON route_points(route_day_id);
CREATE INDEX IF NOT EXISTS idx_route_points_poi_id       ON route_points(poi_id);
CREATE INDEX IF NOT EXISTS idx_route_points_order_index  ON route_points(order_index);

-- =========================
-- updated_at trigger
-- =========================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_routes_updated_at ON routes;
CREATE TRIGGER update_routes_updated_at
    BEFORE UPDATE ON routes
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_route_days_updated_at ON route_days;
CREATE TRIGGER update_route_days_updated_at
    BEFORE UPDATE ON route_days
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_route_points_updated_at ON route_points;
CREATE TRIGGER update_route_points_updated_at
    BEFORE UPDATE ON route_points
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =========================
-- Кэш геометрии маршрута по дням
-- =========================
CREATE TABLE IF NOT EXISTS route_day_paths (
                                               id BIGSERIAL PRIMARY KEY,

                                               route_day_id BIGINT NOT NULL UNIQUE,

                                               provider VARCHAR(50) NOT NULL DEFAULT 'YANDEX',
    transport_mode VARCHAR(16) NOT NULL
    CHECK (transport_mode IN ('WALK', 'PUBLIC_TRANSPORT', 'CAR', 'MIXED')),

    geometry_source VARCHAR(30) NOT NULL DEFAULT 'ROADS'
    CHECK (geometry_source IN ('ROADS', 'STRAIGHT', 'FALLBACK')),

    distance_km NUMERIC(8,2),
    duration_min INTEGER,

    polyline_json JSONB NOT NULL DEFAULT '[]'::jsonb,

    built_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_route_day_paths_route_day
    FOREIGN KEY (route_day_id) REFERENCES route_days(id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_route_day_paths_route_day_id
    ON route_day_paths(route_day_id);

CREATE INDEX IF NOT EXISTS idx_route_day_paths_transport_mode
    ON route_day_paths(transport_mode);


-- =========================
-- Кэш геометрии сегментов между соседними точками
-- =========================
CREATE TABLE IF NOT EXISTS route_segment_paths (
                                                   id BIGSERIAL PRIMARY KEY,

                                                   route_day_id BIGINT NOT NULL,
                                                   from_route_point_id BIGINT NOT NULL,
                                                   to_route_point_id BIGINT NOT NULL,

                                                   segment_order SMALLINT NOT NULL,

                                                   provider VARCHAR(50) NOT NULL DEFAULT 'YANDEX',
    transport_mode VARCHAR(16) NOT NULL
    CHECK (transport_mode IN ('WALK', 'PUBLIC_TRANSPORT', 'CAR', 'MIXED')),

    geometry_source VARCHAR(30) NOT NULL DEFAULT 'ROADS'
    CHECK (geometry_source IN ('ROADS', 'STRAIGHT', 'FALLBACK')),

    status VARCHAR(20) NOT NULL DEFAULT 'OK'
    CHECK (status IN ('OK', 'NOT_FOUND', 'PARTIAL', 'ERROR')),

    distance_km NUMERIC(8,2),
    duration_min INTEGER,

    polyline_json JSONB NOT NULL DEFAULT '[]'::jsonb,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_route_segment_paths_route_day
    FOREIGN KEY (route_day_id) REFERENCES route_days(id) ON DELETE CASCADE,

    CONSTRAINT fk_route_segment_paths_from_point
    FOREIGN KEY (from_route_point_id) REFERENCES route_points(id) ON DELETE CASCADE,

    CONSTRAINT fk_route_segment_paths_to_point
    FOREIGN KEY (to_route_point_id) REFERENCES route_points(id) ON DELETE CASCADE,

    CONSTRAINT uq_route_segment_pair
    UNIQUE (route_day_id, from_route_point_id, to_route_point_id)
    );

CREATE INDEX IF NOT EXISTS idx_route_segment_paths_route_day_id
    ON route_segment_paths(route_day_id);

CREATE INDEX IF NOT EXISTS idx_route_segment_paths_segment_order
    ON route_segment_paths(segment_order);

CREATE INDEX IF NOT EXISTS idx_route_segment_paths_from_to
    ON route_segment_paths(from_route_point_id, to_route_point_id);


-- =========================
-- updated_at trigger
-- =========================
DROP TRIGGER IF EXISTS update_route_day_paths_updated_at ON route_day_paths;
CREATE TRIGGER update_route_day_paths_updated_at
    BEFORE UPDATE ON route_day_paths
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_route_segment_paths_updated_at ON route_segment_paths;
CREATE TRIGGER update_route_segment_paths_updated_at
    BEFORE UPDATE ON route_segment_paths
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE route_day_paths IS 'Кэш геометрии маршрута по дорогам для одного дня';
COMMENT ON TABLE route_segment_paths IS 'Кэш геометрии отдельных сегментов между соседними точками маршрута';

COMMENT ON TABLE routes IS 'Хранит пользовательские маршруты путешествий';
COMMENT ON TABLE route_days IS 'Хранит структуру маршрута по дням';
COMMENT ON TABLE route_points IS 'Хранит точки маршрута с порядком посещения';