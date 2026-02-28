-- Создание схемы для сервиса маршрутов и планирования

-- Расширение для работы с геоданными
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

    is_optimized BOOLEAN DEFAULT FALSE,
    optimization_mode VARCHAR(255),

    distance_km NUMERIC(8,2),
    duration_min INTEGER,

    start_point VARCHAR(300),
    end_point VARCHAR(300),

    is_archived SMALLINT DEFAULT 0
    CHECK (is_archived IN (0, 1)),

    -- В микросервисной архитектуре нельзя ставить FK на таблицы из других сервисов/БД.
    -- Поэтому храним только идентификаторы:
    user_id BIGINT NOT NULL,
    city_id BIGINT NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- Индексы для таблицы routes
CREATE INDEX IF NOT EXISTS idx_routes_user_id       ON routes(user_id);
CREATE INDEX IF NOT EXISTS idx_routes_city_id       ON routes(city_id);
CREATE INDEX IF NOT EXISTS idx_routes_created_at    ON routes(created_at);
CREATE INDEX IF NOT EXISTS idx_routes_is_archived   ON routes(is_archived);

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

-- Индексы для таблицы route_days
CREATE INDEX IF NOT EXISTS idx_route_days_route_id   ON route_days(route_id);
CREATE INDEX IF NOT EXISTS idx_route_days_day_number ON route_days(day_number);

-- =========================
-- Таблица точек маршрута
-- =========================
CREATE TABLE IF NOT EXISTS route_points (
                                            id BIGSERIAL PRIMARY KEY,
                                            order_index SMALLINT NOT NULL,

    -- POI живёт в другом сервисе (poi-service), поэтому FK нельзя.
                                            poi_id BIGINT NOT NULL,

                                            route_day_id BIGINT NOT NULL,

                                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                            CONSTRAINT fk_route_points_route_day
                                            FOREIGN KEY (route_day_id) REFERENCES route_days(id) ON DELETE CASCADE,

    CONSTRAINT uq_route_point_order
    UNIQUE (route_day_id, order_index)
    );

-- Индексы для таблицы route_points
CREATE INDEX IF NOT EXISTS idx_route_points_route_day_id ON route_points(route_day_id);
CREATE INDEX IF NOT EXISTS idx_route_points_poi_id       ON route_points(poi_id);
CREATE INDEX IF NOT EXISTS idx_route_points_order_index  ON route_points(order_index);

-- =========================
-- Функция и триггеры updated_at
-- =========================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Триггер для таблицы routes
CREATE TRIGGER update_routes_updated_at
    BEFORE UPDATE ON routes
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Триггер для таблицы route_days
CREATE TRIGGER update_route_days_updated_at
    BEFORE UPDATE ON route_days
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Триггер для таблицы route_points
CREATE TRIGGER update_route_points_updated_at
    BEFORE UPDATE ON route_points
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =========================
-- Комментарии
-- =========================
COMMENT ON TABLE routes IS 'Хранит пользовательские маршруты путешествий';
COMMENT ON TABLE route_days IS 'Хранит структуру маршрута по дням';
COMMENT ON TABLE route_points IS 'Хранит точки маршрута с порядком посещения';