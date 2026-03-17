-- ============================================
-- V2__seed_test_poi_data.sql
-- Тестовые данные для poi-content-service
-- ============================================

-- -------------------------------------------------
-- Дополнительные типы POI (если нужны)
-- -------------------------------------------------
INSERT INTO poi_type (code, name, icon)
VALUES
    ('bar',        'Бар',         'bar-icon'),
    ('theater',    'Театр',       'theater-icon'),
    ('monument',   'Памятник',    'monument-icon'),
    ('mall',       'Торговый центр', 'mall-icon')
    ON CONFLICT (code) DO NOTHING;

-- -------------------------------------------------
-- Тестовые POI
-- created_by:
--   1 = admin
--   2 = user
-- city_id:
--   1 = Москва
--   2 = Санкт-Петербург
-- -------------------------------------------------
INSERT INTO poi (
    name, slug, tags, description, address,
    latitude, longitude,
    phone, site_url,
    price_level,
    is_verified, is_closed,
    created_by, city_id, poi_type_id
)
SELECT
    'Ресторан Пушкин',
    'restaurant-pushkin-moscow',
    '["ресторан","русская кухня","центр"]'::jsonb,
    'Известный ресторан русской кухни в центре Москвы.',
    'Москва, Тверской бульвар, 26А',
    55.764950, 37.604420,
    '+7-495-000-00-01',
    'https://example.com/pushkin',
    4,
    TRUE, FALSE,
    1, 1,
    pt.id
FROM poi_type pt
WHERE pt.code = 'restaurant'
  AND NOT EXISTS (
    SELECT 1 FROM poi WHERE slug = 'restaurant-pushkin-moscow'
);

INSERT INTO poi (
    name, slug, tags, description, address,
    latitude, longitude,
    phone, site_url,
    price_level,
    is_verified, is_closed,
    created_by, city_id, poi_type_id
)
SELECT
    'Московская педагогическая дрочильня',
    'surf-coffee-arbat',
    '["кофе","кафе","десерты"]'::jsonb,
    'Популярная кофейня с авторскими напитками.',
    'Москва, Арбат, 12',
    55.749550, 37.591000,
    '+7-495-000-00-02',
    'https://example.com/surf-arbat',
    2,
    TRUE, FALSE,
    2, 1,
    pt.id
FROM poi_type pt
WHERE pt.code = 'cafe'
  AND NOT EXISTS (
    SELECT 1 FROM poi WHERE slug = 'surf-coffee-arbat'
);

INSERT INTO poi (
    name, slug, tags, description, address,
    latitude, longitude,
    phone, site_url,
    price_level,
    is_verified, is_closed,
    created_by, city_id, poi_type_id
)
SELECT
    'Государственная дрочильня',
    'hermitage-museum',
    '["музей","искусство","достопримечательность"]'::jsonb,
    'Один из крупнейших художественных музеев мира.',
    'Санкт-Петербург, Дворцовая площадь, 2',
    59.939832, 30.314560,
    '+7-812-000-00-03',
    'https://example.com/hermitage',
    3,
    TRUE, FALSE,
    1, 2,
    pt.id
FROM poi_type pt
WHERE pt.code = 'museum'
  AND NOT EXISTS (
    SELECT 1 FROM poi WHERE slug = 'hermitage-museum'
);

INSERT INTO poi (
    name, slug, tags, description, address,
    latitude, longitude,
    phone, site_url,
    price_level,
    is_verified, is_closed,
    created_by, city_id, poi_type_id
)
SELECT
    'Дрочильня "ручки алины парадокси"',
    'gorky-park-moscow',
    '["парк","прогулки","отдых"]'::jsonb,
    'Большой городской парк для прогулок и активного отдыха.',
    'Москва, Крымский Вал, 9',
    55.729900, 37.603400,
    '+7-495-000-00-04',
    'https://example.com/gorky-park',
    0,
    TRUE, FALSE,
    1, 1,
    pt.id
FROM poi_type pt
WHERE pt.code = 'park'
  AND NOT EXISTS (
    SELECT 1 FROM poi WHERE slug = 'gorky-park-moscow'
);

INSERT INTO poi (
    name, slug, tags, description, address,
    latitude, longitude,
    phone, site_url,
    price_level,
    is_verified, is_closed,
    created_by, city_id, poi_type_id
)
SELECT
    'Дрочильня "У тимура',
    'azimut-hotel-smolenskaya',
    '["отель","центр","проживание"]'::jsonb,
    'Современный городской отель рядом со Смоленской.',
    'Москва, Смоленская, 8',
    55.747780, 37.583500,
    '+7-495-000-00-05',
    'https://example.com/azimut',
    4,
    TRUE, FALSE,
    2, 1,
    pt.id
