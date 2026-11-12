#!/usr/bin/env bash
# Восстановление БД из резервной копии, сделанной сервисом backup.
#   ./scripts/restore.sh backups/last/incidents-latest.sql.gz
set -euo pipefail

file="${1:?укажите файл резервной копии (*.sql.gz)}"
[[ -f "$file" ]] || { echo "Файл не найден: $file" >&2; exit 1; }

read -r -p "БД incidents будет перезаписана из $file. Продолжить? [y/N] " answer
[[ "$answer" == "y" ]] || exit 0

docker compose stop app
docker compose exec -T postgres psql -U incidents -d postgres -c "DROP DATABASE IF EXISTS incidents WITH (FORCE);"
docker compose exec -T postgres psql -U incidents -d postgres -c "CREATE DATABASE incidents OWNER incidents;"
gunzip -c "$file" | docker compose exec -T postgres psql -U incidents -d incidents -v ON_ERROR_STOP=1 -q
docker compose start app
echo "Готово."
