ALTER TABLE city_graph_versions
    ADD COLUMN IF NOT EXISTS progress_percent INTEGER DEFAULT 0;

ALTER TABLE city_graph_versions
    ADD COLUMN IF NOT EXISTS progress_message VARCHAR(300);
