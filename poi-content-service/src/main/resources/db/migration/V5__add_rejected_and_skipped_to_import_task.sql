ALTER TABLE data_import_task
    ADD COLUMN IF NOT EXISTS total_poi_rejected INTEGER DEFAULT 0;

ALTER TABLE data_import_task
    ADD COLUMN IF NOT EXISTS total_poi_skipped INTEGER DEFAULT 0;