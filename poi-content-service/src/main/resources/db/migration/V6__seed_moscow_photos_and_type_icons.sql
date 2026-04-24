ALTER TABLE poi_type
ALTER COLUMN icon TYPE VARCHAR(500);

UPDATE poi_type SET icon = CASE code
                               WHEN 'restaurant' THEN 'https://cdn-icons-png.flaticon.com/512/3075/3075977.png'
                               WHEN 'cafe' THEN 'https://cdn-icons-png.flaticon.com/512/2935/2935413.png'
                               WHEN 'museum' THEN 'https://cdn-icons-png.flaticon.com/512/3936/3936786.png'
                               WHEN 'park' THEN 'https://cdn-icons-png.flaticon.com/512/489/489969.png'
                               WHEN 'hotel' THEN 'https://cdn-icons-png.flaticon.com/512/3009/3009489.png'
                               WHEN 'shop' THEN 'https://cdn-icons-png.flaticon.com/512/3081/3081559.png'
                               WHEN 'atm' THEN 'https://cdn-icons-png.flaticon.com/512/2830/2830284.png'
                               WHEN 'pharmacy' THEN 'https://cdn-icons-png.flaticon.com/512/2966/2966327.png'
                               WHEN 'hospital' THEN 'https://cdn-icons-png.flaticon.com/512/2966/2966486.png'
                               WHEN 'school' THEN 'https://cdn-icons-png.flaticon.com/512/2602/2602414.png'
                               WHEN 'bar' THEN 'https://cdn-icons-png.flaticon.com/512/920/920582.png'
                               WHEN 'theater' THEN 'https://cdn-icons-png.flaticon.com/512/3936/3936729.png'
                               WHEN 'monument' THEN 'https://cdn-icons-png.flaticon.com/512/3448/3448339.png'
                               WHEN 'mall' THEN 'https://cdn-icons-png.flaticon.com/512/3082/3082060.png'
                               WHEN 'landmark' THEN 'https://cdn-icons-png.flaticon.com/512/3448/3448339.png'
                               ELSE COALESCE(icon, 'https://cdn-icons-png.flaticon.com/512/684/684908.png')
    END;

DELETE FROM poi_media
WHERE poi_id IN (SELECT id FROM poi WHERE city_id = 1)
  AND user_id = 1
  AND url LIKE 'https://images.unsplash.com/%';

INSERT INTO poi_media (poi_id, url, media_type, moderation_status, user_id)
SELECT id, 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?q=80&w=1200&auto=format&fit=crop', 'PHOTO', 'APPROVED', 1
FROM poi WHERE slug = 'restaurant-pushkin-moscow';

INSERT INTO poi_media (poi_id, url, media_type, moderation_status, user_id)
SELECT id, 'https://images.unsplash.com/photo-1520106212299-d99c443e4568?q=80&w=1200&auto=format&fit=crop', 'PHOTO', 'APPROVED', 1
FROM poi WHERE slug = 'gorky-park-moscow';

INSERT INTO poi_media (poi_id, url, media_type, moderation_status, user_id)
SELECT id, 'https://images.unsplash.com/photo-1513326738677-b964603b136d?q=80&w=1200&auto=format&fit=crop', 'PHOTO', 'APPROVED', 1
FROM poi
WHERE city_id = 1
  AND slug NOT IN ('restaurant-pushkin-moscow', 'gorky-park-moscow')
  AND NOT EXISTS (SELECT 1 FROM poi_media m WHERE m.poi_id = poi.id);
