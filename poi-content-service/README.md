# POI & Content + Import Service

Микросервис для управления точками интереса (POI), контентом и импортом данных.

## Функциональность

- Управление точками интереса (POI)
- Управление типами POI
- Импорт данных из внешних источников (Google Maps, Yandex, Wikipedia и др.)
- Фильтрация и поиск POI
- Геопоиск (ближайшие объекты)
- Модерация контента

## Технологии

- Java 21
- Spring Boot 3.2
- PostgreSQL + PostGIS
- Redis (кеширование)
- Flyway (миграции)
- OpenAPI 3 (документация)

## Запуск

### Локально с Docker

```bash
# Запуск базы данных
docker-compose up -d poi-db redis

# Запуск приложения
mvn spring-boot:run -Dspring-boot.run.profiles=dev