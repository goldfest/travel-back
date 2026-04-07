-- notification-service/src/main/resources/db/migration/V1__init_notification_schema.sql

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(24) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(500),
    scheduled_at TIMESTAMP,
    is_read BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMP,
    read_at TIMESTAMP,
    route_id BIGINT,
    poi_id BIGINT,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для оптимизации запросов
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);
CREATE INDEX idx_notifications_scheduled_at ON notifications(scheduled_at);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);

-- Индекс для уведомлений по маршрутам
CREATE INDEX idx_notifications_route_id ON notifications(route_id);

-- Индекс для уведомлений по объектам
CREATE INDEX idx_notifications_poi_id ON notifications(poi_id);

-- Комментарии к таблице и полям
COMMENT ON TABLE notifications IS 'Хранит уведомления пользователей: напоминания, события маршрутов, результаты модерации';

COMMENT ON COLUMN notifications.type IS 'Тип уведомления: review, poi_update, route_reminder, moderation';
COMMENT ON COLUMN notifications.title IS 'Заголовок уведомления (до 160 символов)';
COMMENT ON COLUMN notifications.description IS 'Текстовое содержание уведомления';
COMMENT ON COLUMN notifications.scheduled_at IS 'Время планируемой отправки (для напоминаний)';
COMMENT ON COLUMN notifications.is_read IS 'Прочтено ли уведомление';
COMMENT ON COLUMN notifications.sent_at IS 'Время фактической отправки';
COMMENT ON COLUMN notifications.read_at IS 'Время прочтения пользователем';
COMMENT ON COLUMN notifications.route_id IS 'Ссылка на маршрут, если уведомление связано с ним';
COMMENT ON COLUMN notifications.poi_id IS 'Ссылка на объект, если уведомление связано с ним';
COMMENT ON COLUMN notifications.user_id IS 'Пользователь, которому адресовано уведомление';