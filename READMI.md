запуск:
docker compose up --build
docker compose -f docker-compose.gateway.yml up -d
ngrok http 8090
