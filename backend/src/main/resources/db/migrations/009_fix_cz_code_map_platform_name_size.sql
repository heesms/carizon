-- cz_code_map 테이블의 platform_name ENUM에 TCAR, CHARANCHA 추가
-- 현재 ENUM('ENCAR', 'CHACHACHA', 'CHUTCHA', 'KCAR')에 TCAR와 CHARANCHA가 없어서 에러 발생

ALTER TABLE cz_code_map 
MODIFY COLUMN platform_name ENUM('ENCAR', 'KCAR', 'CHACHACHA', 'CHUTCHA', 'CHARANCHA', 'TCAR') NOT NULL 
COMMENT '플랫폼 이름';
