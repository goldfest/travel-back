-- Создание таблицы городов
CREATE TABLE cities (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    country VARCHAR(100),
    description TEXT,
    center_lat DECIMAL(10,8) NOT NULL,
    center_lng DECIMAL(11,8) NOT NULL,
    is_popular BOOLEAN DEFAULT FALSE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    country_code CHAR(2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для оптимизации запросов
CREATE INDEX idx_cities_slug ON cities(slug);
CREATE INDEX idx_cities_is_popular ON cities(is_popular);
CREATE INDEX idx_cities_country_code ON cities(country_code);
CREATE INDEX idx_cities_name_country ON cities(name, country);
CREATE INDEX idx_cities_created_at ON cities(created_at);

-- Комментарии к таблице и колонкам
COMMENT ON TABLE cities IS 'Справочник городов для путешествий';
COMMENT ON COLUMN cities.name IS 'Название города';
COMMENT ON COLUMN cities.country IS 'Отображаемое название страны';
COMMENT ON COLUMN cities.description IS 'Описание города';
COMMENT ON COLUMN cities.center_lat IS 'Широта центра города';
COMMENT ON COLUMN cities.center_lng IS 'Долгота центра города';
COMMENT ON COLUMN cities.is_popular IS 'Признак популярного города для витрины';
COMMENT ON COLUMN cities.slug IS 'URL-идентификатор города';
COMMENT ON COLUMN cities.country_code IS 'ISO 3166-1 alpha-2 код страны';

-- Начальные данные для городов (пример)
INSERT INTO cities (name, country, description, center_lat, center_lng, is_popular, slug, country_code) VALUES
    ('Москва', 'Россия', 'Столица России, крупнейший город страны', 55.755826, 37.6173, true, 'moscow', 'RU'),
    ('Санкт-Петербург', 'Россия', 'Северная столица России, культурная жемчужина', 59.93428, 30.3351, true, 'saint-petersburg', 'RU'),
    ('Сочи', 'Россия', 'Курортный город на Черном море', 43.585525, 39.723062, true, 'sochi', 'RU'),
    ('Казань', 'Россия', 'Столица Республики Татарстан', 55.796127, 49.106414, true, 'kazan', 'RU'),
    ('Екатеринбург', 'Россия', 'Столица Урала', 56.838011, 60.597465, true, 'ekaterinburg', 'RU'),
    ('Новосибирск', 'Россия', 'Столица Сибири', 55.008353, 82.935732, false, 'novosibirsk', 'RU'),
    ('Владивосток', 'Россия', 'Крупнейший порт Дальнего Востока', 43.115536, 131.885485, false, 'vladivostok', 'RU'),
    ('Лондон', 'Великобритания', 'Столица Великобритании', 51.507351, -0.127758, true, 'london', 'GB'),
    ('Париж', 'Франция', 'Столица Франции, город любви', 48.856613, 2.352222, true, 'paris', 'FR'),
    ('Нью-Йорк', 'США', 'Крупнейший город США', 40.712776, -74.005974, true, 'new-york', 'US'),
    ('Токио', 'Япония', 'Столица Японии', 35.676422, 139.650027, true, 'tokyo', 'JP'),
    ('Стамбул', 'Турция', 'Город на двух континентах', 41.008240, 28.978359, true, 'istanbul', 'TR'),
    ('Барселона', 'Испания', 'Столица Каталонии, город Гауди', 41.385064, 2.173404, true, 'barcelona', 'ES'),
    ('Рим', 'Италия', 'Вечный город, столица Италии', 41.902782, 12.496366, true, 'rome', 'IT'),
    ('Прага', 'Чехия', 'Столица Чехии, город сотни шпилей', 50.075538, 14.437800, true, 'prague', 'CZ');