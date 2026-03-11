-- embed_text_3 기존 값 재작성 (기존 generic 문구 → 모델별 구체적 문구로 개선)

-- =====================
-- 카니발 / 대형 MPV
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '대가족 이동의 끝판왕. 9인 탑승+대형 트렁크까지, 카니발이기에 가능한 레벨.' WHERE model_code = '3109'; -- 카니발 4세대
UPDATE cz_model_embedding_source SET embed_text_3 = '카니발 4세대 최신형. 대가족 패밀리 MPV 수요에서 비교 대상이 없는 압도적 1위.' WHERE model_code = '3377'; -- 더 뉴 카니발 하이브리드 4세대
UPDATE cz_model_embedding_source SET embed_text_3 = '카니발 4세대 페이스리프트. 최신 감성으로 더 정제된 대형 패밀리 MPV.' WHERE model_code = '3376'; -- 더 뉴 카니발 4세대
UPDATE cz_model_embedding_source SET embed_text_3 = '카니발 3세대 후기형. 9인 MPV 수요에서 타 모델과 비교 자체가 무의미한 독보적 1위.' WHERE model_code = '2928'; -- 더 뉴 카니발
UPDATE cz_model_embedding_source SET embed_text_3 = '카니발 3세대. 완전변경으로 미니밴 이미지를 SUV 감성으로 탈바꿈시킨 세대.' WHERE model_code = '1497'; -- 올 뉴 카니발
UPDATE cz_model_embedding_source SET embed_text_3 = '카니발 2세대. 중고차 시장 대가족 수요의 가성비 선택지. 여전히 공간은 최강.' WHERE model_code = '1222'; -- 카니발 R
UPDATE cz_model_embedding_source SET embed_text_3 = '카니발 1세대. 대형 미니밴 수요에서 중고 가성비로 꾸준히 검색되는 원조.' WHERE model_code = '1219'; -- 그랜드카니발

-- =====================
-- 그랜저 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 7세대. 세단인지 SUV인지 경계를 흐리는 혁신 디자인으로 준대형 재정의.' WHERE model_code = '3301'; -- 디 올 뉴 그랜저
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 7세대 하이브리드. 혁신 디자인에 연비 효율까지 더한 준대형 완성형.' WHERE model_code = '3302'; -- 디 올 뉴 그랜저 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 6세대. 젊은 그랜저로 세대교체 성공, 준대형 세단 시장의 터닝포인트.' WHERE model_code = '2852'; -- 그랜저IG
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 6세대 페이스리프트. 숙성된 플랫폼에 마감감만 더 끌어올린 완성형.' WHERE model_code = '3048'; -- 더 뉴 그랜저
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 6세대 하이브리드. 국민 준대형 포지션에 효율 두 마리 토끼를 잡은 버전.' WHERE model_code = '2879'; -- 그랜저IG 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 6세대 하이브리드 페이스리프트. 준대형+연비를 동시에 원하는 수요의 정답.' WHERE model_code = '3049'; -- 더 뉴 그랜저 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 5세대. 아버지의 그랜저 시대를 상징하는 국민 준대형 세단의 아이콘.' WHERE model_code = '1411'; -- 그랜저HG
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 5세대 하이브리드. 준대형 세단 연비 수요에서 꾸준히 선택된 모델.' WHERE model_code = '1486'; -- 그랜저 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 4세대. 중고차 시장에서 올드 그랜저 감성과 소장 가치로 꾸준히 검색.' WHERE model_code = '1106'; -- 더 럭셔리 그랜저
UPDATE cz_model_embedding_source SET embed_text_3 = '그랜저 뉴 럭셔리. 준대형 세단 중고 수요에서 클래식 그랜저 감성으로 등장.' WHERE model_code = '1412'; -- 그랜저 뉴 럭셔리

-- =====================
-- 쏘나타 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘나타 DN8. 국산 중형 세단의 기준점. 패밀리·출퇴근 수요 모두에 무난한 정답.' WHERE model_code = '2994'; -- 쏘나타 (DN8)
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘나타 DN8 하이브리드. 중형 세단 연비 수요에서 가장 먼저 거론되는 모델.' WHERE model_code = '3021'; -- 쏘나타 하이브리드 (DN8)
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘나타 DN8 후기형. 파격적 디자인으로 중형 세단 이미지를 통째로 쇄신한 버전.' WHERE model_code = '3330'; -- 쏘나타 디 엣지(DN8)
UPDATE cz_model_embedding_source SET embed_text_3 = 'LF쏘나타. 국산 중형 세단 시장에서 오랫동안 기준점 역할을 해온 7세대.' WHERE model_code = '2504'; -- LF쏘나타
UPDATE cz_model_embedding_source SET embed_text_3 = 'LF쏘나타 하이브리드. 중형 세단 연비 수요의 선택지로 꾸준히 이름이 오르는 모델.' WHERE model_code = '2533'; -- LF쏘나타 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘나타 뉴 라이즈. 중형 세단 수요에서 무난하고 안정적인 7세대 페이스리프트.' WHERE model_code = '2874'; -- 쏘나타 뉴 라이즈
UPDATE cz_model_embedding_source SET embed_text_3 = 'YF쏘나타. 유체역학 디자인으로 중형 세단에 신선함을 불어넣은 6세대.' WHERE model_code = '1123'; -- YF쏘나타
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘나타 더 브릴리언트. 중형 세단 시장의 터줏대감으로 꾸준한 중고 수요.' WHERE model_code = '1447'; -- 쏘나타 더 브릴리언트
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘나타 하이브리드. 중형 세단+연비 수요를 동시에 충족하는 선택지.' WHERE model_code = '1415'; -- 쏘나타 하이브리드

