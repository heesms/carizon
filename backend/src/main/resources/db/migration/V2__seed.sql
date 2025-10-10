-- Seed data for car_master and platform_car with sample data across 3 platforms
-- Platform 1: ENCAR
INSERT INTO car_master (car_id, car_no, maker_code, model_group_code, model_code, trim_code, grade_code, 
                        maker_name, model_group_name, model_name, trim_name, 
                        year, mileage, color, transmission, fuel, displacement, body_type, region, 
                        adv_status, last_seen_date)
VALUES 
(1, '12가3456', 'HYN', 'SONATA', 'SONATA_DN8', 'DN8_25', 'DN8_25_SMART', 
 'HYUNDAI', 'SONATA', 'SONATA DN8', '2.5 SMARTSTREAM', 
 2023, 15000, 'WHITE', 'AUTO', 'GASOLINE', 2500, 'SEDAN', 'SEOUL',
 'ACTIVE', '2025-10-10'),
 
(2, '78나9012', 'KIA', 'K5', 'K5_DL3', 'DL3_16T', 'DL3_16T_PRESTIGE',
 'KIA', 'K5', 'K5 DL3', '1.6 TURBO', 
 2022, 25000, 'BLACK', 'AUTO', 'GASOLINE', 1600, 'SEDAN', 'BUSAN',
 'ACTIVE', '2025-10-10'),
 
(3, '34다5678', 'HYN', 'GRANDEUR', 'GN7', 'GN7_35', 'GN7_35_CALLIGRAPHY',
 'HYUNDAI', 'GRANDEUR', 'GRANDEUR GN7', '3.5 V6',
 2024, 5000, 'SILVER', 'AUTO', 'GASOLINE', 3500, 'SEDAN', 'GYEONGGI',
 'ACTIVE', '2025-10-10');

-- Platform car entries for ENCAR
INSERT INTO platform_car (platform_name, platform_car_key, car_id, car_no,
                          maker_code, model_group_code, model_code, trim_code, grade_code,
                          maker_name, model_group_name, model_name, trim_name,
                          price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                          m_url, pc_url, first_ad_day, last_seen_date)
VALUES
('ENCAR', 'ENCAR_123456', 1, '12가3456',
 'HYN', 'SONATA', 'SONATA_DN8', 'DN8_25', 'DN8_25_SMART',
 'HYUNDAI', 'SONATA', 'SONATA DN8', '2.5 SMARTSTREAM',
 28000000, 15000, 2500, '202301', 'ACTIVE', 'WHITE', 'GASOLINE', 'AUTO', 'SEDAN', 'SEOUL',
 'https://m.encar.com/dc/dc_carsearchpay.do?carno=123456', 'https://www.encar.com/dc/dc_carsearchpay.do?carno=123456',
 '2025-10-01', '2025-10-10'),

('ENCAR', 'ENCAR_789012', 2, '78나9012',
 'KIA', 'K5', 'K5_DL3', 'DL3_16T', 'DL3_16T_PRESTIGE',
 'KIA', 'K5', 'K5 DL3', '1.6 TURBO',
 24000000, 25000, 1600, '202205', 'ACTIVE', 'BLACK', 'GASOLINE', 'AUTO', 'SEDAN', 'BUSAN',
 'https://m.encar.com/dc/dc_carsearchpay.do?carno=789012', 'https://www.encar.com/dc/dc_carsearchpay.do?carno=789012',
 '2025-10-05', '2025-10-10');

-- Platform car entries for KCAR
INSERT INTO platform_car (platform_name, platform_car_key, car_id, car_no,
                          maker_code, model_group_code, model_code, trim_code, grade_code,
                          maker_name, model_group_name, model_name, trim_name,
                          price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                          m_url, pc_url, first_ad_day, last_seen_date)
VALUES
('KCAR', 'KCAR_ABC123', 2, '78나9012',
 'KIA', 'K5', 'K5_DL3', 'DL3_16T', 'DL3_16T_PRESTIGE',
 'KIA', 'K5', 'K5 DL3', '1.6 TURBO',
 23500000, 25000, 1600, '202205', 'ACTIVE', 'BLACK', 'GASOLINE', 'AUTO', 'SEDAN', 'BUSAN',
 'https://m.kcar.com/bc/view?id=ABC123', 'https://www.kcar.com/bc/view?id=ABC123',
 '2025-10-03', '2025-10-10'),

('KCAR', 'KCAR_DEF456', 3, '34다5678',
 'HYN', 'GRANDEUR', 'GN7', 'GN7_35', 'GN7_35_CALLIGRAPHY',
 'HYUNDAI', 'GRANDEUR', 'GRANDEUR GN7', '3.5 V6',
 45000000, 5000, 3500, '202403', 'ACTIVE', 'SILVER', 'GASOLINE', 'AUTO', 'SEDAN', 'GYEONGGI',
 'https://m.kcar.com/bc/view?id=DEF456', 'https://www.kcar.com/bc/view?id=DEF456',
 '2025-10-08', '2025-10-10');

