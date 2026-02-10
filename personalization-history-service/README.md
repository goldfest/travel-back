# Personalization & History Service

Микросервис для управления персонализацией и историей пользователей в приложении для путешественников.

## Функциональность

- Управление избранными объектами (добавление/удаление/просмотр)
- Создание и управление коллекциями объектов
- Управление пресетами фильтров для поиска
- Ведение истории поисковых запросов
- Кэширование данных для повышения производительности

## Технологии

- Java 21
- Spring Boot 3.2.0
- PostgreSQL 16
- Redis для кэширования
- Spring Data JPA
- Docker

## API Endpoints

### Избранное
- `POST /api/v1/favorites` - Добавить в избранное
- `GET /api/v1/favorites` - Получить избранное пользователя
- `DELETE /api/v1/favorites/{poiId}` - Удалить из избранного

### Коллекции
- `POST /api/v1/collections` - Создать коллекцию
- `GET /api/v1/collections` - Получить коллекции пользователя
- `POST /api/v1/collections/{collectionId}/pois` - Добавить POI в коллекцию

### Пресеты фильтров
- `POST /api/v1/preset-filters` - Создать пресет фильтра
- `GET /api/v1/preset-filters/context` - Получить пресеты для контекста

### История поиска
- `POST /api/v1/search-history` - Записать поиск
- `GET /api/v1/search-history` - Получить историю поиска
- `GET /api/v1/search-history/recent-queries` - Получить недавние запросы

## Запуск

### С использованием Docker Compose
```bash
docker-compose up personalization-history-service