-- =====================
-- K5 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 3세대. 유려한 패스트백 디자인으로 국산 중형 세단 판도를 바꾼 모델.' WHERE model_code = '3051'; -- K5 3세대
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 3세대 하이브리드. 디자인+연비를 동시에 원하는 중형 세단 수요에 최적.' WHERE model_code = '3052'; -- K5 하이브리드 3세대
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 3세대 페이스리프트. 완성된 디자인의 중형 세단 최신형으로 꾸준한 선택.' WHERE model_code = '3374'; -- 더 뉴 K5 3세대
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 2세대. 유럽 감성 디자인으로 국산 중형 세단 시장에 새 바람을 불어넣은 모델.' WHERE model_code = '2683'; -- K5 2세대
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 2세대 하이브리드. 디자인+연비 균형을 원하는 중형 세단 수요에 적합.' WHERE model_code = '2793'; -- K5 하이브리드 2세대
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 2세대 페이스리프트. 중형 세단 디자인 감성 수요에서 꾸준한 선택지.' WHERE model_code = '2924'; -- 더 뉴 K5 2세대
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 1세대. 국산 중형 세단 디자인 혁명의 원조. 중고 가성비로 꾸준히 검색.' WHERE model_code = '1262'; -- K5
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 하이브리드 1세대. 중형 세단 연비 수요의 초기 선택지로 꾸준히 등장.' WHERE model_code = '1405'; -- K5 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = 'K5 더 뉴. 중형 세단 디자인 수요에서 꾸준히 비교되는 페이스리프트.' WHERE model_code = '1461'; -- 더 뉴 K5

-- =====================
-- 아반떼 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '아반떼 CN7. 세련된 디자인으로 준중형 세단의 이미지를 통째로 바꾼 7세대.' WHERE model_code = '3079'; -- 올 뉴 아반떼(CN7)
UPDATE cz_model_embedding_source SET embed_text_3 = '아반떼 N. 국산 핫해치의 새 장을 쓴 280마력 고성능 준중형의 정점.' WHERE model_code = '3209'; -- 올 뉴 아반떼 N (CN7)
UPDATE cz_model_embedding_source SET embed_text_3 = '아반떼 CN7 페이스리프트. 완성된 준중형 펀카 포지션의 최신형.' WHERE model_code = '3317'; -- 더 뉴 아반떼 (CN7)
UPDATE cz_model_embedding_source SET embed_text_3 = '아반떼 CN7 하이브리드. 준중형 세단 연비+재미를 동시에 원하는 수요에 적합.' WHERE model_code = '3120'; -- 올 뉴 아반떼 하이브리드(CN7)
UPDATE cz_model_embedding_source SET embed_text_3 = '아반떼 AD. 준중형 세단의 교과서. 첫차·출퇴근 수요에서 꾸준한 인기를 가진 6세대.' WHERE model_code = '2729'; -- 아반떼AD
UPDATE cz_model_embedding_source SET embed_text_3 = '아반떼 AD 페이스리프트. 준중형 첫차·출퇴근 수요의 안정적인 선택지.' WHERE model_code = '2957'; -- 더 뉴 아반떼 AD
UPDATE cz_model_embedding_source SET embed_text_3 = '아반떼 5세대 페이스리프트. 준중형 세단 첫차 수요에서 꾸준히 선택받는 모델.' WHERE model_code = '1459'; -- 더 뉴 아반떼
UPDATE cz_model_embedding_source SET embed_text_3 = '아반떼 XD. 중고차 시장에서 꾸준히 검색되는 가성비 실용 준중형 세단.' WHERE model_code = '1127'; -- 아반떼XD

-- =====================
-- 쏘렌토 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘렌토 4세대. 7인 SUV 시장에서 카니발 다음 후보로 가장 먼저 거론되는 패밀리 SUV.' WHERE model_code = '3073'; -- 쏘렌토 4세대
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘렌토 4세대 하이브리드. 연비와 7인 공간을 동시에 원하는 실용적 패밀리 SUV.' WHERE model_code = '3080'; -- 쏘렌토 4세대 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘렌토 4세대 페이스리프트. 7인 패밀리 SUV 수요의 현재 진행형 선택지.' WHERE model_code = '3356'; -- 더 뉴 쏘렌토 MQ4
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘렌토 4세대 하이브리드 페이스리프트. 연비 좋은 7인 SUV의 최신형.' WHERE model_code = '3357'; -- 더 뉴 쏘렌토 하이브리드 MQ4
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘렌토 3세대 페이스리프트. 7인 SUV 수요에서 카니발 대안으로 자주 언급.' WHERE model_code = '2898'; -- 더 뉴 쏘렌토
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘렌토 3세대. 완전변경으로 7인 패밀리 SUV 시장의 판도를 바꾼 세대.' WHERE model_code = '2506'; -- 올 뉴쏘렌토
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘렌토 2세대. 7인 패밀리 SUV 수요에서 가성비 중고 선택지로 꾸준히 등장.' WHERE model_code = '1217'; -- 쏘렌토R
UPDATE cz_model_embedding_source SET embed_text_3 = '쏘렌토 2세대 페이스리프트. 가성비 7인 SUV 중고 수요에서 꾸준히 검색.' WHERE model_code = '1446'; -- 뉴쏘렌토R

-- =====================
-- 스포티지 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '스포티지 5세대. 국산 준중형 SUV의 디자인+실용성 균형을 완성한 현재 대표주자.' WHERE model_code = '3194'; -- 디 올 뉴 스포티지
UPDATE cz_model_embedding_source SET embed_text_3 = '스포티지 5세대 하이브리드. 연비+공간을 동시에 원하는 패밀리 SUV 수요에 최적.' WHERE model_code = '3197'; -- 디 올 뉴 스포티지 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '스포티지 4세대. 도심+주말 모두 무난한 5인 패밀리 SUV의 안정적인 선택지.' WHERE model_code = '2791'; -- 스포티지 4세대
UPDATE cz_model_embedding_source SET embed_text_3 = '스포티지 4세대 페이스리프트. 도심형 패밀리 SUV의 현대적 선택지.' WHERE model_code = '2944'; -- 스포티지 더 볼드
UPDATE cz_model_embedding_source SET embed_text_3 = '스포티지 3세대 페이스리프트. 준중형 SUV 가성비 수요에서 꾸준히 비교되는 모델.' WHERE model_code = '1462'; -- 더 뉴 스포티지 R
UPDATE cz_model_embedding_source SET embed_text_3 = '스포티지 3세대. 준중형 SUV 시장에서 오랫동안 중고 가성비 대표주자.' WHERE model_code = '1214'; -- 스포티지 R
UPDATE cz_model_embedding_source SET embed_text_3 = '스포티지 2세대. 준중형 SUV 수요에서 중고 가성비로 꾸준히 이름이 오르는 모델.' WHERE model_code = '1213'; -- 뉴스포티지

