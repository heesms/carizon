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
SEED_FIXED_TMP="${SEED_FIXED_TMP:-/tmp/carizon_seed_fixed.sql}"
MIGRATIONS_DIR="${MIGRATIONS_DIR:-./backend/src/main/resources/db/migrations}"
SEED_SOURCE="${SEED_SOURCE:-./infra/mysql/carizon_seed.sql}"
SKIP_EXISTENCE_CHECK="${SKIP_EXISTENCE_CHECK:-false}"
RESET_DATABASE="${RESET_DATABASE:-false}"
SEED_FORCE_IMPORT="${SEED_FORCE_IMPORT:-false}"

normalize_seed_file() {
  local src="$1"
  local dst="$2"

  if [ ! -f "${src}" ]; then
    echo "[bootstrap] seed source not found: ${src}"
    return 1
  fi

  local file_info=""
  if command -v file >/dev/null 2>&1; then
    file_info="$(file -b "${src}" || true)"
  fi

  echo "[bootstrap] normalize seed: ${src} (${file_info:-unknown})"
  if echo "${file_info}" | grep -qi 'utf-16'; then
    if ! iconv -f UTF-16 -t UTF-8 "${src}" -o "${dst}"; then
      if ! iconv -f UTF-16LE -t UTF-8 "${src}" -o "${dst}"; then
        echo "[bootstrap] iconv failed. fallback: cp with binary cleanup"
        cp "${src}" "${dst}"
      fi
    fi
  else
    cp "${src}" "${dst}"
  fi

  tr -d '\000' < "${dst}" > "${dst}.clean"
  mv "${dst}.clean" "${dst}"
  sed -i 's/\r$//' "${dst}"
  echo "[bootstrap] prepared seed file: ${dst}"
}

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
  if normalize_seed_file "${SEED_SOURCE}" "${SEED_FIXED_TMP}"; then
    echo "[bootstrap] run seed: ${SEED_FIXED_TMP}"
    if [ "${SEED_FORCE_IMPORT}" = "true" ]; then
      docker exec -i "${MYSQL_CONTAINER}" mysql --default-character-set=utf8mb4 --binary-mode=1 --force -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${SEED_FIXED_TMP}"
    else
      docker exec -i "${MYSQL_CONTAINER}" mysql --default-character-set=utf8mb4 --binary-mode=1 -u"${MYSQL_ROOT_USER}" -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "${SEED_FIXED_TMP}"
    fi
  else
    echo "[bootstrap] skipped seed import due to missing/invalid source."
  fi
else
  echo "[bootstrap] seed file not found, skipped: ${SEED_SOURCE}"
fi

echo "[bootstrap] done"