FROM poi_type pt
WHERE pt.code = 'hotel'
  AND NOT EXISTS (
    SELECT 1 FROM poi WHERE slug = 'azimut-hotel-smolenskaya'
);

-- -------------------------------------------------
-- Features
-- -------------------------------------------------
INSERT INTO poi_feature (poi_id, key, value)
SELECT p.id, 'wifi', 'true'
FROM poi p
WHERE p.slug = 'restaurant-pushkin-moscow'
  AND NOT EXISTS (
    SELECT 1 FROM poi_feature f
    WHERE f.poi_id = p.id AND f.key = 'wifi'
);

INSERT INTO poi_feature (poi_id, key, value)
SELECT p.id, 'parking', 'false'
FROM poi p
WHERE p.slug = 'restaurant-pushkin-moscow'
  AND NOT EXISTS (
    SELECT 1 FROM poi_feature f
    WHERE f.poi_id = p.id AND f.key = 'parking'
);

INSERT INTO poi_feature (poi_id, key, value)
SELECT p.id, 'takeaway', 'true'
FROM poi p
WHERE p.slug = 'surf-coffee-arbat'
  AND NOT EXISTS (
    SELECT 1 FROM poi_feature f
    WHERE f.poi_id = p.id AND f.key = 'takeaway'
);

INSERT INTO poi_feature (poi_id, key, value)
SELECT p.id, 'family_friendly', 'true'
FROM poi p
WHERE p.slug = 'gorky-park-moscow'
  AND NOT EXISTS (
    SELECT 1 FROM poi_feature f
    WHERE f.poi_id = p.id AND f.key = 'family_friendly'
);

INSERT INTO poi_feature (poi_id, key, value)
SELECT p.id, 'audio_guide', 'true'
FROM poi p
WHERE p.slug = 'hermitage-museum'
  AND NOT EXISTS (
    SELECT 1 FROM poi_feature f
    WHERE f.poi_id = p.id AND f.key = 'audio_guide'
);

-- -------------------------------------------------
-- Hours
-- day_of_week: 0=Sunday ... 6=Saturday
-- -------------------------------------------------
INSERT INTO poi_hours (poi_id, day_of_week, open_time, close_time, around_the_clock)
SELECT p.id, d.day_of_week, d.open_time, d.close_time, FALSE
FROM poi p
         CROSS JOIN (
    VALUES
        (1, TIME '10:00', TIME '22:00'),
        (2, TIME '10:00', TIME '22:00'),
        (3, TIME '10:00', TIME '22:00'),
        (4, TIME '10:00', TIME '23:00'),
        (5, TIME '10:00', TIME '23:00'),
        (6, TIME '10:00', TIME '23:00'),
        (0, TIME '10:00', TIME '21:00')
) AS d(day_of_week, open_time, close_time)
WHERE p.slug = 'restaurant-pushkin-moscow'
  AND NOT EXISTS (
    SELECT 1 FROM poi_hours h
    WHERE h.poi_id = p.id AND h.day_of_week = d.day_of_week
);

INSERT INTO poi_hours (poi_id, day_of_week, open_time, close_time, around_the_clock)
SELECT p.id, d.day_of_week, d.open_time, d.close_time, FALSE
FROM poi p
         CROSS JOIN (
    VALUES
        (1, TIME '08:00', TIME '22:00'),
        (2, TIME '08:00', TIME '22:00'),
        (3, TIME '08:00', TIME '22:00'),
        (4, TIME '08:00', TIME '22:00'),
        (5, TIME '08:00', TIME '23:00'),
        (6, TIME '09:00', TIME '23:00'),
        (0, TIME '09:00', TIME '21:00')
) AS d(day_of_week, open_time, close_time)
WHERE p.slug = 'surf-coffee-arbat'
  AND NOT EXISTS (
    SELECT 1 FROM poi_hours h
    WHERE h.poi_id = p.id AND h.day_of_week = d.day_of_week
);