-- =====================
-- 팰리세이드 / 대형 SUV
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '팰리세이드. 8인 탑승+대형 트렁크, 국산 대형 SUV 패밀리카의 끝판왕.' WHERE model_code = '2971'; -- 팰리세이드
UPDATE cz_model_embedding_source SET embed_text_3 = '팰리세이드 페이스리프트. 대형 패밀리 SUV의 완성형. 국산 대형 SUV 1위.' WHERE model_code = '3265'; -- 더 뉴 팰리세이드
UPDATE cz_model_embedding_source SET embed_text_3 = '팰리세이드 하이브리드. 대형 SUV에 연비까지 더한 국산 대형 패밀리카.' WHERE model_code = '3435'; -- 디 올 뉴 팰리세이드 하이브리드

-- =====================
-- GV80 / GV70 (제네시스 SUV)
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = 'GV80. 국산 프리미엄 대형 SUV의 정점. 수입 대형 SUV와 직접 경쟁하는 제네시스 대표.' WHERE model_code = '3055'; -- GV80
UPDATE cz_model_embedding_source SET embed_text_3 = 'GV70. 제네시스 중형 SUV. 국산 프리미엄 SUV 수요에서 GV80과 함께 양강 구도.' WHERE model_code = '3154'; -- GV70

-- =====================
-- G80 / G90 (제네시스 세단)
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = 'G80 3세대. 국산 프리미엄 세단의 현재 대표주자. 수입 E세그먼트와 직접 비교.' WHERE model_code = '3077'; -- G80(RG3)
UPDATE cz_model_embedding_source SET embed_text_3 = 'G80 2세대. 제네시스 브랜드 독립 초기의 주역. 프리미엄 세단 수요의 입문점.' WHERE model_code = '2849'; -- G80
UPDATE cz_model_embedding_source SET embed_text_3 = 'K8. 그랜저와 같은 포지션이지만 더 유려한 디자인으로 감성파 준대형 수요 공략.' WHERE model_code = '3167'; -- K8
UPDATE cz_model_embedding_source SET embed_text_3 = 'K8 하이브리드. 준대형 세단+하이브리드 연비를 동시에 원하는 수요에 최적.' WHERE model_code = '3180'; -- K8 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '더 뉴 K8. 감성 준대형 세단의 최신형. K8 수요에서 꾸준히 선택받는 버전.' WHERE model_code = '3419'; -- 더 뉴 K8

-- =====================
-- 경차 (모닝/레이)
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '모닝 4세대. 경차 시장의 현대적 표준. 도심 세컨카·초보운전 수요의 기본 선택지.' WHERE model_code = '2870'; -- 올 뉴 모닝(JA)
UPDATE cz_model_embedding_source SET embed_text_3 = '모닝 4세대 페이스리프트. 경차 수요에서 안정적으로 선택받는 현재 진행형.' WHERE model_code = '3346'; -- 더 뉴 모닝(JA)
UPDATE cz_model_embedding_source SET embed_text_3 = '모닝 어반. 경차 도심 수요에서 실용+스타일을 겸비한 사양.' WHERE model_code = '3086'; -- 모닝 어반(JA)
UPDATE cz_model_embedding_source SET embed_text_3 = '모닝 3세대. 경차 세컨카·초보운전 수요에서 오랫동안 선택받아 온 모델.' WHERE model_code = '1409'; -- all new 모닝
UPDATE cz_model_embedding_source SET embed_text_3 = '모닝 3세대 페이스리프트. 경차 도심 수요에서 검증된 선택지.' WHERE model_code = '2554'; -- 더 뉴 모닝
UPDATE cz_model_embedding_source SET embed_text_3 = '모닝밴 4세대 페이스리프트. 소형 상업용 경차 수요의 실용적 선택지.' WHERE model_code = '2555'; -- 더 뉴 모닝밴
UPDATE cz_model_embedding_source SET embed_text_3 = '모닝밴 4세대. 경차 적재 수요에서 도심 배달·영업용으로 꾸준히 선택.' WHERE model_code = '2872'; -- 올 뉴 모닝밴(JA)
UPDATE cz_model_embedding_source SET embed_text_3 = '레이. 박스형 차체로 경차 최강 실내공간을 실현. 작은차 신화의 주인공.' WHERE model_code = '1429'; -- 레이
UPDATE cz_model_embedding_source SET embed_text_3 = '레이 2세대 페이스리프트. 박스카 최강 공간에 현대적 감성을 더한 버전.' WHERE model_code = '2945'; -- 더 뉴 레이
UPDATE cz_model_embedding_source SET embed_text_3 = '레이 2세대 후기형. 박스카 최강 공간의 최신형. 가족·반려동물 수요에 최적.' WHERE model_code = '3306'; -- 더 뉴 기아 레이
UPDATE cz_model_embedding_source SET embed_text_3 = '레이 밴. 박스형 경차 최강 적재 효율. 영업용·반려동물 수요에 꾸준히 선택.' WHERE model_code = '2946'; -- 더 뉴 레이 밴
UPDATE cz_model_embedding_source SET embed_text_3 = '레이 밴 1세대. 경차 최강 공간을 짐칸으로 활용하는 소형 영업용 수요의 선택.' WHERE model_code = '1444'; -- 레이밴
UPDATE cz_model_embedding_source SET embed_text_3 = '레이 밴 2세대 후기형. 박스형 경차 최강 공간과 실용성의 현재진행형.' WHERE model_code = '3307'; -- 더 뉴 기아 레이 밴

-- =====================
-- 투싼 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '투싼 3세대. 준중형 SUV 시장에서 가족·커플 모두에게 어필하는 균형잡힌 선택지.' WHERE model_code = '2539'; -- 올 뉴 투싼
UPDATE cz_model_embedding_source SET embed_text_3 = '투싼 2세대 페이스리프트. 실용적 준중형 SUV 수요에서 오랜 선택지.' WHERE model_code = '1460'; -- 뉴 투싼 ix
UPDATE cz_model_embedding_source SET embed_text_3 = '투싼 2세대. 준중형 SUV 실용 수요의 오랜 터줏대감.' WHERE model_code = '1171'; -- 투싼ix
UPDATE cz_model_embedding_source SET embed_text_3 = '투싼 1세대. 국내 SUV 붐을 이끈 준중형 SUV의 원조 세대.' WHERE model_code = '1170'; -- 투싼

