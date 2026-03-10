-- ============================================
-- V2__seed_test_review_data.sql
-- Тестовые данные для review-service
-- ============================================

CREATE SCHEMA IF NOT EXISTS review_service;
SET search_path TO review_service;

-- -------------------------------------------------
-- Reviews
-- poi_id:
--   1..5 условно соответствуют тестовым POI
-- user_id:
--   1 = admin
--   2,3,4 = обычные пользователи
-- -------------------------------------------------
INSERT INTO reviews (rating, comment, is_hidden, likes_count, poi_id, user_id, created_at, updated_at)
SELECT 5, 'Отличное место, очень понравилась атмосфера и обслуживание.', FALSE, 0, 1, 2, CURRENT_TIMESTAMP - INTERVAL '10 day', CURRENT_TIMESTAMP - INTERVAL '10 day'
WHERE NOT EXISTS (
    SELECT 1 FROM reviews
    WHERE poi_id = 1 AND user_id = 2
  AND comment = 'Отличное место, очень понравилась атмосфера и обслуживание.'
    );

INSERT INTO reviews (rating, comment, is_hidden, likes_count, poi_id, user_id, created_at, updated_at)
SELECT 4, 'Хорошая кухня, но вечером лучше бронировать заранее.', FALSE, 0, 1, 3, CURRENT_TIMESTAMP - INTERVAL '8 day', CURRENT_TIMESTAMP - INTERVAL '8 day'
WHERE NOT EXISTS (
    SELECT 1 FROM reviews
    WHERE poi_id = 1 AND user_id = 3
  AND comment = 'Хорошая кухня, но вечером лучше бронировать заранее.'
    );

INSERT INTO reviews (rating, comment, is_hidden, likes_count, poi_id, user_id, created_at, updated_at)
SELECT 5, 'Один из лучших музеев, обязательно к посещению.', FALSE, 0, 3, 2, CURRENT_TIMESTAMP - INTERVAL '7 day', CURRENT_TIMESTAMP - INTERVAL '7 day'
WHERE NOT EXISTS (
    SELECT 1 FROM reviews
    WHERE poi_id = 3 AND user_id = 2
  AND comment = 'Один из лучших музеев, обязательно к посещению.'
    );

INSERT INTO reviews (rating, comment, is_hidden, likes_count, poi_id, user_id, created_at, updated_at)
SELECT 4, 'Большой парк, удобно гулять с детьми.', FALSE, 0, 4, 4, CURRENT_TIMESTAMP - INTERVAL '5 day', CURRENT_TIMESTAMP - INTERVAL '5 day'
WHERE NOT EXISTS (
    SELECT 1 FROM reviews
    WHERE poi_id = 4 AND user_id = 4
  AND comment = 'Большой парк, удобно гулять с детьми.'
    );

INSERT INTO reviews (rating, comment, is_hidden, likes_count, poi_id, user_id, created_at, updated_at)
SELECT 3, 'Отель нормальный, но цена немного завышена.', FALSE, 0, 5, 3, CURRENT_TIMESTAMP - INTERVAL '3 day', CURRENT_TIMESTAMP - INTERVAL '3 day'
WHERE NOT EXISTS (
    SELECT 1 FROM reviews
    WHERE poi_id = 5 AND user_id = 3
  AND comment = 'Отель нормальный, но цена немного завышена.'
    );

INSERT INTO reviews (rating, comment, is_hidden, likes_count, poi_id, user_id, created_at, updated_at)
SELECT 1, 'Спам-отзыв для проверки модерации.', TRUE, 0, 2, 4, CURRENT_TIMESTAMP - INTERVAL '2 day', CURRENT_TIMESTAMP - INTERVAL '2 day'
WHERE NOT EXISTS (
    SELECT 1 FROM reviews
    WHERE poi_id = 2 AND user_id = 4
  AND comment = 'Спам-отзыв для проверки модерации.'
    );

-- -------------------------------------------------
-- Review media
-- -------------------------------------------------
INSERT INTO review_media (image_url, review_id, created_at)
SELECT 'https://example.com/reviews/review-1-img-1.jpg', r.id, CURRENT_TIMESTAMP - INTERVAL '10 day'
FROM reviews r
WHERE r.poi_id = 1
  AND r.user_id = 2
  AND r.comment = 'Отличное место, очень понравилась атмосфера и обслуживание.'
  AND NOT EXISTS (
    SELECT 1 FROM review_media rm
    WHERE rm.review_id = r.id
  AND rm.image_url = 'https://example.com/reviews/review-1-img-1.jpg'
    );

