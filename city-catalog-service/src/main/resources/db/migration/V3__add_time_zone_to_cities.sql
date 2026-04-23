ALTER TABLE cities
ADD COLUMN time_zone VARCHAR(64);

COMMENT ON COLUMN cities.time_zone IS 'Таймзона города в формате IANA, например Europe/Moscow';

UPDATE cities SET time_zone = 'Europe/Moscow' WHERE slug IN ('moscow', 'saint-petersburg', 'sochi', 'kazan', 'nizhny-novgorod', 'krasnodar', 'rostov-on-don');
UPDATE cities SET time_zone = 'Asia/Yekaterinburg' WHERE slug IN ('ekaterinburg', 'tyumen');
UPDATE cities SET time_zone = 'Asia/Novosibirsk' WHERE slug = 'novosibirsk';
UPDATE cities SET time_zone = 'Asia/Vladivostok' WHERE slug = 'vladivostok';
UPDATE cities SET time_zone = 'Europe/Samara' WHERE slug = 'samara';
UPDATE cities SET time_zone = 'Europe/Ulyanovsk' WHERE slug = 'ulyanovsk';
UPDATE cities SET time_zone = 'Europe/Kaliningrad' WHERE slug = 'kaliningrad';
UPDATE cities SET time_zone = 'Asia/Irkutsk' WHERE slug = 'irkutsk';