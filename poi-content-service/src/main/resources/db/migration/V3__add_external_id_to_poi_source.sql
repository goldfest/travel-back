ALTER TABLE poi_source
    ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_poi_source_code_external_id
    ON poi_source(source_code, external_id);

CREATE INDEX IF NOT EXISTS idx_poi_source_code_source_url
    ON poi_source(source_code, source_url);