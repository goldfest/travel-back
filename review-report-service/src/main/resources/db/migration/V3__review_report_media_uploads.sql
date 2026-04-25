CREATE SCHEMA IF NOT EXISTS review_service;
SET search_path TO review_service;

-- ===========================================
-- Review moderation status for reviews with uploaded photos
-- ===========================================
ALTER TABLE review_service.reviews
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS moderated_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS moderation_comment VARCHAR(1000);

UPDATE review_service.reviews
SET moderation_status = CASE
    WHEN is_hidden = TRUE THEN 'REJECTED'
    ELSE 'APPROVED'
END
WHERE moderation_status IS NULL;

ALTER TABLE review_service.reviews
    DROP CONSTRAINT IF EXISTS chk_reviews_moderation_status;

ALTER TABLE review_service.reviews
    ADD CONSTRAINT chk_reviews_moderation_status
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'));

CREATE INDEX IF NOT EXISTS idx_reviews_moderation_status
    ON review_service.reviews(moderation_status, created_at);

-- ===========================================
-- Extended review media metadata
-- ===========================================
ALTER TABLE review_service.review_media
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) NOT NULL DEFAULT 'USER_UPLOAD',
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255),
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS file_size BIGINT,
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

ALTER TABLE review_service.review_media
    DROP CONSTRAINT IF EXISTS chk_review_media_moderation_status;

ALTER TABLE review_service.review_media
    ADD CONSTRAINT chk_review_media_moderation_status
        CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE review_service.review_media
    DROP CONSTRAINT IF EXISTS chk_review_media_source_type;

ALTER TABLE review_service.review_media
    ADD CONSTRAINT chk_review_media_source_type
        CHECK (source_type IN ('USER_UPLOAD'));

CREATE INDEX IF NOT EXISTS idx_review_media_status
    ON review_service.review_media(moderation_status, created_at);

-- ===========================================
-- Multiple photos for reports
-- ===========================================
CREATE TABLE IF NOT EXISTS review_service.report_media (
    id BIGSERIAL PRIMARY KEY,
    file_url VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    file_size BIGINT,
    uploaded_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    report_id BIGINT NOT NULL,

    CONSTRAINT fk_report_media_report
        FOREIGN KEY (report_id)
        REFERENCES review_service.reports(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_report_media_report_id
    ON review_service.report_media(report_id);