-- =====================
-- SM6 / QM6 (르노코리아)
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM6. 유럽 감성 서스펜션과 디자인으로 국산 중형 세단에서 차별점을 만든 모델.' WHERE model_code = '2769'; -- SM6
UPDATE cz_model_embedding_source SET embed_text_3 = 'QM6. 넉넉한 실내+합리적 가격으로 패밀리 SUV 가성비 수요를 공략한 모델.' WHERE model_code = '2837'; -- QM6
UPDATE cz_model_embedding_source SET embed_text_3 = 'QM6 페이스리프트. 패밀리 SUV 가성비 수요에서 꾸준히 선택받는 버전.' WHERE model_code = '3155'; -- 더 뉴 QM6
UPDATE cz_model_embedding_source SET embed_text_3 = 'QM6 최신형. 패밀리 SUV 가성비 수요의 현재 선택지.' WHERE model_code = '3156'; -- 뉴 QM6

-- =====================
-- 티볼리 (KGM/쌍용)
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '티볼리 1세대. 국내 소형 SUV 시장 개척자. 첫 SUV 도심 수요에서 오랜 선택지.' WHERE model_code = '2536'; -- 티볼리
UPDATE cz_model_embedding_source SET embed_text_3 = '티볼리 2세대. 소형 SUV 첫차·도심 수요에서 꾸준히 거론되는 국산 대표.' WHERE model_code = '3013'; -- 베리 뉴 티볼리
UPDATE cz_model_embedding_source SET embed_text_3 = '티볼리 아머(롱바디). 더 큰 트렁크가 필요한 소형 SUV 수요에 적합한 롱 버전.' WHERE model_code = '2897'; -- 티볼리 아머
UPDATE cz_model_embedding_source SET embed_text_3 = '티볼리 에어. 티볼리보다 넓은 공간이 필요한 소형 SUV 수요의 선택지.' WHERE model_code = '2781'; -- 티볼리 에어
UPDATE cz_model_embedding_source SET embed_text_3 = '티볼리 에어 롱바디. 소형 SUV 중 넉넉한 적재공간을 원하는 수요에 최적.' WHERE model_code = '3134'; -- 티볼리 에어(X150)

-- =====================
-- 렉스턴 계열 (KGM/쌍용)
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '렉스턴 스포츠. 국내 픽업트럭 시장 사실상 유일한 선택지. 견인·적재 수요의 대명사.' WHERE model_code = '2913'; -- 렉스턴 스포츠
UPDATE cz_model_embedding_source SET embed_text_3 = '렉스턴 스포츠 칸. 더 큰 적재함이 필요한 픽업 수요의 국내 유일 선택지.' WHERE model_code = '2978'; -- 렉스턴 스포츠 칸
UPDATE cz_model_embedding_source SET embed_text_3 = '렉스턴 스포츠 칸 페이스리프트. 국내 픽업트럭 적재 수요에서 가장 최신 버전.' WHERE model_code = '3183'; -- 더 뉴 렉스턴 스포츠 칸
UPDATE cz_model_embedding_source SET embed_text_3 = '렉스턴 스포츠 페이스리프트. 국내 픽업트럭 수요에서 꾸준히 선택받는 버전.' WHERE model_code = '3182'; -- 더 뉴 렉스턴 스포츠
UPDATE cz_model_embedding_source SET embed_text_3 = 'G4 렉스턴. 정통 프레임바디 대형 SUV. 견인·오프로드 수요에서 독보적 포지션.' WHERE model_code = '2882'; -- G4 렉스턴
UPDATE cz_model_embedding_source SET embed_text_3 = '올 뉴 렉스턴. 정통 프레임바디 대형 SUV를 원하는 견인·오프로드 수요의 선택.' WHERE model_code = '3141'; -- 올 뉴 렉스턴

-- =====================
-- 니로 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '니로 1세대. 하이브리드 소형 SUV의 선구자. 연비 수요에서 가장 먼저 떠오르는 이름.' WHERE model_code = '2785'; -- 니로
UPDATE cz_model_embedding_source SET embed_text_3 = '니로 EV. 소형 전기 SUV 수요에서 가성비·실용성으로 꾸준히 언급되는 모델.' WHERE model_code = '2948'; -- 니로 EV
UPDATE cz_model_embedding_source SET embed_text_3 = '니로 1세대 페이스리프트. 하이브리드 소형 SUV 연비 수요의 검증된 선택지.' WHERE model_code = '3024'; -- 더 뉴 니로
UPDATE cz_model_embedding_source SET embed_text_3 = '니로 2세대. 하이브리드·PHEV·EV 모두 선택 가능한 친환경 소형 SUV의 현재.' WHERE model_code = '3239'; -- 디 올 뉴 니로

-- =====================
-- 아이오닉 / EV 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '아이오닉5. E-GMP 기반 패밀리 EV의 기준. 공간·디자인·초급속 충전 3박자.' WHERE model_code = '3174'; -- 아이오닉5
UPDATE cz_model_embedding_source SET embed_text_3 = 'EV6. 800V 초급속 충전+GT 옵션으로 전기차에서도 드라이빙 재미를 원하는 수요 공략.' WHERE model_code = '3218'; -- EV6

