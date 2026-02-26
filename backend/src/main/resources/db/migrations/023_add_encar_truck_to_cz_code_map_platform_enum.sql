-- cz_code_map.platform_name ENUM에 ENCAR_TRUCK 추가
ALTER TABLE cz_code_map
MODIFY COLUMN platform_name ENUM(
  'ENCAR',
  'ENCAR_TRUCK',
  'KCAR',
  'CHACHACHA',
  'CHUTCHA',
  'CHARANCHA',
  'TCAR'
) NOT NULL COMMENT '플랫폼 이름';
