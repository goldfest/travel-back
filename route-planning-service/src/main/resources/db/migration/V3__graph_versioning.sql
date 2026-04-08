CREATE TABLE IF NOT EXISTS city_graph_versions (
    id BIGSERIAL PRIMARY KEY,
    city_id BIGINT NOT NULL,
    version_no INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'FAILED')),
    source VARCHAR(30) NOT NULL DEFAULT 'OSM',
    bbox_min_lat DOUBLE PRECISION,
    bbox_min_lng DOUBLE PRECISION,
    bbox_max_lat DOUBLE PRECISION,
    bbox_max_lng DOUBLE PRECISION,
    imported_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_city_graph_versions_city_version UNIQUE (city_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_city_graph_versions_city_id ON city_graph_versions(city_id);
CREATE INDEX IF NOT EXISTS idx_city_graph_versions_status ON city_graph_versions(status);

ALTER TABLE road_nodes ADD COLUMN IF NOT EXISTS graph_version_id BIGINT;
ALTER TABLE road_edges ADD COLUMN IF NOT EXISTS graph_version_id BIGINT;
ALTER TABLE poi_graph_bindings ADD COLUMN IF NOT EXISTS graph_version_id BIGINT;
ALTER TABLE route_day_paths ADD COLUMN IF NOT EXISTS graph_version_id BIGINT;
ALTER TABLE route_segment_paths ADD COLUMN IF NOT EXISTS graph_version_id BIGINT;

ALTER TABLE road_nodes
    ADD CONSTRAINT fk_road_nodes_graph_version
    FOREIGN KEY (graph_version_id) REFERENCES city_graph_versions(id) ON DELETE CASCADE;
ALTER TABLE road_edges
    ADD CONSTRAINT fk_road_edges_graph_version
    FOREIGN KEY (graph_version_id) REFERENCES city_graph_versions(id) ON DELETE CASCADE;
ALTER TABLE poi_graph_bindings
    ADD CONSTRAINT fk_poi_graph_bindings_graph_version
    FOREIGN KEY (graph_version_id) REFERENCES city_graph_versions(id) ON DELETE CASCADE;
ALTER TABLE route_day_paths
    ADD CONSTRAINT fk_route_day_paths_graph_version
    FOREIGN KEY (graph_version_id) REFERENCES city_graph_versions(id) ON DELETE SET NULL;
ALTER TABLE route_segment_paths
    ADD CONSTRAINT fk_route_segment_paths_graph_version
    FOREIGN KEY (graph_version_id) REFERENCES city_graph_versions(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_road_nodes_graph_version ON road_nodes(graph_version_id);
CREATE INDEX IF NOT EXISTS idx_road_edges_graph_version ON road_edges(graph_version_id);
CREATE INDEX IF NOT EXISTS idx_poi_graph_bindings_graph_version ON poi_graph_bindings(graph_version_id);
CREATE INDEX IF NOT EXISTS idx_route_day_paths_graph_version ON route_day_paths(graph_version_id);
CREATE INDEX IF NOT EXISTS idx_route_segment_paths_graph_version ON route_segment_paths(graph_version_id);

UPDATE city_graph_versions cgv
SET imported_at = CURRENT_TIMESTAMP
WHERE imported_at IS NULL;

UPDATE road_nodes rn
SET graph_version_id = (
    SELECT id FROM city_graph_versions cgv
    WHERE cgv.city_id = rn.city_id AND cgv.status = 'ACTIVE'
    ORDER BY cgv.version_no DESC LIMIT 1
)
WHERE rn.graph_version_id IS NULL;

UPDATE road_edges re
SET graph_version_id = (
    SELECT id FROM city_graph_versions cgv
    WHERE cgv.city_id = re.city_id AND cgv.status = 'ACTIVE'
    ORDER BY cgv.version_no DESC LIMIT 1
)
WHERE re.graph_version_id IS NULL;

UPDATE poi_graph_bindings pgb
SET graph_version_id = (
    SELECT id FROM city_graph_versions cgv
    WHERE cgv.city_id = pgb.city_id AND cgv.status = 'ACTIVE'
    ORDER BY cgv.version_no DESC LIMIT 1
)
WHERE pgb.graph_version_id IS NULL;

DROP TRIGGER IF EXISTS update_city_graph_versions_updated_at ON city_graph_versions;
CREATE TRIGGER update_city_graph_versions_updated_at
    BEFORE UPDATE ON city_graph_versions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