-- =====================
-- BMW 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 5시리즈 G30. 수입 비즈니스 세단 시장에서 E클래스와 1위를 다투는 현세대.' WHERE model_code = '2865'; -- 올뉴5시리즈 (G30)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 5시리즈 G60. 수입 준대형 비즈니스 세단의 최신 세대. 플러그인 하이브리드 기본.' WHERE model_code = '3372'; -- 뉴5시리즈(G60)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 5시리즈 F10. 수입 비즈니스 세단 시장에서 오랫동안 사랑받은 6세대.' WHERE model_code = '1509'; -- 뉴5시리즈
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 3시리즈 G20. 수입 준중형 스포츠 세단의 왕좌. 후륜구동 감성을 원하는 수요의 정답.' WHERE model_code = '2989'; -- 3시리즈 (G20)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 3시리즈 F30. 수입 준중형 세단 수요에서 C클래스·A4와 함께 3강 구도.' WHERE model_code = '1508'; -- 뉴3시리즈
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 7시리즈 G11. 수입 플래그십 세단. 최고급 수요에서 S클래스와 양강 구도.' WHERE model_code = '2847'; -- 7시리즈 (G11)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 7시리즈 G70. 전기+내연기관 모두 선택 가능한 최신 플래그십 세단.' WHERE model_code = '3314'; -- 7시리즈(G70)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 7시리즈 E65. 수입 플래그십 세단 중고 수요에서 클래식 감성으로 등장.' WHERE model_code = '1506'; -- 7시리즈
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 7시리즈 F01. 수입 플래그십 세단 중고 수요에서 꾸준히 검색.' WHERE model_code = '1510'; -- 뉴7시리즈
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW X5 G05. 수입 프리미엄 대형 SUV 1위. GLE·GV80과 함께 3강 구도의 중심.' WHERE model_code = '2979'; -- X5 (G05)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW X3 G01. 수입 준중형 프리미엄 SUV의 대표. GLC·Q5와 함께 3강 구도.' WHERE model_code = '2910'; -- X3 (G01)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW X4 F26. 쿠페형 준중형 SUV 수요에서 개성파 선택지.' WHERE model_code = '2538'; -- X4 (F26)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW X4 G02. 쿠페형 준중형 SUV 수요에서 GLC 쿠페와 비교되는 최신형.' WHERE model_code = '2997'; -- New X4 (G02)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW X6 G06. 스포츠 쿠페형 대형 SUV 수요에서 GLE 쿠페와 비교.' WHERE model_code = '3096'; -- X6(G06)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW X7 G07. 수입 대형 7인 SUV. 최고급 패밀리 SUV 수요에서 GLS와 비교.' WHERE model_code = '2993'; -- X7(G07)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW X1 F48. 수입 소형 프리미엄 SUV 수요에서 입문점 역할을 하는 모델.' WHERE model_code = '2896'; -- X1 (F48)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 4시리즈 F32. 수입 쿠페 감성 수요에서 개성과 주행감을 원하는 선택지.' WHERE model_code = '2511'; -- 4시리즈 (F32)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 4시리즈 G22. 수입 쿠페 수요에서 현세대 선택지. 그란쿠페도 함께 비교.' WHERE model_code = '3160'; -- 4시리즈 (G22)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 1시리즈 F40. 수입 소형 해치백 프리미엄 수요에서 A클래스와 비교.' WHERE model_code = '3149'; -- 1시리즈 (F40)
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW 1시리즈 F20. 수입 소형 프리미엄 해치백 수요에서 꾸준히 비교되는 모델.' WHERE model_code = '2144'; -- 뉴1시리즈
UPDATE cz_model_embedding_source SET embed_text_3 = 'BMW M시리즈. 고성능 수입차 수요에서 포르쉐·AMG와 함께 최상위 3강 구도.' WHERE model_code = '1992'; -- M시리즈

-- =====================
-- 메르세데스-벤츠 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 E클래스 W213. 수입 중형-준대형 세단의 가장 대중적인 선택지. 꾸준한 1위.' WHERE model_code = '2806'; -- E클래스 (W213)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 E클래스 W214. E클래스 최신 세대. 수입 비즈니스 세단 수요의 현재 기준.' WHERE model_code = '3387'; -- E-클래스 (W214)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 E클래스 W212. 수입 중형 세단 중고 수요에서 가성비 프리미엄 선택지.' WHERE model_code = '1523'; -- 뉴 E클래스
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 C클래스 W205 페이스리프트. 수입 준중형 세단 수요에서 3시리즈·A4와 비교.' WHERE model_code = '2522'; -- 더 뉴 C클래스
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 C클래스 W206. 수입 준중형 세단 현세대. G20·B9와 함께 3강 구도.' WHERE model_code = '3257'; -- C클래스 6세대(W206)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 S클래스 W222. 수입 플래그십 세단의 상징. 최고급 세단 수요의 기준.' WHERE model_code = '1524'; -- 뉴 S클래스
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 S클래스 W223. 세계 최고급 세단의 최신 기준. 기술과 럭셔리의 집합체.' WHERE model_code = '3163'; -- S클래스 W223
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 GLE V167. 수입 중대형 프리미엄 SUV에서 X5와 함께 최고 인기 모델.' WHERE model_code = '3095'; -- GLE (V167)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 GLE W166. 수입 프리미엄 중대형 SUV 수요에서 X5와 양강을 다투던 모델.' WHERE model_code = '2760'; -- GLE(W166)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 GLS X167. 수입 대형 7인 프리미엄 SUV 수요에서 X7과 비교.' WHERE model_code = '3093'; -- GLS (X167)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 GLC X253. 수입 준중형 프리미엄 SUV 수요에서 X3·Q5와 3강 구도.' WHERE model_code = '2759'; -- GLC(X253)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 GLC X254. GLC 최신 세대. 수입 준중형 SUV 3강 구도의 현재 버전.' WHERE model_code = '3349'; -- GLC(X254)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 GLB X247. 7인 컴팩트 SUV 수요에서 독보적 포지션을 차지한 모델.' WHERE model_code = '3123'; -- GLB(X247)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 GLA X156. 수입 소형 프리미엄 SUV 수요에서 입문점 역할을 하는 모델.' WHERE model_code = '2507'; -- GLA X156
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 A클래스 W177. 수입 소형 프리미엄 해치백의 대표. 1시리즈·A3와 비교.' WHERE model_code = '3041'; -- A클래스(W177)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 CLA W118. 4도어 쿠페 감성의 소형 프리미엄. 스타일 중심 수요의 선택지.' WHERE model_code = '3076'; -- CLA클래스(C118)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 CLA C117. 4도어 쿠페형 소형 프리미엄 세단 수요에서 꾸준한 선택지.' WHERE model_code = '2512'; -- CLA클래스
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 CLS W218. 4도어 쿠페 세그먼트의 원조. 스타일+프리미엄 수요의 아이콘.' WHERE model_code = '1984'; -- CLS클래스(W218)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 CLS C257. 4도어 쿠페 세그먼트의 현대적 버전. 스타일·주행감 모두 원하는 수요.' WHERE model_code = '2975'; -- CLS클래스(C257)
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 AMG GT. 수입 고성능 GT 스포츠카 수요에서 상징적인 이름.' WHERE model_code = '2730'; -- AMG GT
UPDATE cz_model_embedding_source SET embed_text_3 = '벤츠 CLE C236. 수입 쿠페 감성+준대형 수요에서 새롭게 등장한 포지션.' WHERE model_code = '3390'; -- CLE클래스 (C236)

