# City Catalog Service

Микросервис для управления городами в приложении для путешественников.

## Описание

Сервис предоставляет REST API для управления справочником городов, включая:
- Создание, чтение, обновление и удаление городов
- Поиск городов по различным критериям
- Получение популярных городов
- Поиск городов по названию, стране или описанию

## Технологии

- Java 21
- Spring Boot 3.2.0
- Spring Data JPA
- PostgreSQL 16
- Flyway для миграций
- MapStruct для маппинга DTO
- OpenAPI 3.0 (Swagger)

## API Endpoints

### Cities
- `GET /api/v1/cities` - Получить все города (с пагинацией)
- `GET /api/v1/cities/{id}` - Получить город по ID
- `GET /api/v1/cities/slug/{slug}` - Получить город по slug
- `GET /api/v1/cities/popular` - Получить популярные города
- `GET /api/v1/cities/search?query={query}` - Поиск городов
- `GET /api/v1/cities/country/{countryCode}` - Получить города по коду страны
- `POST /api/v1/cities` - Создать новый город
- `PUT /api/v1/cities/{id}` - Обновить город
- `DELETE /api/v1/cities/{id}` - Удалить город

## Конфигурация

### Настройки приложения

Файлы конфигурации:
- `application.yml` - общие настройки
- `application-dev.yml` - настройки для разработки
- `application-docker.yml` - настройки для Docker

### Переменные окружения

- `SPRING_PROFILES_ACTIVE` - активный профиль (dev, docker)
- `SERVER_PORT` - порт сервера (по умолчанию 8081)
- `DB_USERNAME` - имя пользователя БД
- `DB_PASSWORD` - пароль БД
- `DB_URL` - URL подключения к БД

## Запуск

### Локальный запуск

1. Убедитесь, что установлены Java 21 и Maven
2. Установите PostgreSQL и создайте базу данных `city_catalog_db`
3. Запустите приложение:
```bash
mvn spring-boot:run -Dspring.profiles.active=dev