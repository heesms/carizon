package com.carizon.rag.service;

import com.carizon.dto.CarDetailRow;
import com.carizon.rag.dto.CarEmbeddingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 차량 데이터를 RAG에 사용할 텍스트로 변환하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarTextConverterService {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Object>> modelBasicInfoCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> modelStyleInfoCache = new ConcurrentHashMap<>();

    // embed_text_2에서 추출할 스타일 관련 키 목록
    private static final Set<String> STYLE_KEYS = Set.of(
            "스타일", "타겟", "별명", "장점", "단점", "추천", "차급", "좌석", "3열", "연비등급", "유지비"
    );
    
    /**
     * 차종을 카테고리로 매핑 (세단, SUV, 미니밴, 해치백, 왜건 등)
     * 실제 DB의 BODY_TYPE 값을 기준으로 매핑 (모델명 기반 판단 제거)
     */
    private String mapBodyTypeToCategory(String bodyType) {
        if (bodyType == null || bodyType.isEmpty()) {
            return null;
        }
        
        String upper = bodyType.toUpperCase();
        String lower = bodyType.toLowerCase();
        
        // 세단 관련
        if (lower.contains("세단") || lower.contains("sedan") || lower.contains("saloon")) {
            return "세단";
        }
        // SUV 관련 (RV는 한국에서 SUV 또는 미니밴 모두 포함할 수 있으므로, 
        // 모델명이나 추가 정보 없이는 SUV로 분류하지 않음)
        if (lower.contains("suv") || lower.contains("스포츠유틸리티") || 
            lower.contains("크로스오버") || lower.contains("crossover")) {
            return "SUV";
        }
        // RV 처리: 한국에서 RV는 주로 미니밴/MPV를 의미하지만, SUV도 포함할 수 있음
        // 기본적으로는 미니밴으로 분류하되, 추후 모델별 세분화 가능
        if (upper.equals("RV") || lower.contains("rv")) {
            // RV는 기본적으로 미니밴으로 분류
            // 단, 모델명이나 추가 정보로 SUV인지 판단 가능하면 그에 따라 분류
            return "미니밴";
        }
        // 미니밴/승합차 관련
        if (lower.contains("미니밴") || lower.contains("van") || lower.contains("승합") ||
            lower.contains("mpv") || lower.contains("minivan")) {
            return "미니밴";
        }
        // 해치백 관련
        if (lower.contains("해치백") || lower.contains("hatchback") || lower.contains("해치")) {
            return "해치백";
        }
        // 왜건 관련
        if (lower.contains("왜건") || lower.contains("wagon") || lower.contains("에스테이트")) {
            return "왜건";
        }
        // 쿠페 관련
        if (lower.contains("쿠페") || lower.contains("coupe")) {
            return "쿠페";
        }
        // 컨버터블 관련
        if (lower.contains("컨버터블") || lower.contains("convertible") || lower.contains("로드스터")) {
            return "컨버터블";
        }
        // 픽업트럭 관련
        if (lower.contains("픽업") || lower.contains("pickup") || lower.contains("트럭")) {
            return "픽업트럭";
        }
        // 경차/소형 (소형차 추천 시 필터용)
        if (lower.contains("경차") || lower.contains("경차량") || lower.contains("케이카")) {
            return "경차";
        }
        if (lower.contains("소형") || lower.contains("소형차") || lower.contains("경소형")) {
            return "소형";
        }
        
        // 매핑되지 않으면 원본 반환
        return bodyType;
    }
    
    /**
     * 가격대 카테고리 계산
     */
    private String getPriceCategory(Integer price) {
        if (price == null) return null;
        
        if (price >= 5000) return "고급차";
        if (price >= 3000) return "중고급차";
        if (price >= 1500) return "중형차";
        return "경형차";
    }
    
    /**
     * 연식 카테고리 계산
     */
    private String getYearCategory(Integer year) {
        if (year == null) return null;
        
        int currentYear = java.time.Year.now().getValue();
        int age = currentYear - year;
        
        if (age <= 3) return "최신형";
        if (age <= 7) return "중고형";
        return "구형";
    }
    
    /**
     * 주행거리 카테고리 계산
     */
    private String getMileageCategory(Integer mileage) {
        if (mileage == null) return null;
        
        if (mileage <= 30000) return "저주행";
        if (mileage <= 100000) return "중주행";
        return "고주행";
    }
    
    /**
     * 배기량 카테고리 계산
     */
    private String getDisplacementCategory(Integer displacement) {
        if (displacement == null) return null;
        
        if (displacement <= 1600) return "소형";
        if (displacement <= 2500) return "중형";
        return "대형";
    }
    
    /**
     * 연료 타입 정규화
     */
    private String normalizeFuel(String fuel) {
        if (fuel == null || fuel.isEmpty()) return null;
        
        String lower = fuel.toLowerCase();
        if (lower.contains("가솔린") || lower.contains("gasoline") || lower.contains("휘발유")) {
            return "가솔린";
        }
        if (lower.contains("디젤") || lower.contains("diesel")) {
            return "디젤";
        }
        if (lower.contains("하이브리드") || lower.contains("hybrid")) {
            return "하이브리드";
        }
        if (lower.contains("전기") || lower.contains("electric") || lower.contains("ev")) {
            return "전기";
        }
        if (lower.contains("lpg") || lower.contains("엘피지")) {
            return "LPG";
        }
        return fuel;
    }
    
    /**
     * 변속기 타입 정규화
     */
    private String normalizeTransmission(String transmission) {
        if (transmission == null || transmission.isEmpty()) return null;
        
        String lower = transmission.toLowerCase();
        if (lower.contains("자동") || lower.contains("automatic") || lower.contains("at")) {
            return "자동";
        }
        if (lower.contains("수동") || lower.contains("manual") || lower.contains("mt")) {
            return "수동";
        }
        if (lower.contains("cvt")) {
            return "CVT";
        }
        if (lower.contains("dct") || lower.contains("듀얼클러치")) {
            return "DCT";
        }
        return transmission;
    }
    
    /**
     * 차량 상세 정보를 텍스트로 변환 (개선된 버전)
     * 차종 구분을 명확하게 하기 위해 키워드 강화
     * 모델 기본 정보도 포함
     */
    public String convertCarToText(CarDetailRow car) {
        return convertCarToText(car, new HashMap<>());
    }
    
    /**
     * 차량 상세 정보를 텍스트로 변환 (모델 기본 정보 포함)
     */
    public String convertCarToText(CarDetailRow car, Map<String, Object> modelBasicInfo) {
        StringBuilder text = new StringBuilder();
        
        // 카테고리 매핑
        String bodyTypeCategory = car.bodyType() != null ? mapBodyTypeToCategory(car.bodyType()) : null;
        String priceCategory = getPriceCategory(car.price());
        String yearCategory = getYearCategory(car.year());
        String mileageCategory = getMileageCategory(car.mileage());
        String displacementCategory = getDisplacementCategory(car.displacement());
        String normalizedFuel = normalizeFuel(car.fuel());
        String normalizedTransmission = normalizeTransmission(car.transmission());
        
        // 1. 차량 기본 정보 (차종 강조)
        text.append("차량: ");
        if (car.makerName() != null) text.append(car.makerName()).append(" ");
        if (car.modelGroupName() != null) text.append(car.modelGroupName()).append(" ");
        if (car.modelName() != null) text.append(car.modelName()).append(" ");
        if (car.trimName() != null) text.append(car.trimName()).append(" ");
        
        // 차종 정보를 여러 형태로 강조
        if (bodyTypeCategory != null) {
            text.append("[").append(bodyTypeCategory).append("] ");
            // 차종 키워드 추가 (검색 정확도 향상)
            if (bodyTypeCategory.equals("세단")) {
                text.append("세단형 세단차량 승용차세단 ");
            } else if (bodyTypeCategory.equals("SUV")) {
                text.append("SUV형 스포츠유틸리티 ");
            } else if (bodyTypeCategory.equals("미니밴")) {
                text.append("미니밴형 승합차 ");
            }
        }
        if (car.bodyType() != null && !car.bodyType().equals(bodyTypeCategory)) {
            text.append("(").append(car.bodyType()).append(") ");
        }
        text.append("\n");
        
        // 2. 가격대 정보 (고급차 구분)
        if (priceCategory != null) {
            text.append("가격대: ").append(priceCategory);
            if (car.price() != null) {
                text.append(" (").append(String.format("%,d", car.price())).append("만원)");
            }
            text.append("\n");
        } else if (car.price() != null) {
            text.append("가격: ").append(String.format("%,d", car.price())).append("만원\n");
        }
        
        // 3. 연식 정보 (카테고리 포함)
        if (car.year() != null) {
            text.append("연식: ").append(car.year()).append("년");
            if (yearCategory != null) {
                text.append(" (").append(yearCategory).append(")");
            }
            text.append("\n");
        }
        
        // 4. 주행거리 정보 (카테고리 포함)
        if (car.mileage() != null) {
            text.append("주행거리: ").append(String.format("%,d", car.mileage())).append("km");
            if (mileageCategory != null) {
                text.append(" (").append(mileageCategory).append(")");
            }
            text.append("\n");
        }
        
        // 5. 배기량 정보 (카테고리 포함)
        if (car.displacement() != null) {
            text.append("배기량: ").append(car.displacement()).append("cc");
            if (displacementCategory != null) {
                text.append(" (").append(displacementCategory).append(")");
            }
            text.append("\n");
        }
        
        // 6. 연료 정보 (정규화된 값 포함)
        if (normalizedFuel != null) {
            text.append("연료: ").append(normalizedFuel);
            if (car.fuel() != null && !car.fuel().equals(normalizedFuel)) {
                text.append(" (").append(car.fuel()).append(")");
            }
            text.append("\n");
        } else if (car.fuel() != null) {
            text.append("연료: ").append(car.fuel()).append("\n");
        }
        
        // 7. 변속기 정보 (정규화된 값 포함)
        if (normalizedTransmission != null) {
            text.append("변속기: ").append(normalizedTransmission);
            if (car.transmission() != null && !car.transmission().equals(normalizedTransmission)) {
                text.append(" (").append(car.transmission()).append(")");
            }
            text.append("\n");
        } else if (car.transmission() != null) {
            text.append("변속기: ").append(car.transmission()).append("\n");
        }
        
        // 8. 차종 정보 (명시적으로 강조)
        if (bodyTypeCategory != null) {
            text.append("차종카테고리: ").append(bodyTypeCategory).append("\n");
        }
        if (car.bodyType() != null) {
            text.append("차종: ").append(car.bodyType()).append("\n");
        }
        
        // 9. 기타 정보 (제조국은 유사도 검색용으로 텍스트에 포함)
        if (modelBasicInfo.get("maker_country") != null && !String.valueOf(modelBasicInfo.get("maker_country")).isEmpty()) {
            text.append("제조국: ").append(modelBasicInfo.get("maker_country")).append("\n");
        }
        if (car.color() != null) {
            text.append("색상: ").append(car.color()).append("\n");
        }
        if (car.region() != null) {
            text.append("지역: ").append(car.region()).append("\n");
        }
        if (car.status() != null) {
            text.append("판매상태: ").append(car.status()).append("\n");
        }
        
        // 10. 모델 기본 정보 (매물이 아닌 모델 자체의 정보)
        if (!modelBasicInfo.isEmpty()) {
            text.append("\n[모델 기본 정보]\n");

            if (modelBasicInfo.get("typical_fuel") != null) {
                text.append("모델기본연료: ").append(modelBasicInfo.get("typical_fuel")).append("\n");
            }
            if (modelBasicInfo.get("typical_transmission") != null) {
                text.append("모델기본변속기: ").append(modelBasicInfo.get("typical_transmission")).append("\n");
            }
            if (modelBasicInfo.get("typical_body_type") != null) {
                text.append("모델기본차종: ").append(modelBasicInfo.get("typical_body_type")).append("\n");
            }
            if (modelBasicInfo.get("avg_displacement") != null) {
                Number avgDisp = (Number) modelBasicInfo.get("avg_displacement");
                text.append("모델평균배기량: ").append(avgDisp.intValue()).append("cc\n");
            }
            if (modelBasicInfo.get("min_year") != null && modelBasicInfo.get("max_year") != null) {
                Number minYear = (Number) modelBasicInfo.get("min_year");
                Number maxYear = (Number) modelBasicInfo.get("max_year");
                text.append("모델연식범위: ").append(minYear.intValue()).append("년~").append(maxYear.intValue()).append("년\n");
            }
        }

        // 11. 모델 감성/스타일 정보 (cz_model_embedding_source.embed_text_2)
        // "간지나는 차", "20대 여성", "패밀리카" 등 의미 기반 쿼리에 대응
        boolean hasStyleInfo = STYLE_KEYS.stream().anyMatch(k -> modelBasicInfo.get("model_" + k) != null);
        if (hasStyleInfo) {
            text.append("\n[모델 특성]\n");
            appendIfPresent(text, modelBasicInfo, "model_스타일",   "스타일: ");
            appendIfPresent(text, modelBasicInfo, "model_타겟",     "추천대상: ");
            appendIfPresent(text, modelBasicInfo, "model_별명",     "별명: ");
            appendIfPresent(text, modelBasicInfo, "model_장점",     "장점: ");
            appendIfPresent(text, modelBasicInfo, "model_단점",     "단점: ");
            appendIfPresent(text, modelBasicInfo, "model_추천",     "추천용도: ");
            appendIfPresent(text, modelBasicInfo, "model_차급",     "차급: ");
            appendIfPresent(text, modelBasicInfo, "model_좌석",     "좌석: ");
            appendIfPresent(text, modelBasicInfo, "model_3열",      "3열여부: ");
            appendIfPresent(text, modelBasicInfo, "model_연비등급", "연비등급: ");
            appendIfPresent(text, modelBasicInfo, "model_유지비",   "유지비: ");
        }

        return text.toString();
    }
    
    /**
     * 모델별 기본 정보 조회 (model_code 기준으로 가장 빈도 높은 스펙)
     * MySQL에서는 MODE() 함수가 없으므로 서브쿼리로 최빈값 계산
     */
    private Map<String, Object> getModelBasicInfo(String modelCode) {
        if (modelCode == null || modelCode.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, Object> cached = modelBasicInfoCache.get(modelCode);
        if (cached != null) {
            return new HashMap<>(cached);
        }
        
        // 각 필드별로 가장 빈도 높은 값 조회
        String sql = """
            SELECT 
                (SELECT fuel FROM (
                    SELECT pc2.fuel, COUNT(*) as cnt
                    FROM car_master cm2
                    INNER JOIN platform_car pc2 ON pc2.car_id = cm2.car_id
                    WHERE cm2.model_code = ?
                      AND cm2.adv_status = 'ONSALE'
                      AND pc2.fuel IS NOT NULL
                    GROUP BY pc2.fuel
                    ORDER BY cnt DESC
                    LIMIT 1
                ) t1) AS typical_fuel,
                (SELECT transmission FROM (
                    SELECT pc2.transmission, COUNT(*) as cnt
                    FROM car_master cm2
                    INNER JOIN platform_car pc2 ON pc2.car_id = cm2.car_id
                    WHERE cm2.model_code = ?
                      AND cm2.adv_status = 'ONSALE'
                      AND pc2.transmission IS NOT NULL
                    GROUP BY pc2.transmission
                    ORDER BY cnt DESC
                    LIMIT 1
                ) t2) AS typical_transmission,
                (SELECT body_type FROM (
                    SELECT COALESCE(pc2.body_type, cm2.body_type) as body_type, COUNT(*) as cnt
                    FROM car_master cm2
                    INNER JOIN platform_car pc2 ON pc2.car_id = cm2.car_id
                    WHERE cm2.model_code = ?
                      AND cm2.adv_status = 'ONSALE'
                      AND COALESCE(pc2.body_type, cm2.body_type) IS NOT NULL
                    GROUP BY COALESCE(pc2.body_type, cm2.body_type)
                    ORDER BY cnt DESC
                    LIMIT 1
                ) t3) AS typical_body_type,
                AVG(cm.displacement) AS avg_displacement,
                MIN(cm.year) AS min_year,
                MAX(cm.year) AS max_year
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE cm.model_code = ?
              AND cm.adv_status = 'ONSALE'
            GROUP BY cm.model_code
            """;
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, 
                modelCode, modelCode, modelCode, modelCode);
            if (!results.isEmpty()) {
                Map<String, Object> loaded = new HashMap<>(results.get(0));
                modelBasicInfoCache.put(modelCode, loaded);
                return new HashMap<>(loaded);
            }
        } catch (Exception e) {
            log.warn("Failed to get model basic info for model_code={}", modelCode, e);
        }
        
        modelBasicInfoCache.put(modelCode, Map.of());
        return new HashMap<>();
    }

    public void clearModelBasicInfoCache() {
        modelBasicInfoCache.clear();
        modelStyleInfoCache.clear();
    }

    /**
     * cz_model_embedding_source.embed_text_2 에서 스타일/감성/타겟 정보를 추출.
     * 반환 맵 키는 "model_스타일", "model_타겟" 등으로 prefix 붙여서 modelBasicInfo에 합산.
     */
    private Map<String, Object> getModelStyleInfo(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) return Map.of();
        Map<String, Object> cached = modelStyleInfoCache.get(modelCode);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT embed_text_2 FROM cz_model_embedding_source WHERE model_code = ? LIMIT 1",
                    modelCode);
            if (rows.isEmpty()) {
                modelStyleInfoCache.put(modelCode, Map.of());
                return Map.of();
            }
            Object raw = rows.get(0).get("embed_text_2");
            if (raw == null) {
                modelStyleInfoCache.put(modelCode, Map.of());
                return Map.of();
            }
            String embedText = raw.toString().trim();
            if (embedText.isBlank() || embedText.contains("#정보확인필요")) {
                modelStyleInfoCache.put(modelCode, Map.of());
                return Map.of();
            }
            Map<String, String> parsed = parseEmbedText(embedText);
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : STYLE_KEYS) {
                String val = parsed.get(key);
                if (val != null && !val.isBlank()) {
                    result.put("model_" + key, val);
                }
            }
            modelStyleInfoCache.put(modelCode, result);
            return result;
        } catch (Exception e) {
            log.warn("Failed to get model style info for model_code={}", modelCode, e);
            modelStyleInfoCache.put(modelCode, Map.of());
            return Map.of();
        }
    }

    /** "키=값 | 키=값" 형식의 embed_text를 Map으로 파싱 */
    private static Map<String, String> parseEmbedText(String embedText) {
        Map<String, String> result = new LinkedHashMap<>();
        if (embedText == null || embedText.isBlank()) return result;
        for (String part : embedText.split("\\s*\\|\\s*")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                String key = part.substring(0, eq).trim();
                String val = part.substring(eq + 1).trim();
                if (!key.isBlank() && !val.isBlank()) {
                    result.put(key, val);
                }
            }
        }
        return result;
    }

    private static void appendIfPresent(StringBuilder sb, Map<String, Object> map, String key, String label) {
        Object val = map.get(key);
        if (val != null) {
            sb.append(label).append(val).append("\n");
        }
    }
    
    /**
     * 차량 ID로 CarEmbeddingDto 생성
     */
    public CarEmbeddingDto createCarEmbedding(Long carId) {
        String sql = """
            SELECT cm.car_id AS carId,
                   cm.maker_code,
                   cm.model_code,
                   COALESCE(m.maker_name, pc.maker_name) AS maker_name,
                   pc.model_group_name,
                   pc.model_name,
                   pc.trim_name,
                   cm.year, cm.mileage, cm.displacement, 
                   COALESCE(pc.fuel, cm.fuel) AS fuel,
                   COALESCE(pc.transmission, cm.transmission) AS transmission,
                   COALESCE(pc.color, cm.color) AS color,
                   COALESCE(pc.body_type, cm.body_type) AS body_type,
                   COALESCE(pc.region, cm.region) AS region,
                   pc.platform_car_id AS platformCarId, 
                   pc.platform_name, 
                   pc.price, pc.status, pc.pc_url, pc.m_url,
                   pc.option_array AS optionArray,
                   m.country_name,
                   (SELECT pc2.car_image_url FROM platform_car pc2
                    LEFT JOIN cz_platform_priority pp ON pp.platform_name = pc2.platform_name
                    WHERE pc2.car_id = cm.car_id
                      AND pc2.car_image_url IS NOT NULL AND TRIM(IFNULL(pc2.car_image_url,'')) != ''
                    ORDER BY COALESCE(pp.priority, 999) ASC LIMIT 1) AS representativeImageUrl
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            LEFT JOIN cz_maker m ON m.maker_code = cm.maker_code
            WHERE cm.car_id = ?
            LIMIT 1
            """;
        
        List<Map<String, Object>> carMaps = jdbcTemplate.queryForList(sql, carId);
        
        if (carMaps.isEmpty()) {
            return null;
        }
        
        Map<String, Object> carMap = carMaps.get(0);
        String modelCode = (String) carMap.get("model_code");
        
        // 모델 기본 정보 조회
        Map<String, Object> modelBasicInfo = getModelBasicInfo(modelCode);
        // 제조국(메이커별 나라) — 유사도 검색용 텍스트에 포함
        if (carMap.get("country_name") != null && !String.valueOf(carMap.get("country_name")).isEmpty()) {
            modelBasicInfo.put("maker_country", carMap.get("country_name"));
        }
        // 모델 스타일/감성/타겟 정보 (cz_model_embedding_source) — "간지나는", "20대 여성" 쿼리 대응
        modelBasicInfo.putAll(getModelStyleInfo(modelCode));
        
        CarDetailRow car = new CarDetailRow(
            ((Number) carMap.get("carId")).longValue(),
            (String) carMap.get("maker_code"),
            (String) carMap.get("model_code"),
            (String) carMap.get("maker_name"),
            (String) carMap.get("model_group_name"),
            (String) carMap.get("model_name"),
            (String) carMap.get("trim_name"),
            carMap.get("year") != null ? ((Number) carMap.get("year")).intValue() : null,
            carMap.get("mileage") != null ? ((Number) carMap.get("mileage")).intValue() : null,
            carMap.get("displacement") != null ? ((Number) carMap.get("displacement")).intValue() : null,
            (String) carMap.get("fuel"),
            (String) carMap.get("transmission"),
            (String) carMap.get("color"),
            (String) carMap.get("body_type"),
            (String) carMap.get("region"),
            null,
            null,
            carMap.get("platformCarId") != null ? ((Number) carMap.get("platformCarId")).longValue() : null,
            (String) carMap.get("platform_name"),
            carMap.get("price") != null ? ((Number) carMap.get("price")).intValue() : null,
            (String) carMap.get("status"),
            (String) carMap.get("pc_url"),
            (String) carMap.get("m_url"),
            (String) carMap.get("lastSeenDate"),
            (String) carMap.get("optionArray"),
            (String) carMap.get("representativeImageUrl")
        );
        
        String text = convertCarToText(car, modelBasicInfo);
        
        // 메타데이터 JSON 생성 (모든 필드 포함)
        String metadata = null;
        try {
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("carId", car.carId());
            if (car.platformCarId() != null) {
                metadataMap.put("platformCarId", car.platformCarId());
            }
            if (car.makerName() != null && !car.makerName().isEmpty()) {
                metadataMap.put("maker", car.makerName());
            }
            if (carMap.get("country_name") != null && !String.valueOf(carMap.get("country_name")).isEmpty()) {
                metadataMap.put("country", String.valueOf(carMap.get("country_name")));
            }
            if (car.modelGroupName() != null && !car.modelGroupName().isEmpty()) {
                metadataMap.put("modelGroup", car.modelGroupName());
            }
            if (car.modelName() != null && !car.modelName().isEmpty()) {
                metadataMap.put("model", car.modelName());
            }
            if (car.trimName() != null && !car.trimName().isEmpty()) {
                metadataMap.put("trim", car.trimName());
            }
            if (car.year() != null) {
                metadataMap.put("year", car.year());
                String yearCategory = getYearCategory(car.year());
                if (yearCategory != null) {
                    metadataMap.put("yearCategory", yearCategory);
                }
            }
            if (car.mileage() != null) {
                metadataMap.put("mileage", car.mileage());
                String mileageCategory = getMileageCategory(car.mileage());
                if (mileageCategory != null) {
                    metadataMap.put("mileageCategory", mileageCategory);
                }
            }
            if (car.price() != null) {
                metadataMap.put("price", car.price());
            }
            if (car.fuel() != null && !car.fuel().isEmpty()) {
                metadataMap.put("fuel", car.fuel());
                String normalizedFuel = normalizeFuel(car.fuel());
                if (normalizedFuel != null && !normalizedFuel.equals(car.fuel())) {
                    metadataMap.put("fuelType", normalizedFuel);
                }
            }
            if (car.transmission() != null && !car.transmission().isEmpty()) {
                metadataMap.put("transmission", car.transmission());
                String normalizedTransmission = normalizeTransmission(car.transmission());
                if (normalizedTransmission != null && !normalizedTransmission.equals(car.transmission())) {
                    metadataMap.put("transmissionType", normalizedTransmission);
                }
            }
            if (car.color() != null && !car.color().isEmpty()) {
                metadataMap.put("color", car.color());
            }
            if (car.bodyType() != null && !car.bodyType().isEmpty()) {
                metadataMap.put("bodyType", car.bodyType());
                // 차종 카테고리도 메타데이터에 추가
                String bodyTypeCategory = mapBodyTypeToCategory(car.bodyType());
                if (bodyTypeCategory != null) {
                    metadataMap.put("bodyTypeCategory", bodyTypeCategory);
                }
            }
            
            // 가격대 카테고리 추가
            String priceCategory = getPriceCategory(car.price());
            if (priceCategory != null) {
                metadataMap.put("priceCategory", priceCategory);
            }
            if (car.region() != null && !car.region().isEmpty()) {
                metadataMap.put("region", car.region());
            }
            if (car.status() != null && !car.status().isEmpty()) {
                metadataMap.put("status", car.status());
            }
            if (car.platformName() != null && !car.platformName().isEmpty()) {
                metadataMap.put("platformName", car.platformName());
            }
            if (car.displacement() != null) {
                metadataMap.put("displacement", car.displacement());
                String displacementCategory = getDisplacementCategory(car.displacement());
                if (displacementCategory != null) {
                    metadataMap.put("displacementCategory", displacementCategory);
                }
            }
            if (car.pcUrl() != null && !car.pcUrl().isEmpty()) {
                metadataMap.put("pcUrl", car.pcUrl());
            }
            if (car.mUrl() != null && !car.mUrl().isEmpty()) {
                metadataMap.put("mUrl", car.mUrl());
            }
            if (car.representativeImageUrl() != null && !car.representativeImageUrl().isEmpty()) {
                metadataMap.put("imageUrl", car.representativeImageUrl());
            }
            
            // 모델 기본 정보를 메타데이터에 추가
            if (!modelBasicInfo.isEmpty()) {
                if (modelBasicInfo.get("typical_fuel") != null) {
                    metadataMap.put("modelTypicalFuel", modelBasicInfo.get("typical_fuel"));
                }
                if (modelBasicInfo.get("typical_transmission") != null) {
                    metadataMap.put("modelTypicalTransmission", modelBasicInfo.get("typical_transmission"));
                }
                if (modelBasicInfo.get("typical_body_type") != null) {
                    metadataMap.put("modelTypicalBodyType", modelBasicInfo.get("typical_body_type"));
                }
                if (modelBasicInfo.get("avg_displacement") != null) {
                    Number avgDisp = (Number) modelBasicInfo.get("avg_displacement");
                    metadataMap.put("modelAvgDisplacement", avgDisp.intValue());
                }
                if (modelBasicInfo.get("min_year") != null) {
                    Number minYear = (Number) modelBasicInfo.get("min_year");
                    metadataMap.put("modelMinYear", minYear.intValue());
                }
                if (modelBasicInfo.get("max_year") != null) {
                    Number maxYear = (Number) modelBasicInfo.get("max_year");
                    metadataMap.put("modelMaxYear", maxYear.intValue());
                }
            }
            
            metadata = objectMapper.writeValueAsString(metadataMap);
        } catch (Exception e) {
            log.warn("Failed to create metadata JSON", e);
        }
        
        return CarEmbeddingDto.builder()
                .carId(car.carId())
                .platformCarId(car.platformCarId())
                .text(text)
                .metadata(metadata)
                .build();
    }
}