-- =====================
-- 아우디 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A6 C8. 수입 비즈니스 세단에서 E클래스·5시리즈와 3강 구도를 형성.' WHERE model_code = '3038'; -- A6(C8)
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A6 C7. 수입 비즈니스 세단 중고 수요에서 꾸준히 검색되는 모델.' WHERE model_code = '1554'; -- NEW A6
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A7. 4도어 쿠페 세그먼트에서 CLS와 함께 가장 자주 비교되는 모델.' WHERE model_code = '2033'; -- A7
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A7 4K8. 4도어 쿠페 수요에서 현세대 아우디 스타일의 정점.' WHERE model_code = '3075'; -- A7(4K8)
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A5 8W6. 수입 준중형 쿠페·스포트백 감성 수요에서 꾸준한 선택지.' WHERE model_code = '3094'; -- A5(8W6)
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A5 B8. 수입 쿠페 감성 수요에서 오랫동안 개성파 선택지로 자리잡은 모델.' WHERE model_code = '2142'; -- NEW A5
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A4 B9. 수입 준중형 세단 수요에서 3시리즈·C클래스와 3강 구도.' WHERE model_code = '2982'; -- A4 (B9)
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A4 B8. 수입 준중형 세단 중고 수요에서 꾸준히 검색되는 모델.' WHERE model_code = '1553'; -- NEW A4
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 Q5 FY 페이스리프트. 수입 준중형 SUV 3강 구도에서 꾸준한 선택지.' WHERE model_code = '3089'; -- The New Q5(FY)
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 Q5 8R. 수입 준중형 SUV 수요에서 X3·GLC와 함께 3강 구도의 원조.' WHERE model_code = '1565'; -- Q5
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 SQ5. Q5 고성능 버전. 수입 준중형 스포츠 SUV 수요에서 독보적 포지션.' WHERE model_code = '3116'; -- SQ5(FY)
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 Q8. 수입 대형 프리미엄 쿠페 SUV 수요에서 X6·GLE 쿠페와 비교.' WHERE model_code = '3078'; -- Q8
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 Q7 4M. 수입 대형 7인 SUV 수요에서 X7·GLS와 함께 비교되는 모델.' WHERE model_code = '2777'; -- 뉴 Q7
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 RS6 C8. 수입 고성능 왜건 수요에서 독보적 포지션을 차지한 아이콘.' WHERE model_code = '3201'; -- RS6(C8)
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 A3. 수입 소형 프리미엄 해치백 수요에서 A클래스·1시리즈와 비교.' WHERE model_code = '1552'; -- NEW A3
UPDATE cz_model_embedding_source SET embed_text_3 = '아우디 i3. 수입 소형 순수 전기차 중 BMW 감성을 원하는 수요의 선택지.' WHERE model_code = '2644'; -- i3

-- =====================
-- 포르쉐 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '포르쉐 718 박스터. 미드십 로드스터의 순수한 드라이빙 감성을 원하는 수요의 선택.' WHERE model_code = '2784'; -- 718 박스터
UPDATE cz_model_embedding_source SET embed_text_3 = '포르쉐 파나메라 971. 스포츠카+세단의 경계를 허문 럭셔리 4도어의 최신형.' WHERE model_code = '2906'; -- 파나메라 (971)

-- =====================
-- 마세라티 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '마세라티 그란투리스모. 이탈리아 럭셔리 GT 수요에서 상징적인 2도어 쿠페.' WHERE model_code = '2034'; -- 그란투리스모
UPDATE cz_model_embedding_source SET embed_text_3 = '마세라티 기블리. 가장 접근하기 쉬운 마세라티. 이탈리안 럭셔리 세단 입문점.' WHERE model_code = '2253'; -- 기블리
UPDATE cz_model_embedding_source SET embed_text_3 = '마세라티 콰트로포르테. 이탈리안 럭셔리 대형 세단의 정점. 희소 수요에서 독보적.' WHERE model_code = '2130'; -- 콰트로포르테
UPDATE cz_model_embedding_source SET embed_text_3 = '마세라티 그레칼레. 마세라티 최초의 컴팩트 SUV. 수입 럭셔리 SUV 입문 수요 공략.' WHERE model_code = '3322'; -- 그레칼레(M182)
UPDATE cz_model_embedding_source SET embed_text_3 = '마세라티 르반떼. 이탈리아 럭셔리 SUV 수요에서 독보적 존재감을 지닌 모델.' WHERE model_code = '2827'; -- 르반떼

-- =====================
-- 랜드로버 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '랜드로버 디스커버리 스포츠. 수입 컴팩트 SUV 중 오프로드 감성과 공간을 겸비.' WHERE model_code = '2665'; -- 디스커버리 스포츠
UPDATE cz_model_embedding_source SET embed_text_3 = '랜드로버 디스커버리 5세대. 7인 오프로드+럭셔리 SUV 수요에서 독보적 포지션.' WHERE model_code = '2883'; -- 디스커버리5
UPDATE cz_model_embedding_source SET embed_text_3 = '랜드로버 디스커버리 4세대. 클래식 오프로드 SUV 수요에서 가성비 입문점.' WHERE model_code = '2007'; -- 디스커버리4
UPDATE cz_model_embedding_source SET embed_text_3 = '레인지로버 이보크 1세대. 수입 소형 럭셔리 SUV 수요에서 독보적 디자인 아이콘.' WHERE model_code = '2133'; -- 레인지로버 이보크
UPDATE cz_model_embedding_source SET embed_text_3 = '레인지로버 4세대. 오프로드 럭셔리 대형 SUV의 왕좌. 수입 대형 SUV 최고급 수요.' WHERE model_code = '2143'; -- 뉴 레인지로버
UPDATE cz_model_embedding_source SET embed_text_3 = '레인지로버 스포츠 2세대. 오프로드 감성+스포티한 스타일의 럭셔리 SUV.' WHERE model_code = '2579'; -- 뉴 레인지로버 스포츠
UPDATE cz_model_embedding_source SET embed_text_3 = '랜드로버 디펜더 L663. 정통 오프로드 아이콘의 현대적 재해석. 탐험가 수요의 상징.' WHERE model_code = '3119'; -- 디펜더(L663)

-- =====================
-- 재규어 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '재규어 E-PACE. 수입 소형 프리미엄 SUV 중 브리티시 스포티 감성을 원하는 수요.' WHERE model_code = '2923'; -- E-PACE
UPDATE cz_model_embedding_source SET embed_text_3 = '재규어 F-TYPE. 브리티시 스포츠카의 상징. 고성능 수입 2도어 수요에서 독보적 감성.' WHERE model_code = '2550'; -- F-TYPE
UPDATE cz_model_embedding_source SET embed_text_3 = '재규어 XF 2세대. 수입 준대형 세단 중 브리티시 감성을 원하는 수요의 선택지.' WHERE model_code = '2141'; -- New XF

-- =====================
-- 벤틀리
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '벤틀리 컨티넨탈 GT. 수입 럭셔리 그란투리스모 최상위. 희소한 최고급 수요.' WHERE model_code = '1879'; -- 컨티넨탈-GT

