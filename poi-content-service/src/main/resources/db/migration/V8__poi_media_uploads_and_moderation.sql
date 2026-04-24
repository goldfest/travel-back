ALTER TABLE poi_media
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'ADMIN_UPLOAD',
    ADD COLUMN IF NOT EXISTS display_order INTEGER,
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size BIGINT,
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS moderated_by BIGINT,
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP;

-- Убираем тестовые и случайные fake-фото, потому что реальные фото теперь загружаются админом/пользователями
-- или подтягиваются из разрешённых источников вроде Wikimedia.
DELETE FROM poi_media
WHERE url LIKE 'https://images.unsplash.com/%'
   OR url LIKE 'https://example.com/media/%';

UPDATE poi_media
SET source_type = 'SYSTEM_WIKIMEDIA'
WHERE url ILIKE '%wikimedia.org%'
   OR url ILIKE '%wikipedia.org%';

UPDATE poi_media
SET moderated_at = COALESCE(moderated_at, created_at),
    moderated_by = COALESCE(moderated_by, user_id)
WHERE moderation_status = 'APPROVED';

CREATE INDEX IF NOT EXISTS idx_poi_media_status ON poi_media(moderation_status);
CREATE INDEX IF NOT EXISTS idx_poi_media_source_type ON poi_media(source_type);
CREATE INDEX IF NOT EXISTS idx_poi_media_display ON poi_media(poi_id, moderation_status, source_type, display_order, created_at);
