ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS event_key VARCHAR(120),
    ADD COLUMN IF NOT EXISTS route_day_id BIGINT,
    ADD COLUMN IF NOT EXISTS delivery_channel VARCHAR(20) DEFAULT 'IN_APP',
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS ux_notifications_event_key
    ON notifications(event_key)
    WHERE event_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notifications_route_day_id
    ON notifications(route_day_id);

CREATE INDEX IF NOT EXISTS idx_notifications_status
    ON notifications(status);