-- =====================
-- 지프 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '지프 그랜드 체로키. 수입 미국산 대형 SUV 수요에서 독보적 아이덴티티.' WHERE model_code = '2096'; -- 그랜드 체로키
UPDATE cz_model_embedding_source SET embed_text_3 = '지프 랭글러 JK. 오프로드·캠핑 수요에서 아이코닉한 존재. 타협 없는 순수 오프로더.' WHERE model_code = '2095'; -- 랭글러
UPDATE cz_model_embedding_source SET embed_text_3 = '지프 랭글러 JL. 오프로드·캠핑 수요의 아이콘. 전통적인 랭글러 감성의 최신형.' WHERE model_code = '2980'; -- 랭글러 (JL)
UPDATE cz_model_embedding_source SET embed_text_3 = '지프 컴패스 2세대. 수입 소형 SUV 중 미국 감성을 원하는 수요에 가장 적합.' WHERE model_code = '3006'; -- 올뉴 컴패스
UPDATE cz_model_embedding_source SET embed_text_3 = '지프 레니게이드. 수입 컴팩트 SUV 중 가장 개성 강한 오프로드 디자인 아이콘.' WHERE model_code = '2723'; -- 레니게이드

-- =====================
-- 볼보 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '볼보 S90 2세대. 스칸디나비아 감성 수입 준대형 세단. 안전+북유럽 고급감의 조합.' WHERE model_code = '2843'; -- S90 2세대
UPDATE cz_model_embedding_source SET embed_text_3 = '볼보 XC90 2세대. 수입 프리미엄 대형 SUV에서 안전+공간+북유럽 감성으로 차별화.' WHERE model_code = '2779'; -- 올 뉴 XC90
UPDATE cz_model_embedding_source SET embed_text_3 = '볼보 XC60 2세대. 수입 중형 SUV에서 안전+스칸디나비아 감성으로 확고한 입지.' WHERE model_code = '2947'; -- XC60 2세대
UPDATE cz_model_embedding_source SET embed_text_3 = '볼보 XC40. 수입 소형 프리미엄 SUV 중 스칸디나비아 안전 감성의 대표.' WHERE model_code = '2940'; -- XC40
UPDATE cz_model_embedding_source SET embed_text_3 = '볼보 EX30. 수입 소형 전기 SUV 신인. 컴팩트한 크기에 볼보 안전 철학 집약.' WHERE model_code = '3890'; -- EX30 CC

-- =====================
-- 렉서스 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '렉서스 ES 7세대. 렉서스 최대 볼륨 모델. 수입 세단 중 조용함과 승차감 최우선 수요.' WHERE model_code = '2951'; -- 뉴 제너레이션 ES
UPDATE cz_model_embedding_source SET embed_text_3 = '렉서스 RX 4세대. 렉서스 SUV 최강 볼륨. 수입 중형 SUV 편안함·신뢰성 수요의 대표.' WHERE model_code = '2241'; -- 뉴 RX
UPDATE cz_model_embedding_source SET embed_text_3 = '렉서스 UX. 렉서스 소형 SUV 입문점. 수입 소형 프리미엄 연비 수요에 적합.' WHERE model_code = '3004'; -- UX

-- =====================
-- 포드 / 링컨 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '포드 머스탱 6세대. 아메리칸 머슬카의 아이콘. 스포츠카 감성 수요에서 독보적 존재감.' WHERE model_code = '2662'; -- 올뉴머스탱
UPDATE cz_model_embedding_source SET embed_text_3 = '포드 익스플로러 6세대. 미국산 대형 7인 SUV. 캠핑·견인 수요에서 차별화된 포지션.' WHERE model_code = '3039'; -- 익스플로러(6세대)
UPDATE cz_model_embedding_source SET embed_text_3 = '포드 익스플로러 5세대. 미국산 대형 7인 SUV 중고 수요에서 꾸준히 검색.' WHERE model_code = '2306'; -- 뉴 익스플로러
UPDATE cz_model_embedding_source SET embed_text_3 = '링컨 노틸러스. 수입 프리미엄 중형 SUV 중 미국 럭셔리 감성을 원하는 수요 공략.' WHERE model_code = '3007'; -- 노틸러스(U540)
UPDATE cz_model_embedding_source SET embed_text_3 = '링컨 에비에이터. 수입 7인 프리미엄 SUV 수요에서 PHEV 옵션이 강점인 모델.' WHERE model_code = '3068'; -- 에비에이터 2세대(U611)

-- =====================
-- 캐딜락 / 인피니티
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '캐딜락 에스컬레이드 5세대. 미국산 최고급 대형 SUV. 존재감 자체가 아이덴티티인 모델.' WHERE model_code = '3190'; -- ESCALADE(5세대)
UPDATE cz_model_embedding_source SET embed_text_3 = '인피니티 G37. 후륜구동 수입 스포츠 세단. 국내 희소 수요에서 개성 선택지.' WHERE model_code = '1961'; -- G37

-- =====================
-- 쉐보레 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 말리부 8세대. 국산 중형 세단 수요에서 아메리칸 감성으로 차별화한 모델.' WHERE model_code = '2800'; -- 올 뉴 말리부
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 말리부 페이스리프트. 중형 세단 수요에서 아메리칸 감성의 합리적 선택지.' WHERE model_code = '2974'; -- 더 뉴 말리부
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 말리부 7세대. 국산 중형 세단 시장에서 아메리칸 감성으로 차별화한 구세대.' WHERE model_code = '1420'; -- 쉐보레 말리부
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 크루즈. 준중형 세단·해치백 수요에서 아메리칸 감성의 선택지.' WHERE model_code = '1406'; -- 쉐보레 크루즈
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 크루즈 2세대. 준중형 수요에서 글로벌 베스트셀러의 최신형.' WHERE model_code = '2868'; -- 올 뉴 크루즈
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 라세티 프리미어. 준중형 수요에서 크루즈 이전 세대의 가성비 중고 선택지.' WHERE model_code = '1300'; -- 라세티 프리미어
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 트랙스 크로스오버. 소형 SUV 수요에서 가성비+아메리칸 감성의 최신형.' WHERE model_code = '3319'; -- 트랙스 크로스오버
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 트레일블레이저. 소형 SUV 가성비 수요에서 아메리칸 감성으로 경쟁.' WHERE model_code = '3057'; -- 트레일블레이저
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 트랙스 1세대. 소형 SUV 가성비 수요에서 꾸준히 선택받는 모델.' WHERE model_code = '1453'; -- 쉐보레 트랙스
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 캡티바 2세대. 패밀리 SUV 가성비 수요에서 검색되는 미국 감성 모델.' WHERE model_code = '2786'; -- 신형 캡티바
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 캡티바 1세대. 중형 SUV 가성비 수요의 초기 선택지. 중고 가성비.' WHERE model_code = '1398'; -- 쉐보레 캡티바
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 임팔라. 미국산 대형 세단 감성을 원하는 희소 수요에서 독보적 아이덴티티.' WHERE model_code = '2682'; -- 임팔라
UPDATE cz_model_embedding_source SET embed_text_3 = '쉐보레 트래버스. 미국산 대형 7인 SUV 수요에서 합리적 가격대의 선택지.' WHERE model_code = '3029'; -- 트래버스

