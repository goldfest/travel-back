# Базовый образ с Java 21
FROM openjdk:21-jdk-slim as builder

# Установка Maven
RUN apt-get update && apt-get install -y maven

# Рабочая директория
WORKDIR /app

# Копируем pom.xml
COPY pom.xml .

# Скачиваем зависимости
RUN mvn dependency:go-offline

# Копируем исходный код
COPY src ./src

# Собираем приложение
RUN mvn clean package -DskipTests

# Финальный образ
FROM openjdk:21-jdk-slim

# Устанавливаем зависимости для работы с PostgreSQL
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

# Создаем пользователя для безопасности
RUN addgroup --system app && adduser --system --ingroup app app

# Рабочая директория
WORKDIR /app

# Копируем собранный JAR файл
COPY --from=builder /app/target/*.jar app.jar

# Копируем скрипт запуска
COPY --chown=app:app entrypoint.sh .

# Устанавливаем права
RUN chmod +x entrypoint.sh

# Переключаемся на пользователя app
USER app

# Точка входа
ENTRYPOINT ["./entrypoint.sh"]