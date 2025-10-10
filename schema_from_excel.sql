CREATE TABLE IF NOT EXISTS `car_master` (
  `CAR_ID` VARCHAR,
  `CAR_NO` VARCHAR,
  `MAKER_CODE` VARCHAR,
  `MODEL_GROUP_CODE` VARCHAR,
  `MODEL_CODE` VARCHAR,
  `TRIM_CODE` VARCHAR,
  `GRADE_CODE` VARCHAR,
  `YEAR` VARCHAR,
  `MILEAGE` VARCHAR,
  `COLOR` VARCHAR,
  `TRANSMISSION` VARCHAR,
  `FUEL` VARCHAR,
  `CREATED_AT` VARCHAR,
  `UPDATED_AT` VARCHAR,
  `adv_status` VARCHAR,
  `last_seen_date` VARCHAR,
  `region` VARCHAR,
  `displacement` VARCHAR,
  `BODY_TYPE` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `car_price_history` (
  `HISTORY_ID` VARCHAR,
  `PLATFORM_CAR_ID` VARCHAR,
  `PRICE` VARCHAR,
  `CHECKED_AT` VARCHAR,
  `is_current` VARCHAR,
  `last_seen_at` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `crawl_run` (
  `id` VARCHAR,
  `run_id` VARCHAR,
  `source` VARCHAR,
  `started_at` VARCHAR,
  `ended_at` VARCHAR,
  `total_items` VARCHAR,
  `status` VARCHAR,
  `message` VARCHAR,
  `created_at` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_code_map` (
  `platform_name` VARCHAR,
  `p_maker_code` VARCHAR,
  `p_model_group_code` VARCHAR,
  `p_model_code` VARCHAR,
  `p_trim_code` VARCHAR,
  `p_grade_code` VARCHAR,
  `p_maker_name_norm` VARCHAR,
  `p_model_group_name_norm` VARCHAR,
  `p_model_name_norm` VARCHAR,
  `p_trim_name_norm` VARCHAR,
  `p_grade_name_norm` VARCHAR,
  `p_key` VARCHAR,
  `ref_plate_no` VARCHAR,
  `maker_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `trim_code` VARCHAR,
  `grade_code` VARCHAR,
  `confidence_score` VARCHAR,
  `match_reason` VARCHAR,
  `status` VARCHAR,
  `first_seen` VARCHAR,
  `last_seen` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_forced_map` (
  `platform_name` VARCHAR,
  `depth` VARCHAR,
  `p_maker_code` VARCHAR,
  `p_model_group_code` VARCHAR,
  `p_model_code` VARCHAR,
  `p_trim_code` VARCHAR,
  `p_grade_code` VARCHAR,
  `maker_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `trim_code` VARCHAR,
  `grade_code` VARCHAR,
  `note` VARCHAR,
  `updated_at` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_grade` (
  `maker_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `trim_code` VARCHAR,
  `grade_code` VARCHAR,
  `grade_name` VARCHAR,
  `grade_order` VARCHAR,
  `model_grade_code` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_maker` (
  `maker_code` VARCHAR,
  `maker_name` VARCHAR,
  `country_code` VARCHAR,
  `maker_order` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_model` (
  `maker_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `model_name` VARCHAR,
  `car_order` VARCHAR,
  `from_year` VARCHAR,
  `to_year` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_model_group` (
  `maker_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_group_name` VARCHAR,
  `class_order` VARCHAR,
  `use_code` VARCHAR,
  `use_name` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_platform_priority` (
  `platform_name` VARCHAR,
  `priority` VARCHAR,
  `updated_at` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_price_history` (
  `my_car_id` VARCHAR,
  `platform_name` VARCHAR,
  `biz_date` VARCHAR,
  `price` VARCHAR,
  `created_at` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `cz_trim` (
  `maker_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `trim_code` VARCHAR,
  `trim_name` VARCHAR,
  `model_order` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `daily_listing_snapshot` (
  `snap_date` VARCHAR,
  `source` VARCHAR,
  `source_key` VARCHAR,
  `my_car_key` VARCHAR,
  `price` VARCHAR,
  `status` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `maker_code` (
  `maker_code` VARCHAR,
  `maker_name` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `model_code` (
  `model_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_name` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `model_group_code` (
  `model_group_code` VARCHAR,
  `maker_code` VARCHAR,
  `model_group_name` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `platform_car` (
  `PLATFORM_CAR_ID` VARCHAR,
  `PLATFORM_NAME` VARCHAR,
  `PLATFORM_CAR_KEY` VARCHAR,
  `CAR_NO` VARCHAR,
  `CAR_ID` VARCHAR,
  `MAKER_CODE` VARCHAR,
  `MODEL_GROUP_CODE` VARCHAR,
  `MODEL_CODE` VARCHAR,
  `TRIM_CODE` VARCHAR,
  `GRADE_CODE` VARCHAR,
  `MAKER_NAME` VARCHAR,
  `MODEL_GROUP_NAME` VARCHAR,
  `MODEL_NAME` VARCHAR,
  `TRIM_NAME` VARCHAR,
  `GRADE_NAME` VARCHAR,
  `PRICE` VARCHAR,
  `KM` VARCHAR,
  `YYMM` VARCHAR,
  `STATUS` VARCHAR,
  `COLOR` VARCHAR,
  `FUEL` VARCHAR,
  `transmission` VARCHAR,
  `BODY_TYPE` VARCHAR,
  `M_URL` VARCHAR,
  `PC_URL` VARCHAR,
  `FIRST_AD_DAY` VARCHAR,
  `CREATED_AT` VARCHAR,
  `UPDATED_AT` VARCHAR,
  `extra` VARCHAR,
  `last_seen_date` VARCHAR,
  `region` VARCHAR,
  `displacement` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `platform_car_bak` (
  `PLATFORM_CAR_ID` VARCHAR,
  `PLATFORM_NAME` VARCHAR,
  `PLATFORM_CAR_KEY` VARCHAR,
  `CAR_NO` VARCHAR,
  `CAR_ID` VARCHAR,
  `MAKER_CODE` VARCHAR,
  `MODEL_GROUP_CODE` VARCHAR,
  `MODEL_CODE` VARCHAR,
  `TRIM_CODE` VARCHAR,
  `GRADE_CODE` VARCHAR,
  `MAKER_NAME` VARCHAR,
  `MODEL_GROUP_NAME` VARCHAR,
  `MODEL_NAME` VARCHAR,
  `TRIM_NAME` VARCHAR,
  `GRADE_NAME` VARCHAR,
  `PRICE` VARCHAR,
  `KM` VARCHAR,
  `YYMM` VARCHAR,
  `STATUS` VARCHAR,
  `COLOR` VARCHAR,
  `FUEL` VARCHAR,
  `transmission` VARCHAR,
  `BODY_TYPE` VARCHAR,
  `M_URL` VARCHAR,
  `PC_URL` VARCHAR,
  `FIRST_AD_DAY` VARCHAR,
  `CREATED_AT` VARCHAR,
  `UPDATED_AT` VARCHAR,
  `extra` VARCHAR,
  `last_seen_date` VARCHAR,
  `region` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_chachacha` (
  `id` VARCHAR,
  `car_seq` VARCHAR,
  `car_no` VARCHAR,
  `sell_amt` VARCHAR,
  `yymm` VARCHAR,
  `km` VARCHAR,
  `color` VARCHAR,
  `use_code_name` VARCHAR,
  `gas_Name` VARCHAR,
  `order_dt` VARCHAR,
  `maker_code` VARCHAR,
  `class_code` VARCHAR,
  `car_code` VARCHAR,
  `model_code` VARCHAR,
  `grade_code` VARCHAR,
  `maker_name` VARCHAR,
  `class_name` VARCHAR,
  `car_name` VARCHAR,
  `model_name` VARCHAR,
  `grade_name` VARCHAR,
  `region` VARCHAR,
  `ad_day` VARCHAR,
  `first_ad_day` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR,
  `auto_gbn_name` VARCHAR,
  `displacement` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_chachacha_20251004` (
  `id` VARCHAR,
  `car_seq` VARCHAR,
  `car_no` VARCHAR,
  `sell_amt` VARCHAR,
  `yymm` VARCHAR,
  `km` VARCHAR,
  `color` VARCHAR,
  `use_code_name` VARCHAR,
  `gas_Name` VARCHAR,
  `order_dt` VARCHAR,
  `maker_code` VARCHAR,
  `class_code` VARCHAR,
  `car_code` VARCHAR,
  `model_code` VARCHAR,
  `grade_code` VARCHAR,
  `maker_name` VARCHAR,
  `class_name` VARCHAR,
  `car_name` VARCHAR,
  `model_name` VARCHAR,
  `grade_name` VARCHAR,
  `region` VARCHAR,
  `ad_day` VARCHAR,
  `first_ad_day` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR,
  `auto_gbn_name` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_charancha` (
  `id` VARCHAR,
  `sell_no` VARCHAR,
  `car_no` VARCHAR,
  `sell_price` VARCHAR,
  `yyyymm` VARCHAR,
  `mileage` VARCHAR,
  `displacement` VARCHAR,
  `fuel_code` VARCHAR,
  `fuel_name` VARCHAR,
  `transmission_code` VARCHAR,
  `accident` VARCHAR,
  `maker_code` VARCHAR,
  `model_code` VARCHAR,
  `model_detail_code` VARCHAR,
  `grade_code` VARCHAR,
  `maker_name` VARCHAR,
  `model_name` VARCHAR,
  `model_detail_name` VARCHAR,
  `grade_name` VARCHAR,
  `region_name` VARCHAR,
  `car_type_code` VARCHAR,
  `sell_start_dt` VARCHAR,
  `reg_dt` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR,
  `color_name` VARCHAR,
  `transmission_name` VARCHAR,
  `car_type` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_chutcha` (
  `id` VARCHAR,
  `car_id` VARCHAR,
  `number_plate` VARCHAR,
  `detail_hash` VARCHAR,
  `car_type` VARCHAR,
  `color` VARCHAR,
  `transmission_name` VARCHAR,
  `brand_name` VARCHAR,
  `model_name` VARCHAR,
  `sub_model_name` VARCHAR,
  `grade_name` VARCHAR,
  `sub_grade_name` VARCHAR,
  `first_reg_year` VARCHAR,
  `first_reg_month` VARCHAR,
  `mileage` VARCHAR,
  `price` VARCHAR,
  `list_img_path` VARCHAR,
  `share_hash` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR,
  `fuel_name` VARCHAR,
  `shop_addr_full` VARCHAR,
  `shop_addr_short` VARCHAR,
  `displacement` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_encar` (
  `id` VARCHAR,
  `vehicle_id` VARCHAR,
  `vehicle_no` VARCHAR,
  `price` VARCHAR,
  `adv_status` VARCHAR,
  `sell_type` VARCHAR,
  `fuel` VARCHAR,
  `color` VARCHAR,
  `body_type` VARCHAR,
  `transmission` VARCHAR,
  `displacement` VARCHAR,
  `mileage` VARCHAR,
  `manufacturer_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `grade_code` VARCHAR,
  `grade_detail_code` VARCHAR,
  `manufacturer_name` VARCHAR,
  `model_group_name` VARCHAR,
  `model_name` VARCHAR,
  `grade_name` VARCHAR,
  `grade_detail_name` VARCHAR,
  `year_month` VARCHAR,
  `form_year` VARCHAR,
  `regist_dt` VARCHAR,
  `first_ad_dt` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR,
  `sales_status` VARCHAR,
  `region` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_encar_20251004` (
  `id` VARCHAR,
  `vehicle_id` VARCHAR,
  `vehicle_no` VARCHAR,
  `price` VARCHAR,
  `adv_status` VARCHAR,
  `sell_type` VARCHAR,
  `fuel` VARCHAR,
  `color` VARCHAR,
  `body_type` VARCHAR,
  `transmission` VARCHAR,
  `displacement` VARCHAR,
  `mileage` VARCHAR,
  `manufacturer_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `grade_code` VARCHAR,
  `grade_detail_code` VARCHAR,
  `manufacturer_name` VARCHAR,
  `model_group_name` VARCHAR,
  `model_name` VARCHAR,
  `grade_name` VARCHAR,
  `grade_detail_name` VARCHAR,
  `year_month` VARCHAR,
  `form_year` VARCHAR,
  `regist_dt` VARCHAR,
  `first_ad_dt` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR,
  `sales_status` VARCHAR,
  `region` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_kcar` (
  `id` VARCHAR,
  `car_cd` VARCHAR,
  `cno` VARCHAR,
  `price` VARCHAR,
  `fuel` VARCHAR,
  `mileage` VARCHAR,
  `yymm` VARCHAR,
  `body_type` VARCHAR,
  `transmission` VARCHAR,
  `displacement` VARCHAR,
  `color` VARCHAR,
  `maker_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `grade_code` VARCHAR,
  `grade_detail_code` VARCHAR,
  `maker_name` VARCHAR,
  `model_group_name` VARCHAR,
  `model_name` VARCHAR,
  `grade_name` VARCHAR,
  `grade_detail_name` VARCHAR,
  `model_full` VARCHAR,
  `mfg_ym` VARCHAR,
  `main_img` VARCHAR,
  `region` VARCHAR,
  `center` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_kcar_20251004` (
  `id` VARCHAR,
  `car_cd` VARCHAR,
  `cno` VARCHAR,
  `price` VARCHAR,
  `fuel` VARCHAR,
  `mileage` VARCHAR,
  `yymm` VARCHAR,
  `body_type` VARCHAR,
  `transmission` VARCHAR,
  `displacement` VARCHAR,
  `color` VARCHAR,
  `maker_code` VARCHAR,
  `model_group_code` VARCHAR,
  `model_code` VARCHAR,
  `grade_code` VARCHAR,
  `grade_detail_code` VARCHAR,
  `maker_name` VARCHAR,
  `model_group_name` VARCHAR,
  `model_name` VARCHAR,
  `grade_name` VARCHAR,
  `grade_detail_name` VARCHAR,
  `model_full` VARCHAR,
  `mfg_ym` VARCHAR,
  `main_img` VARCHAR,
  `region` VARCHAR,
  `center` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `raw_tcar` (
  `id` VARCHAR,
  `car_id` VARCHAR,
  `plate_no` VARCHAR,
  `brand_id` VARCHAR,
  `brand_name` VARCHAR,
  `modelgroup_id` VARCHAR,
  `modelgroup_name` VARCHAR,
  `model_id` VARCHAR,
  `model_name` VARCHAR,
  `grade_id` VARCHAR,
  `grade_name` VARCHAR,
  `subgrade_id` VARCHAR,
  `subgrade_name` VARCHAR,
  `fuel_code` VARCHAR,
  `fuel_name` VARCHAR,
  `transmission` VARCHAR,
  `color_name` VARCHAR,
  `displacement` VARCHAR,
  `mileage` VARCHAR,
  `reg_year` VARCHAR,
  `price_new` VARCHAR,
  `price_sell` VARCHAR,
  `price_expect` VARCHAR,
  `return_price` VARCHAR,
  `rent_month_price` VARCHAR,
  `reg_date` VARCHAR,
  `reg_dt_compact` VARCHAR,
  `thumbnail_path` VARCHAR,
  `car_image_url` VARCHAR,
  `sale_type` VARCHAR,
  `status_code` VARCHAR,
  `payload` VARCHAR,
  `fetched_at` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS `trim_code` (
  `trim_code` VARCHAR,
  `model_code` VARCHAR,
  `trim_name` VARCHAR
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;