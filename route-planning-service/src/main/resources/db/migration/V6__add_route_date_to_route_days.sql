ALTER TABLE route_days
    ADD COLUMN IF NOT EXISTS route_date DATE;

UPDATE route_days
SET route_date = COALESCE(route_date, planned_start::date, planned_end::date);
