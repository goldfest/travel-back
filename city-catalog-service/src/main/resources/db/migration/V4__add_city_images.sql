ALTER TABLE cities
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);

CREATE TABLE IF NOT EXISTS city_image (
    city_id BIGINT NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL,
    url VARCHAR(500) NOT NULL,
    PRIMARY KEY (city_id, sort_order)
);

UPDATE cities
SET image_url = 'https://images.unsplash.com/photo-1513326738677-b964603b136d?q=80&w=1400&auto=format&fit=crop'
WHERE lower(slug) = 'moscow' OR lower(name) = 'москва';

DELETE FROM city_image
WHERE city_id IN (SELECT id FROM cities WHERE lower(slug) = 'moscow' OR lower(name) = 'москва');

INSERT INTO city_image (city_id, sort_order, url)
SELECT id, 0, 'https://images.unsplash.com/photo-1513326738677-b964603b136d?q=80&w=1400&auto=format&fit=crop'
FROM cities
WHERE lower(slug) = 'moscow' OR lower(name) = 'москва';

INSERT INTO city_image (city_id, sort_order, url)
SELECT id, 1, 'https://images.unsplash.com/photo-1613059093860-582e53c584c3?q=80&w=1200&auto=format&fit=crop'
FROM cities
WHERE lower(slug) = 'moscow' OR lower(name) = 'москва';

INSERT INTO city_image (city_id, sort_order, url)
SELECT id, 2, 'https://images.unsplash.com/photo-1578922746465-3a80a228f223?q=80&w=1400&auto=format&fit=crop'
FROM cities
WHERE lower(slug) = 'moscow' OR lower(name) = 'москва';
