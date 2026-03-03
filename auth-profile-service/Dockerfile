# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# su-exec нужен, чтобы запускать java от spring после chown
RUN apk add --no-cache su-exec

# jar
COPY --from=builder /app/target/*.jar /app/app.jar

# user
RUN addgroup -S spring && adduser -S spring -G spring

# entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8084

# ВАЖНО: ENTRYPOINT от root, внутри скрипта запустим java от spring
ENTRYPOINT ["/app/entrypoint.sh"]