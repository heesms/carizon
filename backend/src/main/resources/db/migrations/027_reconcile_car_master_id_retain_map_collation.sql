-- live DB 기준: preserve map의 CAR_NO를 platform_car(CAR_NO)와 조인할 때
-- utf8mb4_0900_ai_ci vs utf8mb4_general_ci 충돌 방지
-- 기존에 0900으로 생성된 테이블을 일반 운영 환경과 일치시킴

ALTER TABLE car_master_id_retain_map
  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