INSERT INTO poi_hours (poi_id, day_of_week, open_time, close_time, around_the_clock)
SELECT p.id, d.day_of_week, d.open_time, d.close_time, FALSE
FROM poi p
         CROSS JOIN (
    VALUES
        (2, TIME '11:00', TIME '20:00'),
        (3, TIME '11:00', TIME '20:00'),
        (4, TIME '11:00', TIME '20:00'),
        (5, TIME '11:00', TIME '20:00'),
        (6, TIME '11:00', TIME '20:00'),
        (0, TIME '11:00', TIME '20:00')
) AS d(day_of_week, open_time, close_time)
WHERE p.slug = 'hermitage-museum'
  AND NOT EXISTS (
    SELECT 1 FROM poi_hours h
    WHERE h.poi_id = p.id AND h.day_of_week = d.day_of_week
);

INSERT INTO poi_hours (poi_id, day_of_week, open_time, close_time, around_the_clock)
SELECT p.id, d.day_of_week, NULL, NULL, TRUE
FROM poi p
         CROSS JOIN (
    VALUES (0), (1), (2), (3), (4), (5), (6)
) AS d(day_of_week)
WHERE p.slug = 'azimut-hotel-smolenskaya'
  AND NOT EXISTS (
    SELECT 1 FROM poi_hours h
    WHERE h.poi_id = p.id AND h.day_of_week = d.day_of_week
);

-- -------------------------------------------------
-- Media
-- -------------------------------------------------
INSERT INTO poi_media (poi_id, url, media_type, moderation_status, user_id)
SELECT p.id, 'https://example.com/media/pushkin-1.jpg', 'IMAGE', 'APPROVED', 1
FROM poi p
WHERE p.slug = 'restaurant-pushkin-moscow'
  AND NOT EXISTS (
    SELECT 1 FROM poi_media m
    WHERE m.poi_id = p.id AND m.url = 'https://example.com/media/pushkin-1.jpg'
);

INSERT INTO poi_media (poi_id, url, media_type, moderation_status, user_id)
SELECT p.id, 'https://example.com/media/hermitage-1.jpg', 'IMAGE', 'APPROVED', 1
FROM poi p
WHERE p.slug = 'hermitage-museum'
  AND NOT EXISTS (
    SELECT 1 FROM poi_media m
    WHERE m.poi_id = p.id AND m.url = 'https://example.com/media/hermitage-1.jpg'
);

INSERT INTO poi_media (poi_id, url, media_type, moderation_status, user_id)
SELECT p.id, 'https://example.com/media/gorky-park-1.jpg', 'IMAGE', 'APPROVED', 2
FROM poi p
WHERE p.slug = 'gorky-park-moscow'
  AND NOT EXISTS (
    SELECT 1 FROM poi_media m
    WHERE m.poi_id = p.id AND m.url = 'https://example.com/media/gorky-park-1.jpg'
);

-- -------------------------------------------------
-- Sources
-- -------------------------------------------------
INSERT INTO poi_source (poi_id, source_code, source_url, confidence_score)
SELECT p.id, 'MANUAL', 'https://admin.travelapp.local/poi/' || p.id, 1.00
FROM poi p
WHERE p.slug IN (
                 'restaurant-pushkin-moscow',
                 'surf-coffee-arbat',
                 'hermitage-museum',
                 'gorky-park-moscow',
                 'azimut-hotel-smolenskaya'
    )
  AND NOT EXISTS (
    SELECT 1 FROM poi_source s
    WHERE s.poi_id = p.id AND s.source_code = 'MANUAL'
);

-- -------------------------------------------------
-- Import tasks
-- -------------------------------------------------
INSERT INTO data_import_task (
    source_code, query, status,
    started_at, finished_at,
    total_poi_found, total_poi_created, total_poi_updated,
    error_message, city_id
)
SELECT
    'OSM',
    'Moscow center restaurants',
    'SUCCESS',
    CURRENT_TIMESTAMP - INTERVAL '2 day',
    CURRENT_TIMESTAMP - INTERVAL '2 day' + INTERVAL '10 minute',
    25, 10, 5,
    NULL,
    1
WHERE NOT EXISTS (
    SELECT 1
    FROM data_import_task t
    WHERE t.source_code = 'OSM' AND t.query = 'Moscow center restaurants'
    );

INSERT INTO data_import_task (
    source_code, query, status,
    started_at, finished_at,
    total_poi_found, total_poi_created, total_poi_updated,
    error_message, city_id
)
SELECT
    '2GIS',
    'Saint Petersburg museums',
    'SUCCESS',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    CURRENT_TIMESTAMP - INTERVAL '1 day' + INTERVAL '7 minute',
    18, 7, 3,
    NULL,
    2
WHERE NOT EXISTS (
    SELECT 1
    FROM data_import_task t
    WHERE t.source_code = '2GIS' AND t.query = 'Saint Petersburg museums'
    );