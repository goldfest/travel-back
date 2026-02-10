-- Review & Reports Service Database Schema
-- Schema: review_service

CREATE SCHEMA IF NOT EXISTS review_service;

SET search_path TO review_service;

-- ===========================================
-- REVIEW ENTITY
-- ===========================================
CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    rating SMALLINT NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment VARCHAR(1000),
    is_hidden BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    likes_count INTEGER DEFAULT 0,
    poi_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    CONSTRAINT fk_review_poi FOREIGN KEY (poi_id) REFERENCES poi_service.pois(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES auth_service.users(id) ON DELETE CASCADE
);

-- Индексы для Reviews
CREATE INDEX idx_reviews_poi_id ON reviews(poi_id);
CREATE INDEX idx_reviews_user_id ON reviews(user_id);
CREATE INDEX idx_reviews_created_at ON reviews(created_at DESC);
CREATE INDEX idx_reviews_rating ON reviews(rating);

-- ===========================================
-- REVIEW MEDIA ENTITY
-- ===========================================
CREATE TABLE review_media (
    id BIGSERIAL PRIMARY KEY,
    image_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    review_id BIGINT NOT NULL,

    CONSTRAINT fk_review_media_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE
);

CREATE INDEX idx_review_media_review_id ON review_media(review_id);

-- ===========================================
-- REPORTS ENTITY
-- ===========================================
CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(32) NOT NULL,
    comment VARCHAR(1000),
    status VARCHAR(16) DEFAULT 'pending',
    photo_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMP,
    user_id BIGINT NOT NULL,
    handled_by_user_id BIGINT,
    review_id BIGINT,
    poi_id BIGINT,

    CONSTRAINT fk_report_user FOREIGN KEY (user_id) REFERENCES auth_service.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_handler FOREIGN KEY (handled_by_user_id) REFERENCES auth_service.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_report_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_poi FOREIGN KEY (poi_id) REFERENCES poi_service.pois(id) ON DELETE CASCADE,

    -- Проверка, что жалоба относится либо к отзыву, либо к объекту
    CONSTRAINT chk_report_target CHECK (
        (review_id IS NOT NULL AND poi_id IS NULL) OR
        (review_id IS NULL AND poi_id IS NOT NULL)
    ),

    -- Проверка статусов
    CONSTRAINT chk_report_status CHECK (status IN ('pending', 'approved', 'rejected'))
);

-- Индексы для Reports
CREATE INDEX idx_reports_user_id ON reports(user_id);
CREATE INDEX idx_reports_status ON reports(status);
CREATE INDEX idx_reports_created_at ON reports(created_at DESC);
CREATE INDEX idx_reports_review_id ON reports(review_id) WHERE review_id IS NOT NULL;
CREATE INDEX idx_reports_poi_id ON reports(poi_id) WHERE poi_id IS NOT NULL;

-- ===========================================
-- REVIEW LIKE TRACKING (для подсчета лайков)
-- ===========================================
CREATE TABLE review_likes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    review_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_review_like_user FOREIGN KEY (user_id) REFERENCES auth_service.users(id) ON DELETE CASCADE,
    CONSTRAINT fk_review_like_review FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_review_like UNIQUE (user_id, review_id)
);

CREATE INDEX idx_review_likes_review_id ON review_likes(review_id);

-- ===========================================
-- TRIGGERS для обновления updated_at
-- ===========================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_reviews_updated_at
    BEFORE UPDATE ON reviews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ===========================================
-- VIEWS для агрегированных данных
-- ===========================================
CREATE VIEW poi_review_stats AS
SELECT
    poi_id,
    COUNT(*) as total_reviews,
    AVG(rating) as average_rating,
    SUM(CASE WHEN is_hidden = FALSE THEN 1 ELSE 0 END) as visible_reviews,
    MAX(created_at) as last_review_date
FROM reviews
WHERE is_hidden = FALSE
GROUP BY poi_id;

-- ===========================================
-- FUNCTIONS
-- ===========================================

-- Функция для подсчета лайков отзыва
CREATE OR REPLACE FUNCTION update_review_likes_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE reviews
        SET likes_count = likes_count + 1
        WHERE id = NEW.review_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE reviews
        SET likes_count = likes_count - 1
        WHERE id = OLD.review_id;
    END IF;
    RETURN NULL;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_review_likes_trigger
    AFTER INSERT OR DELETE ON review_likes
    FOR EACH ROW EXECUTE FUNCTION update_review_likes_count();
