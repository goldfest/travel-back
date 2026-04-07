DO $$
DECLARE
    status_check_name text;
BEGIN
    SELECT c.conname
    INTO status_check_name
    FROM pg_constraint c
             JOIN pg_class t ON t.oid = c.conrelid
    WHERE t.relname = 'routes'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) ILIKE '%status%';

    IF status_check_name IS NOT NULL THEN
        EXECUTE 'ALTER TABLE routes DROP CONSTRAINT ' || quote_ident(status_check_name);
    END IF;
END $$;

ALTER TABLE routes
    ADD CONSTRAINT routes_status_check
        CHECK (status IN ('DRAFT', 'GRAPH_PREPARING', 'READY', 'ARCHIVED'));