-- Platform car entries for CHACHACHA
INSERT INTO platform_car (platform_name, platform_car_key, car_id, car_no,
                          maker_code, model_group_code, model_code, trim_code, grade_code,
                          maker_name, model_group_name, model_name, trim_name,
                          price, km, displacement, yymm, status, color, fuel, transmission, body_type, region,
                          m_url, pc_url, first_ad_day, last_seen_date)
VALUES
('CHACHACHA', 'CCC_789XYZ', 1, '12가3456',
 'HYN', 'SONATA', 'SONATA_DN8', 'DN8_25', 'DN8_25_SMART',
 'HYUNDAI', 'SONATA', 'SONATA DN8', '2.5 SMARTSTREAM',
 27800000, 15000, 2500, '202301', 'ACTIVE', 'WHITE', 'GASOLINE', 'AUTO', 'SEDAN', 'SEOUL',
 'https://m.kbchachacha.com/public/car/detail.kbc?carSeq=789XYZ', 'https://www.kbchachacha.com/public/car/detail.kbc?carSeq=789XYZ',
 '2025-09-28', '2025-10-10'),

('CHACHACHA', 'CCC_456ABC', 3, '34다5678',
 'HYN', 'GRANDEUR', 'GN7', 'GN7_35', 'GN7_35_CALLIGRAPHY',
 'HYUNDAI', 'GRANDEUR', 'GRANDEUR GN7', '3.5 V6',
 44500000, 5000, 3500, '202403', 'ACTIVE', 'SILVER', 'GASOLINE', 'AUTO', 'SEDAN', 'GYEONGGI',
 'https://m.kbchachacha.com/public/car/detail.kbc?carSeq=456ABC', 'https://www.kbchachacha.com/public/car/detail.kbc?carSeq=456ABC',
 '2025-10-06', '2025-10-10');

-- Price history snapshots
INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current, last_seen_at)
SELECT platform_car_id, price, DATE_SUB(NOW(), INTERVAL 10 DAY), 0, DATE_SUB(NOW(), INTERVAL 10 DAY)
FROM platform_car WHERE platform_name = 'ENCAR' AND platform_car_key = 'ENCAR_123456';

INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current, last_seen_at)
SELECT platform_car_id, price - 500000, DATE_SUB(NOW(), INTERVAL 7 DAY), 0, DATE_SUB(NOW(), INTERVAL 7 DAY)
FROM platform_car WHERE platform_name = 'ENCAR' AND platform_car_key = 'ENCAR_123456';

INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current, last_seen_at)
SELECT platform_car_id, price - 1000000, DATE_SUB(NOW(), INTERVAL 3 DAY), 0, DATE_SUB(NOW(), INTERVAL 3 DAY)
FROM platform_car WHERE platform_name = 'ENCAR' AND platform_car_key = 'ENCAR_123456';

INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current, last_seen_at)
SELECT platform_car_id, price, NOW(), 1, NOW()
FROM platform_car WHERE platform_name = 'ENCAR' AND platform_car_key = 'ENCAR_123456';

-- More price history for K5
INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current, last_seen_at)
SELECT platform_car_id, price + 1000000, DATE_SUB(NOW(), INTERVAL 5 DAY), 0, DATE_SUB(NOW(), INTERVAL 5 DAY)
FROM platform_car WHERE platform_name = 'ENCAR' AND platform_car_key = 'ENCAR_789012';

INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current, last_seen_at)
SELECT platform_car_id, price, NOW(), 1, NOW()
FROM platform_car WHERE platform_name = 'ENCAR' AND platform_car_key = 'ENCAR_789012';

-- Price history for Grandeur on KCAR
INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current, last_seen_at)
SELECT platform_car_id, price + 500000, DATE_SUB(NOW(), INTERVAL 2 DAY), 0, DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM platform_car WHERE platform_name = 'KCAR' AND platform_car_key = 'KCAR_DEF456';

INSERT INTO car_price_history (platform_car_id, price, checked_at, is_current, last_seen_at)
SELECT platform_car_id, price, NOW(), 1, NOW()
FROM platform_car WHERE platform_name = 'KCAR' AND platform_car_key = 'KCAR_DEF456';

-- Platform code mappings
INSERT INTO platform_code_mapping (platform_name, level, raw_code, raw_name, standard_code, confidence)
VALUES
('ENCAR', 'MAKER', 'HYN', 'HYUNDAI', 'HYN', 1.000),
('ENCAR', 'MAKER', 'KIA', 'KIA', 'KIA', 1.000),
('ENCAR', 'MODEL_GROUP', 'SONATA', 'SONATA', 'SONATA', 1.000),
('ENCAR', 'MODEL_GROUP', 'K5', 'K5', 'K5', 1.000),
('KCAR', 'MAKER', 'HYUNDAI', 'HYUNDAI', 'HYN', 0.980),
('KCAR', 'MAKER', 'KIA', 'KIA', 'KIA', 1.000),
('CHACHACHA', 'MAKER', 'HYN', 'HYUNDAI', 'HYN', 1.000);
