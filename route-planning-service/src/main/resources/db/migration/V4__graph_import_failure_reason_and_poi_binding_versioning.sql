ALTER TABLE city_graph_versions
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(1000);

ALTER TABLE route_segment_paths
    ADD COLUMN IF NOT EXISTS diagnostic_code VARCHAR(64);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'poi_graph_bindings'
          AND column_name = 'poi_id'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'poi_graph_bindings'
          AND column_name = 'id'
    ) THEN
ALTER TABLE poi_graph_bindings ADD COLUMN id BIGSERIAL;
ALTER TABLE poi_graph_bindings DROP CONSTRAINT IF EXISTS poi_graph_bindings_pkey;
ALTER TABLE poi_graph_bindings ADD CONSTRAINT poi_graph_bindings_pkey PRIMARY KEY (id);
ALTER TABLE poi_graph_bindings ALTER COLUMN poi_id SET NOT NULL;
ALTER TABLE poi_graph_bindings ADD CONSTRAINT uq_poi_graph_bindings_poi_version UNIQUE (poi_id, graph_version_id);
END IF;
END $$;
