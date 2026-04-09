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
('Москва', 'Россия', 'Столица России, крупнейший город страны', 55.755826, 37.617300, true, 'moscow', 'RU'),
('Санкт-Петербург', 'Россия', 'Северная столица России, культурная жемчужина', 59.934280, 30.335099, true, 'saint-petersburg', 'RU'),
('Сочи', 'Россия', 'Курортный город на Черном море', 43.585525, 39.723062, true, 'sochi', 'RU'),
('Казань', 'Россия', 'Столица Республики Татарстан', 55.796127, 49.106414, true, 'kazan', 'RU'),
('Екатеринбург', 'Россия', 'Столица Урала', 56.838011, 60.597465, true, 'ekaterinburg', 'RU'),
('Новосибирск', 'Россия', 'Столица Сибири', 55.008353, 82.935733, false, 'novosibirsk', 'RU'),
('Владивосток', 'Россия', 'Крупнейший порт Дальнего Востока', 43.115536, 131.885485, false, 'vladivostok', 'RU'),
('Нижний Новгород', 'Россия', 'Крупный город на слиянии Оки и Волги', 56.326887, 44.005986, true, 'nizhny-novgorod', 'RU'),
('Самара', 'Россия', 'Крупный город Поволжья на берегу Волги', 53.195878, 50.100202, false, 'samara', 'RU'),
('Ульяновск', 'Россия', 'Город на Волге, административный центр Ульяновской области', 54.314192, 48.403132, false, 'ulyanovsk', 'RU'),
('Калининград', 'Россия', 'Город на Балтийском побережье', 54.710426, 20.452214, false, 'kaliningrad', 'RU'),
('Краснодар', 'Россия', 'Крупный город юга России', 45.035470, 38.975313, false, 'krasnodar', 'RU'),
('Ростов-на-Дону', 'Россия', 'Крупный город юга России, ворота Кавказа', 47.235713, 39.701505, false, 'rostov-on-don', 'RU'),
('Тюмень', 'Россия', 'Один из старейших городов Сибири', 57.152985, 65.541227, false, 'tyumen', 'RU'),
('Иркутск', 'Россия', 'Крупный город Восточной Сибири рядом с Байкалом', 52.286974, 104.305018, false, 'irkutsk', 'RU');
