-- Создание таблицы PresetFilter (Пресеты фильтров)
CREATE TABLE preset_filters (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    filters_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL,
    city_id BIGINT NOT NULL,
    poi_type_id BIGINT NOT NULL,

    CONSTRAINT fk_preset_filter_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_preset_filter_city FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    CONSTRAINT fk_preset_filter_poi_type FOREIGN KEY (poi_type_id) REFERENCES poi_types(id) ON DELETE CASCADE
);

-- Индекс для быстрого поиска пресетов пользователя
CREATE INDEX idx_preset_filters_user_id ON preset_filters(user_id);
CREATE INDEX idx_preset_filters_city_type ON preset_filters(city_id, poi_type_id);

-- Создание таблицы SearchHistory (История поиска)
CREATE TABLE search_history (
    id BIGSERIAL PRIMARY KEY,
    query_text VARCHAR(255) NOT NULL,
    searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    filters_json JSONB,
    user_id BIGINT NOT NULL,
    city_id BIGINT NOT NULL,
    preset_filter_id BIGINT,

    CONSTRAINT fk_search_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_search_history_city FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    CONSTRAINT fk_search_history_preset FOREIGN KEY (preset_filter_id) REFERENCES preset_filters(id) ON DELETE SET NULL
);

-- Индексы для истории поиска
CREATE INDEX idx_search_history_user ON search_history(user_id);
CREATE INDEX idx_search_history_searched_at ON search_history(searched_at DESC);
CREATE INDEX idx_search_history_city_user ON search_history(city_id, user_id);

-- Создание таблицы Favorite (Избранное)
CREATE TABLE favorites (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL,
    poi_id BIGINT NOT NULL,

    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_poi FOREIGN KEY (poi_id) REFERENCES pois(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_poi_favorite UNIQUE (user_id, poi_id)
);

-- Индекс для быстрого получения избранного пользователя
CREATE INDEX idx_favorites_user ON favorites(user_id);
CREATE INDEX idx_favorites_created_at ON favorites(created_at DESC);

-- Создание таблицы Collection (Коллекции)
CREATE TABLE collections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    cover_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL,

    CONSTRAINT fk_collection_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Индекс для коллекций пользователя
CREATE INDEX idx_collections_user ON collections(user_id);

-- Создание таблицы Collection_poi (Связь коллекций с POI)
CREATE TABLE collection_poi (
    id BIGSERIAL PRIMARY KEY,
    order_index SMALLINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    collection_id BIGINT NOT NULL,
    poi_id BIGINT NOT NULL,

    CONSTRAINT fk_collection_poi_collection FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
    CONSTRAINT fk_collection_poi_poi FOREIGN KEY (poi_id) REFERENCES pois(id) ON DELETE CASCADE,
    CONSTRAINT unique_collection_poi UNIQUE (collection_id, poi_id)
);

-- Индекс для сортировки по порядку в коллекции
CREATE INDEX idx_collection_poi_order ON collection_poi(collection_id, order_index);

-- Создание индекса для эффективного поиска POI в коллекциях
CREATE INDEX idx_collection_poi_poi ON collection_poi(poi_id);

-- Добавляем триггер для обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггеры для обновления updated_at
CREATE TRIGGER update_preset_filters_updated_at
    BEFORE UPDATE ON preset_filters
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_collections_updated_at
    BEFORE UPDATE ON collections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();