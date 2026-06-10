INSERT INTO poi_type (code, name, icon)
VALUES ('toilet', 'Туалет', 'toilet-icon')
ON CONFLICT (code) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS ux_poi_source_code_external_id_not_null
    ON poi_source(source_code, external_id)
    WHERE external_id IS NOT NULL AND external_id <> '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_poi_source_code_source_url_not_null
    ON poi_source(source_code, source_url)
    WHERE source_url IS NOT NULL AND source_url <> '';
