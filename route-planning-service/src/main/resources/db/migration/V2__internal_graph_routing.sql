-- =========================================
-- V2__internal_graph_routing.sql
-- Переход с внешнего routing provider на внутренний граф дорог
-- =========================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- =========================================
-- 1. Обновление route_day_paths
-- =========================================

ALTER TABLE route_day_paths
    ALTER COLUMN provider SET DEFAULT 'INTERNAL_GRAPH';

ALTER TABLE route_day_paths
DROP CONSTRAINT IF EXISTS route_day_paths_geometry_source_check;

ALTER TABLE route_day_paths
    ADD CONSTRAINT route_day_paths_geometry_source_check
        CHECK (geometry_source IN ('GRAPH', 'STRAIGHT', 'FALLBACK'));

UPDATE route_day_paths
SET provider = 'INTERNAL_GRAPH'
WHERE provider = 'YANDEX';

UPDATE route_day_paths
SET geometry_source = 'GRAPH'
WHERE geometry_source = 'ROADS';

-- =========================================
-- 2. Обновление route_segment_paths
-- =========================================

ALTER TABLE route_segment_paths
    ALTER COLUMN provider SET DEFAULT 'INTERNAL_GRAPH';

ALTER TABLE route_segment_paths
DROP CONSTRAINT IF EXISTS route_segment_paths_geometry_source_check;

ALTER TABLE route_segment_paths
    ADD CONSTRAINT route_segment_paths_geometry_source_check
        CHECK (geometry_source IN ('GRAPH', 'STRAIGHT', 'FALLBACK'));

UPDATE route_segment_paths
SET provider = 'INTERNAL_GRAPH'
WHERE provider = 'YANDEX';

UPDATE route_segment_paths
SET geometry_source = 'GRAPH'
WHERE geometry_source = 'ROADS';

-- =========================================
-- 3. Узлы дорожного графа
-- =========================================

CREATE TABLE IF NOT EXISTS road_nodes (
                                          id BIGSERIAL PRIMARY KEY,
                                          city_id BIGINT NOT NULL,

                                          latitude DOUBLE PRECISION NOT NULL,
                                          longitude DOUBLE PRECISION NOT NULL,

                                          geom geometry(Point, 4326) NOT NULL,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_road_nodes_city_id
    ON road_nodes(city_id);

CREATE INDEX IF NOT EXISTS idx_road_nodes_geom
    ON road_nodes USING GIST (geom);

DROP TRIGGER IF EXISTS update_road_nodes_updated_at ON road_nodes;
CREATE TRIGGER update_road_nodes_updated_at
    BEFORE UPDATE ON road_nodes
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =========================================
-- 4. Рёбра дорожного графа
-- =========================================

CREATE TABLE IF NOT EXISTS road_edges (
                                          id BIGSERIAL PRIMARY KEY,
                                          city_id BIGINT NOT NULL,

                                          from_node_id BIGINT NOT NULL,
                                          to_node_id BIGINT NOT NULL,

                                          length_m DOUBLE PRECISION NOT NULL,

                                          walk_allowed BOOLEAN NOT NULL DEFAULT TRUE,
                                          car_allowed BOOLEAN NOT NULL DEFAULT FALSE,
                                          mixed_allowed BOOLEAN NOT NULL DEFAULT TRUE,
                                          public_transport_allowed BOOLEAN NOT NULL DEFAULT FALSE,

                                          walk_time_sec INTEGER,
                                          car_time_sec INTEGER,
                                          mixed_time_sec INTEGER,
                                          public_transport_time_sec INTEGER,

                                          bidirectional BOOLEAN NOT NULL DEFAULT TRUE,

    -- Геометрия ребра в PostGIS
                                          geom geometry(LineString, 4326) NOT NULL,

    -- Дополнительно для быстрого ответа на клиент без конвертации из geom
    polyline_json JSONB NOT NULL DEFAULT '[]'::jsonb,

    source VARCHAR(30) NOT NULL DEFAULT 'OSM',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_road_edges_from_node
    FOREIGN KEY (from_node_id) REFERENCES road_nodes(id) ON DELETE CASCADE,

    CONSTRAINT fk_road_edges_to_node
    FOREIGN KEY (to_node_id) REFERENCES road_nodes(id) ON DELETE CASCADE,

    CONSTRAINT chk_road_edges_not_same_node
    CHECK (from_node_id <> to_node_id)
    );

CREATE INDEX IF NOT EXISTS idx_road_edges_city_id
    ON road_edges(city_id);

CREATE INDEX IF NOT EXISTS idx_road_edges_from_node_id
    ON road_edges(from_node_id);

CREATE INDEX IF NOT EXISTS idx_road_edges_to_node_id
    ON road_edges(to_node_id);

CREATE INDEX IF NOT EXISTS idx_road_edges_geom
    ON road_edges USING GIST (geom);

DROP TRIGGER IF EXISTS update_road_edges_updated_at ON road_edges;
CREATE TRIGGER update_road_edges_updated_at
    BEFORE UPDATE ON road_edges
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =========================================
-- 5. Привязка POI к ближайшему узлу графа
-- =========================================

CREATE TABLE IF NOT EXISTS poi_graph_bindings (
                                                  poi_id BIGINT PRIMARY KEY,
                                                  city_id BIGINT NOT NULL,

                                                  nearest_node_id BIGINT NOT NULL,

                                                  snapped_latitude DOUBLE PRECISION,
                                                  snapped_longitude DOUBLE PRECISION,
                                                  snap_distance_m DOUBLE PRECISION,

                                                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                                  CONSTRAINT fk_poi_graph_bindings_nearest_node
                                                  FOREIGN KEY (nearest_node_id) REFERENCES road_nodes(id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_poi_graph_bindings_city_id
    ON poi_graph_bindings(city_id);

CREATE INDEX IF NOT EXISTS idx_poi_graph_bindings_nearest_node_id
    ON poi_graph_bindings(nearest_node_id);

DROP TRIGGER IF EXISTS update_poi_graph_bindings_updated_at ON poi_graph_bindings;
CREATE TRIGGER update_poi_graph_bindings_updated_at
    BEFORE UPDATE ON poi_graph_bindings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =========================================
-- 6. Комментарии
-- =========================================

COMMENT ON TABLE road_nodes IS 'Узлы внутреннего дорожного графа по городам';
COMMENT ON TABLE road_edges IS 'Рёбра внутреннего дорожного графа по городам';
COMMENT ON TABLE poi_graph_bindings IS 'Привязка POI к ближайшему узлу графа для быстрого pathfinding';

COMMENT ON COLUMN road_edges.polyline_json IS 'JSON-массив координат ребра для быстрого восстановления polyline';
COMMENT ON COLUMN road_edges.source IS 'Источник графа, например OSM';