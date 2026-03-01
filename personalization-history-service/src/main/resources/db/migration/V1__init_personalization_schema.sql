-- V1: init personalization schema (без FK на чужие сервисы)

-- PresetFilter
CREATE TABLE IF NOT EXISTS preset_filters (
                                              id BIGSERIAL PRIMARY KEY,
                                              name VARCHAR(100) NOT NULL,
    filters_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL,
    city_id BIGINT NOT NULL,
    poi_type_id BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_preset_filters_user_id ON preset_filters(user_id);
CREATE INDEX IF NOT EXISTS idx_preset_filters_city_type ON preset_filters(city_id, poi_type_id);

-- SearchHistory
CREATE TABLE IF NOT EXISTS search_history (
                                              id BIGSERIAL PRIMARY KEY,
                                              query_text VARCHAR(255) NOT NULL,
    searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    filters_json JSONB,
    user_id BIGINT NOT NULL,
    city_id BIGINT NOT NULL,
    preset_filter_id BIGINT,
    CONSTRAINT fk_search_history_preset
    FOREIGN KEY (preset_filter_id) REFERENCES preset_filters(id) ON DELETE SET NULL
    );

CREATE INDEX IF NOT EXISTS idx_search_history_user ON search_history(user_id);
CREATE INDEX IF NOT EXISTS idx_search_history_searched_at ON search_history(searched_at DESC);
CREATE INDEX IF NOT EXISTS idx_search_history_city_user ON search_history(city_id, user_id);

-- Favorites
CREATE TABLE IF NOT EXISTS favorites (
                                         id BIGSERIAL PRIMARY KEY,
                                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                         user_id BIGINT NOT NULL,
                                         poi_id BIGINT NOT NULL,
                                         CONSTRAINT unique_user_poi_favorite UNIQUE (user_id, poi_id)
    );

CREATE INDEX IF NOT EXISTS idx_favorites_user ON favorites(user_id);
CREATE INDEX IF NOT EXISTS idx_favorites_created_at ON favorites(created_at DESC);

-- Collections
CREATE TABLE IF NOT EXISTS collections (
                                           id BIGSERIAL PRIMARY KEY,
                                           name VARCHAR(255) NOT NULL,
    description TEXT,
    cover_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_collections_user ON collections(user_id);

-- Collection_poi
CREATE TABLE IF NOT EXISTS collection_poi (
                                              id BIGSERIAL PRIMARY KEY,
                                              order_index INTEGER DEFAULT 0,
                                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                              collection_id BIGINT NOT NULL,
                                              poi_id BIGINT NOT NULL,
                                              CONSTRAINT fk_collection_poi_collection
                                              FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE,
    CONSTRAINT unique_collection_poi UNIQUE (collection_id, poi_id)
    );

CREATE INDEX IF NOT EXISTS idx_collection_poi_order ON collection_poi(collection_id, order_index);
CREATE INDEX IF NOT EXISTS idx_collection_poi_poi ON collection_poi(poi_id);

-- Trigger function for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_preset_filters_updated_at ON preset_filters;
CREATE TRIGGER update_preset_filters_updated_at
    BEFORE UPDATE ON preset_filters
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_collections_updated_at ON collections;
CREATE TRIGGER update_collections_updated_at
    BEFORE UPDATE ON collections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();