INSERT INTO review_media (image_url, review_id, created_at)
SELECT 'https://example.com/reviews/review-3-img-1.jpg', r.id, CURRENT_TIMESTAMP - INTERVAL '7 day'
FROM reviews r
WHERE r.poi_id = 3
  AND r.user_id = 2
  AND r.comment = 'Один из лучших музеев, обязательно к посещению.'
  AND NOT EXISTS (
    SELECT 1 FROM review_media rm
    WHERE rm.review_id = r.id
  AND rm.image_url = 'https://example.com/reviews/review-3-img-1.jpg'
    );

-- -------------------------------------------------
-- Review likes
-- Триггер сам обновит likes_count
-- -------------------------------------------------
INSERT INTO review_likes (user_id, review_id, created_at)
SELECT 1, r.id, CURRENT_TIMESTAMP - INTERVAL '9 day'
FROM reviews r
WHERE r.poi_id = 1
  AND r.user_id = 2
  AND r.comment = 'Отличное место, очень понравилась атмосфера и обслуживание.'
ON CONFLICT (user_id, review_id) DO NOTHING;

INSERT INTO review_likes (user_id, review_id, created_at)
SELECT 4, r.id, CURRENT_TIMESTAMP - INTERVAL '8 day'
FROM reviews r
WHERE r.poi_id = 1
  AND r.user_id = 2
  AND r.comment = 'Отличное место, очень понравилась атмосфера и обслуживание.'
ON CONFLICT (user_id, review_id) DO NOTHING;

INSERT INTO review_likes (user_id, review_id, created_at)
SELECT 1, r.id, CURRENT_TIMESTAMP - INTERVAL '6 day'
FROM reviews r
WHERE r.poi_id = 3
  AND r.user_id = 2
  AND r.comment = 'Один из лучших музеев, обязательно к посещению.'
ON CONFLICT (user_id, review_id) DO NOTHING;

-- -------------------------------------------------
-- Reports
-- -------------------------------------------------
INSERT INTO reports (
    report_type, comment, status, photo_url,
    created_at, handled_at,
    user_id, handled_by_user_id,
    review_id, poi_id, moderator_comment
)
SELECT
    'SPAM',
    'Похоже на рекламный или фейковый отзыв.',
    'approved',
    NULL,
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    CURRENT_TIMESTAMP - INTERVAL '20 hour',
    1,
    1,
    r.id,
    NULL,
    'Скрыто после проверки модератором.'
FROM reviews r
WHERE r.poi_id = 2
  AND r.user_id = 4
  AND r.comment = 'Спам-отзыв для проверки модерации.'
  AND NOT EXISTS (
    SELECT 1 FROM reports rep
    WHERE rep.review_id = r.id
  AND rep.report_type = 'SPAM'
  AND rep.user_id = 1
    );

INSERT INTO reports (
    report_type, comment, status, photo_url,
    created_at, handled_at,
    user_id, handled_by_user_id,
    review_id, poi_id, moderator_comment
)
SELECT
    'WRONG_INFO',
    'У объекта устаревшая информация по времени работы.',
    'pending',
    NULL,
    CURRENT_TIMESTAMP - INTERVAL '12 hour',
    NULL,
    3,
    NULL,
    NULL,
    4,
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM reports rep
    WHERE rep.poi_id = 4
  AND rep.report_type = 'WRONG_INFO'
  AND rep.user_id = 3
    );

INSERT INTO reports (
    report_type, comment, status, photo_url,
    created_at, handled_at,
    user_id, handled_by_user_id,
    review_id, poi_id, moderator_comment
)
SELECT
    'OFFENSIVE',
    'Отзыв содержит грубую лексику.',
    'rejected',
    NULL,
    CURRENT_TIMESTAMP - INTERVAL '6 hour',
    CURRENT_TIMESTAMP - INTERVAL '3 hour',
    2,
    1,
    r.id,
    NULL,
    'Нарушений не обнаружено.'
FROM reviews r
WHERE r.poi_id = 5
  AND r.user_id = 3
  AND r.comment = 'Отель нормальный, но цена немного завышена.'
  AND NOT EXISTS (
    SELECT 1 FROM reports rep
    WHERE rep.review_id = r.id
  AND rep.report_type = 'OFFENSIVE'
  AND rep.user_id = 2
    );