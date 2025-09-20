package com.carizon.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class MergeService {
    private final JdbcTemplate jdbc;

    // 공통: 임시 매핑테이블 준비
    private void prepareTmpPlateMap() {
        jdbc.execute("DROP TEMPORARY TABLE IF EXISTS tmp_plate_map");
        jdbc.execute("""
      CREATE TEMPORARY TABLE tmp_plate_map(
        plate_no_norm VARCHAR(30) PRIMARY KEY,
        carizon_id BIGINT
      ) ENGINE=Memory
    """);
    }

    // ========== ENCAR ==========
    @Transactional
    public int mergeFromEncar_NoBinding(LocalDate bizDate) {
        prepareTmpPlateMap();

        // 1) 오늘 대상 번호 목록(정규화)
        jdbc.update("""
      INSERT INTO tmp_plate_map(plate_no_norm)
      SELECT DISTINCT fn_normalize_plate(e.vehicle_no)
        FROM raw_encar e
       WHERE e.vehicle_no IS NOT NULL
    """);

        // 2) 현재 ONSALE 마스터와 매핑
        jdbc.update("""
      UPDATE tmp_plate_map t
      JOIN my_car_master m ON m.plate_no_norm = t.plate_no_norm AND m.adv_status='ONSALE'
      SET t.carizon_id = m.carizon_id
    """);

        // 3) 매핑 안 된 번호는 새 마스터 생성
        jdbc.update("""
      INSERT INTO my_car_master(plate_no_raw, plate_no_norm, adv_status, last_seen_date)
      SELECT e.vehicle_no, fn_normalize_plate(e.vehicle_no), 'ONSALE', ?
        FROM raw_encar e
       WHERE e.vehicle_no IS NOT NULL
         AND NOT EXISTS (
           SELECT 1 FROM tmp_plate_map t WHERE t.plate_no_norm = fn_normalize_plate(e.vehicle_no) AND t.carizon_id IS NOT NULL
         )
      GROUP BY fn_normalize_plate(e.vehicle_no), e.vehicle_no
    """, bizDate);

        // 4) 새로 생긴 carizon_id까지 포함해 다시 매핑 완성
        jdbc.update("""
      UPDATE tmp_plate_map t
      JOIN my_car_master m ON m.plate_no_norm = t.plate_no_norm
      SET t.carizon_id = COALESCE(t.carizon_id, m.carizon_id)
    """);

        // 5) platform_car upsert (carizon_id FK)
        jdbc.update("""
      INSERT INTO platform_car(platform, carizon_id, platform_key, price, title, link_url, reg_date, extra, last_seen_date)
      SELECT
        'ENCAR',
        t.carizon_id,
        CAST(e.vehicle_id AS CHAR(20)),
        e.price,
        JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.advertisement.title')),
        CONCAT('https://fem.encar.com/cars/detail/', CAST(e.vehicle_id AS CHAR(20))),
        DATE(e.regist_dt),
        JSON_OBJECT(
          'adv_status', e.adv_status,
          'fuel', e.fuel, 'color', e.color, 'body_type', e.body_type,
          'transmission', e.transmission, 'displacement', e.displacement,
          'mileage', e.mileage, 'manufacturer', e.manufacturer, 'model_group', e.model_group,
          'model_name', e.model_name, 'grade_name', e.grade_name
        ),
        ?
      FROM raw_encar e
      JOIN tmp_plate_map t ON t.plate_no_norm = fn_normalize_plate(e.vehicle_no)
      WHERE e.vehicle_no IS NOT NULL
      ON DUPLICATE KEY UPDATE
        price=VALUES(price),
        title=VALUES(title),
        link_url=VALUES(link_url),
        reg_date=VALUES(reg_date),
        extra=VALUES(extra),
        last_seen_date=VALUES(last_seen_date)
    """, bizDate);

        // 6) master 대표 필드 갱신(최근 본 번호/상태/last_seen)
        jdbc.update("""
      UPDATE my_car_master m
      JOIN tmp_plate_map t ON t.carizon_id = m.carizon_id
      SET m.plate_no_raw = COALESCE(m.plate_no_raw, m.plate_no_raw),
          m.plate_no_norm = t.plate_no_norm,
          m.adv_status='ONSALE',
          m.last_seen_date = ?,
          m.updated_at = NOW()
    """, bizDate);

        return 1;
    }

    // ========== CHACHACHA ==========
    @Transactional
    public int mergeFromChacha_NoBinding(LocalDate bizDate) {
        prepareTmpPlateMap();

        jdbc.update("INSERT INTO tmp_plate_map(plate_no_norm) SELECT DISTINCT fn_normalize_plate(car_no) FROM raw_chachacha WHERE car_no IS NOT NULL");
        jdbc.update("""
      UPDATE tmp_plate_map t
      JOIN my_car_master m ON m.plate_no_norm = t.plate_no_norm AND m.adv_status='ONSALE'
      SET t.carizon_id = m.carizon_id
    """);
        jdbc.update("""
      INSERT INTO my_car_master(plate_no_raw, plate_no_norm, adv_status, last_seen_date)
      SELECT c.car_no, fn_normalize_plate(c.car_no), 'ONSALE', ?
        FROM raw_chachacha c
       WHERE c.car_no IS NOT NULL
         AND NOT EXISTS (
           SELECT 1 FROM tmp_plate_map t WHERE t.plate_no_norm = fn_normalize_plate(c.car_no) AND t.carizon_id IS NOT NULL
         )
      GROUP BY fn_normalize_plate(c.car_no), c.car_no
    """, bizDate);
        jdbc.update("UPDATE tmp_plate_map t JOIN my_car_master m ON m.plate_no_norm = t.plate_no_norm SET t.carizon_id = COALESCE(t.carizon_id, m.carizon_id)");
        jdbc.update("""
      INSERT INTO platform_car(platform, carizon_id, platform_key, price, title, link_url, reg_date, extra, last_seen_date)
      SELECT
        'CHACHA',
        t.carizon_id,
        CAST(c.car_seq AS CHAR(20)),
        c.sell_amt,
        CONCAT(c.maker_name,' ',c.model_name,' ',c.class_name),
        CONCAT('https://www.kbchachacha.com/public/car/detail.kbc?carSeq=', CAST(c.car_seq AS CHAR(20))),
        DATE(c.order_dt),
        JSON_OBJECT('maker_name',c.maker_name,'model_name',c.model_name,'class_name',c.class_name,'region',c.region,'yymm',c.yymm,'km',c.km),
        ?
      FROM raw_chachacha c
      JOIN tmp_plate_map t ON t.plate_no_norm = fn_normalize_plate(c.car_no)
      WHERE c.car_no IS NOT NULL
      ON DUPLICATE KEY UPDATE
        price=VALUES(price),
        title=VALUES(title),
        link_url=VALUES(link_url),
        reg_date=VALUES(reg_date),
        extra=VALUES(extra),
        last_seen_date=VALUES(last_seen_date)
    """, bizDate);
        jdbc.update("""
      UPDATE my_car_master m
      JOIN tmp_plate_map t ON t.carizon_id = m.carizon_id
      SET m.adv_status='ONSALE', m.last_seen_date=?, m.updated_at=NOW()
    """, bizDate);
        return 1;
    }

    // ========== CHUTCHA ==========
    @Transactional
    public int mergeFromChutcha_NoBinding(LocalDate bizDate) {
        prepareTmpPlateMap();

        jdbc.update("INSERT INTO tmp_plate_map(plate_no_norm) SELECT DISTINCT fn_normalize_plate(number_plate) FROM raw_chutcha WHERE number_plate IS NOT NULL");
        jdbc.update("""
      UPDATE tmp_plate_map t
      JOIN my_car_master m ON m.plate_no_norm = t.plate_no_norm AND m.adv_status='ONSALE'
      SET t.carizon_id = m.carizon_id
    """);
        jdbc.update("""
      INSERT INTO my_car_master(plate_no_raw, plate_no_norm, adv_status, last_seen_date, thumb_url)
      SELECT cc.number_plate, fn_normalize_plate(cc.number_plate), 'ONSALE', ?, cc.list_img_path
        FROM raw_chutcha cc
       WHERE cc.number_plate IS NOT NULL
         AND NOT EXISTS (
           SELECT 1 FROM tmp_plate_map t WHERE t.plate_no_norm = fn_normalize_plate(cc.number_plate) AND t.carizon_id IS NOT NULL
         )
      GROUP BY fn_normalize_plate(cc.number_plate), cc.number_plate, cc.list_img_path
    """, bizDate);
        jdbc.update("UPDATE tmp_plate_map t JOIN my_car_master m ON m.plate_no_norm = t.plate_no_norm SET t.carizon_id = COALESCE(t.carizon_id, m.carizon_id)");
        jdbc.update("""
      INSERT INTO platform_car(platform, carizon_id, platform_key, price, title, link_url, reg_date, extra, last_seen_date)
      SELECT
        'CHUTCHA',
        t.carizon_id,
        cc.detail_hash,
        cc.price,
        CONCAT(cc.brand_name,' ',cc.model_name,' ',COALESCE(cc.sub_model_name,''),' ',COALESCE(cc.grade_name,'')),
        CONCAT('https://web.chutcha.net/bmc/detail/', cc.detail_hash),
        NULL,
        JSON_OBJECT(
          'brand_name',cc.brand_name,'model_name',cc.model_name,'grade_name',cc.grade_name,
          'sub_model_name',cc.sub_model_name,'first_reg_year',cc.first_reg_year,'first_reg_month',cc.first_reg_month,
          'mileage',cc.mileage,'list_img_path',cc.list_img_path,'share_hash',cc.share_hash
        ),
        ?
      FROM raw_chutcha cc
      JOIN tmp_plate_map t ON t.plate_no_norm = fn_normalize_plate(cc.number_plate)
      WHERE cc.number_plate IS NOT NULL
      ON DUPLICATE KEY UPDATE
        price=VALUES(price),
        title=VALUES(title),
        link_url=VALUES(link_url),
        reg_date=VALUES(reg_date),
        extra=VALUES(extra),
        last_seen_date=VALUES(last_seen_date)
    """, bizDate);
        jdbc.update("""
      UPDATE my_car_master m
      JOIN tmp_plate_map t ON t.carizon_id = m.carizon_id
      SET m.adv_status='ONSALE', m.last_seen_date=?, m.updated_at=NOW(),
          m.thumb_url = COALESCE(m.thumb_url, m.thumb_url) -- 필요 시 최신값으로 교체
    """, bizDate);
        return 1;
    }

    // ========== KCAR (번호 매핑된 것만) ==========
    @Transactional
    public int mergeFromKcarLinked_NoBinding(LocalDate bizDate) {
        prepareTmpPlateMap();

        // kcar_link(car_cd, plate_no_norm) 형태라고 가정(번호를 알게 된 데이터)
        // 없다면 이 부분은 스킵하거나, 별도 매핑 API로 먼저 넣어야 함.
        jdbc.update("""
      INSERT INTO tmp_plate_map(plate_no_norm)
      SELECT DISTINCT kl.plate_no_norm FROM kcar_link kl
    """);

        jdbc.update("""
      UPDATE tmp_plate_map t
      JOIN my_car_master m ON m.plate_no_norm = t.plate_no_norm AND m.adv_status='ONSALE'
      SET t.carizon_id = m.carizon_id
    """);

        jdbc.update("""
      INSERT INTO my_car_master(plate_no_raw, plate_no_norm, adv_status, last_seen_date)
      SELECT kl.plate_no_norm, kl.plate_no_norm, 'ONSALE', ?
        FROM kcar_link kl
       WHERE NOT EXISTS (
         SELECT 1 FROM tmp_plate_map t WHERE t.plate_no_norm = kl.plate_no_norm AND t.carizon_id IS NOT NULL
       )
    """, bizDate);

        jdbc.update("UPDATE tmp_plate_map t JOIN my_car_master m ON m.plate_no_norm = t.plate_no_norm SET t.carizon_id = COALESCE(t.carizon_id, m.carizon_id)");

        jdbc.update("""
      INSERT INTO platform_car(platform, carizon_id, platform_key, price, title, link_url, reg_date, extra, last_seen_date)
      SELECT
        'KCAR',
        t.carizon_id,
        rk.car_cd,
        rk.price,
        rk.model_full,
        CONCAT('https://m.kcar.com/bc/detail/carInfoDtl?i_sCarCd=', rk.car_cd),
        NULL,
        JSON_OBJECT('main_img', rk.main_img, 'mfg_ym', rk.mfg_ym, 'mileage', rk.mileage),
        ?
      FROM raw_kcar rk
      JOIN kcar_link kl ON kl.car_cd = rk.car_cd
      JOIN tmp_plate_map t ON t.plate_no_norm = kl.plate_no_norm
      ON DUPLICATE KEY UPDATE
        price=VALUES(price),
        title=VALUES(title),
        link_url=VALUES(link_url),
        extra=VALUES(extra),
        last_seen_date=VALUES(last_seen_date)
    """, bizDate);

        jdbc.update("""
      UPDATE my_car_master m
      JOIN tmp_plate_map t ON t.carizon_id = m.carizon_id
      SET m.adv_status='ONSALE', m.last_seen_date=?, m.updated_at=NOW()
    """, bizDate);

        return 1;
    }
}
