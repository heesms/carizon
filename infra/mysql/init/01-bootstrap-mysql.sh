#!/usr/bin/env bash
set -eu

set -o pipefail

MYSQL_BIN="${MYSQL_BIN:-mysql}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
MYSQL_DATABASE="${MYSQL_DATABASE:-carizon}"
MYSQL_BOOTSTRAP_SCHEMA="${MYSQL_BOOTSTRAP_SCHEMA:-true}"
MYSQL_BOOTSTRAP_MIGRATIONS="${MYSQL_BOOTSTRAP_MIGRATIONS:-true}"
MYSQL_BOOTSTRAP_SCHEMA_SQL="${MYSQL_BOOTSTRAP_SCHEMA_SQL:-/docker-init/schema_from_excel.sql}"
MYSQL_BOOTSTRAP_SEED_SQL="${MYSQL_BOOTSTRAP_SEED_SQL:-/docker-init/carizon_seed.sql}"
MYSQL_BOOTSTRAP_MIGRATION_DIR="${MYSQL_BOOTSTRAP_MIGRATION_DIR:-/docker-migrations}"
MYSQL_IMPORT_DUMP_FILE="${MYSQL_IMPORT_DUMP_FILE:-}"
MYSQL_BOOTSTRAP_SKIP_IF_EXISTS="${MYSQL_BOOTSTRAP_SKIP_IF_EXISTS:-true}"
MYSQL_BOOTSTRAP_GUARD_TABLE="${MYSQL_BOOTSTRAP_GUARD_TABLE:-car_master}"
MYSQL_BOOTSTRAP_SEED="${MYSQL_BOOTSTRAP_SEED:-true}"

MYSQL_CMD=("${MYSQL_BIN}" --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}")

run_sql_file() {
  local file_path="$1"

  if [ ! -f "${file_path}" ]; then
    echo "[mysql-bootstrap] skip missing file: ${file_path}"
    return 0
  fi

  echo "[mysql-bootstrap] run: ${file_path}"
  "${MYSQL_CMD[@]}" --force < "${file_path}"
}

has_table() {
  local table_name="$1"
  local count_result

  if count_result="$("${MYSQL_CMD[@]}" -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name='${table_name}';")"; then
    [ "${count_result}" -gt 0 ]
  else
    echo "[mysql-bootstrap] table check failed for ${table_name}, continue bootstrap"
    return 1
  fi
}

should_bootstrap="true"
if [ "${MYSQL_BOOTSTRAP_SKIP_IF_EXISTS}" = "true" ] && [ -n "${MYSQL_BOOTSTRAP_GUARD_TABLE}" ]; then
  if has_table "${MYSQL_BOOTSTRAP_GUARD_TABLE}"; then
    should_bootstrap="false"
  fi
fi

if [ "${should_bootstrap}" = "false" ]; then
  echo "[mysql-bootstrap] table already exists: ${MYSQL_BOOTSTRAP_GUARD_TABLE}"
  echo "[mysql-bootstrap] skip schema/migrations/seed bootstrap"
  echo "[mysql-bootstrap] mysql bootstrap done"
  exit 0
fi

echo "[mysql-bootstrap] mysql bootstrap start"

if [ -n "${MYSQL_IMPORT_DUMP_FILE}" ]; then
  run_sql_file "${MYSQL_IMPORT_DUMP_FILE}"
fi

if [ "${MYSQL_BOOTSTRAP_SCHEMA}" = "true" ]; then
  run_sql_file "${MYSQL_BOOTSTRAP_SCHEMA_SQL}"
fi

if [ "${MYSQL_BOOTSTRAP_MIGRATIONS}" = "true" ]; then
  for sql in \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/001_create_llm_config_tables.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/002_create_system_config_table.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/003_create_batch_job_tables.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/004_add_code_mapping_jobs.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/005_add_platform_car_indexes_safe.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/006_add_car_master_merge_indexes_safe.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/007_add_car_image_url_columns.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/008_create_api_run_table.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/009_fix_cz_code_map_platform_name_size.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/010_add_price_new_columns.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/011_create_cz_model_new_price_and_fix_grade_null.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/012_cz_model_new_price_per_car.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/013_add_cz_maker_country_name.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/014_alter_platform_car_car_id_bigint.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/015_create_cz_model_embedding_source.sql" \
    "${MYSQL_BOOTSTRAP_MIGRATION_DIR}/022_create_raw_encar_truck_table.sql"
  do
    run_sql_file "${sql}"
  done
fi

if [ "${MYSQL_BOOTSTRAP_SEED}" = "true" ]; then
  run_sql_file "${MYSQL_BOOTSTRAP_SEED_SQL}"
fi

echo "[mysql-bootstrap] mysql bootstrap done"
