#!/bin/sh
set -e

# создаём директорию, если нет
mkdir -p /app/uploads

# выставляем владельца на пользователя spring
chown -R spring:spring /app/uploads

# запускаем приложение от spring
exec su-exec spring:spring java -jar /app/app.jar