-- =====================
-- 르노코리아 (SM/QM)
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM6. 유럽 감성 서스펜션과 디자인으로 중형 세단에서 차별점을 만든 모델.' WHERE model_code = '2769'; -- SM6 (duplicate guard - already above)
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM5 뉴임프레션. 중형 세단 수요에서 부드러운 승차감으로 기억되는 르노삼성 모델.' WHERE model_code = '1340'; -- SM5 뉴임프레션
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM5 플래티넘. 중형 세단 수요에서 르노삼성 감성으로 꾸준히 선택받던 모델.' WHERE model_code = '1449'; -- 뉴SM5 플래티넘
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM5 노바. 중형 세단 수요에서 르노삼성 감성의 선택지로 검색되는 모델.' WHERE model_code = '2534'; -- SM5 노바
UPDATE cz_model_embedding_source SET embed_text_3 = 'All New SM7. 국내 준대형 세단 중 프렌치 감성을 원하는 수요의 선택지.' WHERE model_code = '1416'; -- All New SM7
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM7 노바. 준대형 세단 수요에서 희소한 프렌치 감성으로 중고 시장에서 등장.' WHERE model_code = '2520'; -- ALL New SM7 노바
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM7 뉴아트. 준대형 세단 수요에서 르노삼성 독특한 프랑스 감성의 구세대.' WHERE model_code = '1347'; -- SM7 뉴아트
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM3. 소형 세단 수요에서 감성·연비 균형의 르노삼성 선택지.' WHERE model_code = '1343'; -- 뉴SM3
UPDATE cz_model_embedding_source SET embed_text_3 = 'SM3 네오. 소형 세단 수요에서 꾸준히 언급되는 르노삼성 소형 세단.' WHERE model_code = '1488'; -- SM3 네오
UPDATE cz_model_embedding_source SET embed_text_3 = 'QM5 2세대. 패밀리 SUV 가성비 수요에서 르노삼성 선택지로 중고 시장 등장.' WHERE model_code = '1419'; -- 뉴QM5
UPDATE cz_model_embedding_source SET embed_text_3 = 'QM5 NEO. 중형 SUV 가성비 수요에서 르노삼성 선택지로 꾸준히 검색.' WHERE model_code = '1496'; -- QM5 NEO
UPDATE cz_model_embedding_source SET embed_text_3 = 'QM3 페이스리프트. 소형 SUV 수요에서 르노 감성의 가성비 선택지.' WHERE model_code = '2916'; -- New QM3
UPDATE cz_model_embedding_source SET embed_text_3 = 'QM3 1세대. 소형 SUV 국내 초기 수요 공략. 캡처 기반의 르노삼성 소형 SUV.' WHERE model_code = '1494'; -- QM3
UPDATE cz_model_embedding_source SET embed_text_3 = 'XM3 하이브리드. 국내 소형 SUV 중 하이브리드 연비 수요를 공략하는 모델.' WHERE model_code = '3299'; -- XM3 E-TECH 하이브리드
UPDATE cz_model_embedding_source SET embed_text_3 = '르노 캡처 2세대. 소형 SUV 수요에서 유럽 스타일로 차별화하는 모델.' WHERE model_code = '3088'; -- 캡처

-- =====================
-- 폭스바겐 계열
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '폭스바겐 골프 7세대. 수입 준중형 해치백의 글로벌 베스트셀러. 운전 재미의 기준.' WHERE model_code = '1599'; -- 골프
UPDATE cz_model_embedding_source SET embed_text_3 = '폭스바겐 티구안 2세대. 수입 준중형 SUV 수요에서 꾸준한 유럽 이성적 선택지.' WHERE model_code = '2053'; -- 뉴 티구안
UPDATE cz_model_embedding_source SET embed_text_3 = '폭스바겐 티구안 2세대 페이스리프트. 수입 준중형 SUV 이성적 선택의 최신형.' WHERE model_code = '2925'; -- 더 뉴 티구안
UPDATE cz_model_embedding_source SET embed_text_3 = '폭스바겐 티구안 올스페이스. 7인 수요에서 수입 패밀리 SUV 합리적 선택지.' WHERE model_code = '2941'; -- 티구안 올스페이스
UPDATE cz_model_embedding_source SET embed_text_3 = '폭스바겐 파사트 페이스리프트. 수입 중형 세단 이성적 선택의 대명사.' WHERE model_code = '2054'; -- 더뉴파사트
UPDATE cz_model_embedding_source SET embed_text_3 = '폭스바겐 아테온. 패스트백 세단 감성의 수입차 수요에서 독특한 포지션.' WHERE model_code = '2969'; -- 아테온

-- =====================
-- 푸조
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '푸조 308. 유럽 준중형 해치백 감성을 원하는 수요에서 독특한 프렌치 포지션.' WHERE model_code = '1581'; -- 308

-- =====================
-- 현대·기아 기타
-- =====================
UPDATE cz_model_embedding_source SET embed_text_3 = '아슬란. 그랜저와 K7 사이 틈새를 노린 현대 전륜구동 준대형. 국내 희소 모델.' WHERE model_code = '2517'; -- 아슬란
UPDATE cz_model_embedding_source SET embed_text_3 = '알페온. 뷰익 베이스 국내 준대형. 희소한 수요에서 아메리칸 준대형 감성.' WHERE model_code = '1395'; -- 알페온
UPDATE cz_model_embedding_source SET embed_text_3 = '골프 8세대. 수입 준중형 해치백 이성적 선택의 현재 기준.' WHERE model_code = '3237'; -- The Golf 8(MK8)
