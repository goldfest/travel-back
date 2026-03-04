CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- Создание таблицы users
CREATE TABLE IF NOT EXISTS users (
                                     id BIGSERIAL PRIMARY KEY,
                                     email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(32),
    avatar_url VARCHAR(500),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP,
    home_city_id BIGINT,
    preferences_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- Создание таблицы refresh_tokens
CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id BIGSERIAL PRIMARY KEY,
                                              token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

-- Создание индексов для users
CREATE INDEX IF NOT EXISTS idx_user_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_user_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_user_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_user_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_user_created_at ON users(created_at);

-- Создание индексов для refresh_tokens
CREATE INDEX IF NOT EXISTS idx_refresh_token_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_token ON refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_token_expiry_date ON refresh_tokens(expiry_date);
CREATE INDEX IF NOT EXISTS idx_refresh_token_revoked ON refresh_tokens(revoked);

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггер для users
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Администратор (пароль: admin123)
INSERT INTO users (email, username, password_hash, role, status, is_blocked, created_at, updated_at)
VALUES (
           'admin@mail.ru',
           'admin',
           '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVFCVC',
           'ADMIN',
           'ACTIVE',
           false,
           CURRENT_TIMESTAMP,
           CURRENT_TIMESTAMP
       ) ON CONFLICT (email) DO NOTHING;

-- Пользователь vano (пароль: 555555va)
INSERT INTO users (email, username, password_hash, role, status, is_blocked, created_at, updated_at)
VALUES (
           'vano00189@mail.ru',
           'vano',
           '$2a$10$9s5p6GJ5k9FQK7cF8q9qTOVX4sG8Z1Q8yCkQ1Y9R9N8nG7x6d5a3e',
           'USER',
           'ACTIVE',
           false,
           CURRENT_TIMESTAMP,
           CURRENT_TIMESTAMP
       ) ON CONFLICT (email) DO NOTHING;

UPDATE users
SET password_hash = crypt('555555va', gen_salt('bf', 10)),
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'vano00189@mail.ru';

UPDATE users
SET password_hash = crypt('admin123', gen_salt('bf', 10)),
    updated_at = CURRENT_TIMESTAMP
WHERE email = 'admin@mail.ru';