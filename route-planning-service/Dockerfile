FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Копируем только файлы зависимостей для кэширования
COPY pom.xml .
COPY route-planning-service/pom.xml route-planning-service/

# Скачиваем зависимости
RUN mvn dependency:go-offline -B

# Копируем исходный код
COPY route-planning-service/src route-planning-service/src

# Собираем приложение
RUN mvn -f route-planning-service/pom.xml clean package -DskipTests

# Создаем образ для запуска
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Копируем собранный JAR
COPY --from=build /app/route-planning-service/target/*.jar app.jar

# Создаем пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Открываем порт
EXPOSE 8082

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "/app/app.jar"]