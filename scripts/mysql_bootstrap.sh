#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   MYSQL_ROOT_PASSWORD=... \
#   DDL_SOURCE=./schema_from_excel.sql \
#   SEED_SOURCE=./infra/mysql/carizon_seed.sql \
#   bash scripts/mysql_bootstrap.sh

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.mvp.yml}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-carizon-mysql}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-carizon}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD}"
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"

DDL_SOURCE="${DDL_SOURCE:-./schema_from_excel.sql}"
DDL_FIXED_TMP="${DDL_FIXED_TMP:-/tmp/carizon_schema_fixed.sql}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-./backend/src/main/resources/db/migrations}"
SEED_SOURCE="${SEED_SOURCE:-./infra/mysql/carizon_seed.sql}"
SKIP_EXISTENCE_CHECK="${SKIP_EXISTENCE_CHECK:-false}"
RESET_DATABASE="${RESET_DATABASE:-false}"

echo "[bootstrap] start mysql bootstrap"
docker compose -f "${COMPOSE_FILE}" up -d mysql
echo "[bootstrap] waiting mysql healthy"
for _ in $(seq 1 40); do
  if docker inspect -f '{{.State.Health.Status}}' "${MYSQL_CONTAINER}" | grep -q healthy; then
    break
  fi
  sleep 2
done

if docker inspect -f '{{.State.Health.Status}}' "${MYSQL_CONTAINER}" | grep -q healthy; then
  echo "[bootstrap] mysql healthy"
else
  echo "[bootstrap] mysql not healthy. check logs: docker compose -f ${COMPOSE_FILE} logs mysql"
  exit 1
fi

if [ "${RESET_DATABASE}" = "true" ]; then
  docker exec "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" -e "DROP DATABASE IF EXISTS ${MYSQL_DATABASE};"
fi

docker exec "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" -e "CREATE DATABASE IF NOT EXISTS ${MYSQL_DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

if [ "${SKIP_EXISTENCE_CHECK}" != "true" ]; then
  table_count="$(docker exec "${MYSQL_CONTAINER}" mysql -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}';" -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}")"
  if [ "${table_count}" -gt 0 ]; then
    echo "[bootstrap] database already has tables. if you want fresh init, set RESET_DATABASE=true"
    echo "[bootstrap] skipped bootstrap to avoid overwrite."
    exit 0
  fi
fi

if ! command -v perl >/dev/null 2>&1; then
  echo "[bootstrap] perl is required for DDL auto-fix. install perl or pre-fix schema file manually."
  exit 1
fi
perl -pe 's/\bVARCHAR\b(?=\s*[,)\n])/VARCHAR(255)/g' "${DDL_SOURCE}" > "${DDL_FIXED_TMP}"
echo "[bootstrap] fixed DDL written: ${DDL_FIXED_TMP}"

cat "${DDL_FIXED_TMP}" | docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"

for file in "${MIGRATIONS_DIR}"/*.sql; do
  [ -f "${file}" ] || continue
  base="$(basename "${file}")"

  # prefer safe migrations for duplicated versions
  case "${base}" in
    005_add_platform_car_indexes.sql)
      safe_file="${MIGRATIONS_DIR}/005_add_platform_car_indexes_safe.sql"
      if [ -f "${safe_file}" ]; then
        echo "[bootstrap] run migration: ${safe_file} (safe replacement)"
        cat "${safe_file}" | docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
      else
        echo "[bootstrap] run migration: ${file}"
        cat "${file}" | docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
      fi
      continue
      ;;
    006_add_car_master_merge_indexes.sql)
      safe_file="${MIGRATIONS_DIR}/006_add_car_master_merge_indexes_safe.sql"
      if [ -f "${safe_file}" ]; then
        echo "[bootstrap] run migration: ${safe_file} (safe replacement)"
        cat "${safe_file}" | docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
      else
        echo "[bootstrap] run migration: ${file}"
        cat "${file}" | docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
      fi
      continue
      ;;
  esac

  echo "[bootstrap] run migration: ${file}"
  cat "${file}" | docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
done

if [ -f "${SEED_SOURCE}" ]; then
  echo "[bootstrap] run seed: ${SEED_SOURCE}"
  cat "${SEED_SOURCE}" | docker exec -i "${MYSQL_CONTAINER}" mysql -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
else
  echo "[bootstrap] seed file not found, skipped: ${SEED_SOURCE}"
fi

echo "[bootstrap] done"
