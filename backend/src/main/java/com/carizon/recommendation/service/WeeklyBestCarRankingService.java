package com.carizon.recommendation.service;

import com.carizon.recommendation.dto.WeeklyBestCarDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 주간 Best 매물 순위 선정 서비스
 * 
 * [플랫폼별 코드 통합 설명]
 * - 각 플랫폼(ENCAR, KCAR, CHACHACHA 등)은 서로 다른 모델 코드와 이름을 사용합니다.
 *   예: "더 뉴 코란도 스포츠"는 플랫폼마다 다른 코드와 이름으로 표시될 수 있습니다.
 * - cz_code_map 테이블을 통해 플랫폼별 코드가 표준 코드로 매핑됩니다.
 * - car_master 테이블의 model_code는 이미 표준화된 코드이므로,
 *   모든 플랫폼의 동일 모델이 같은 model_code로 통합되어 비교 가능합니다.
 * - 따라서 이 서비스는 플랫폼에 관계없이 모델 기준으로 통합 평가가 가능합니다.
 * 
 * 추천 로직 (카리즌 스코어):
 * 1. 가격 점수 (35%): 시세 대비 저렴할수록 높음
 * 2. 주행거리 점수 (35%): 연식 대비 낮을수록 높음 (저주행·적정가 선호 반영)
 * 3. 연식 점수 (15%): 최신일수록 높음
 * 4. 최신성 점수 (15%): 최근 업데이트일수록 높음
 * 
 * 카리즌 스코어 = 가중 평균 (0~100점)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyBestCarRankingService {

    private final JdbcTemplate jdbc;

    // 스코어 가중치 (총합 1.0) - 가격보다 주행거리 비중 확대 (저주행·적정가 선호 반영)
    private static final double WEIGHT_PRICE = 0.35;      // 가격 35%
    private static final double WEIGHT_MILEAGE = 0.35;    // 주행거리 35%
    private static final double WEIGHT_AGE = 0.15;        // 연식 15%
    private static final double WEIGHT_FRESHNESS = 0.15;  // 최신성 15%
    
    // 필터링 임계값
    private static final int MAX_DAYS_SINCE_UPDATE = 14; // 14일 이상 경과 시 제외
    private static final double MAX_PRICE_DISCOUNT = 0.35; // 시세 대비 35% 이상 할인 시 미끼 가능성
    private static final int MAX_CAR_AGE = 12; // 12년 이상 차량 패널티
    private static final double MAX_KM_RATIO_PENALTY = 1.8; // 연식 대비 주행거리 비율 1.8 이상 패널티
    private static final double MAX_KM_RATIO_FILTER = 2.2; // 연식 대비 주행거리 비율 2.2 이상 필터링

    // 기타 임계값
    private static final int MIN_MILEAGE = 0;
    private static final int IDEAL_MILEAGE_PER_YEAR = 12000; // 연간 1.2만km 기준 (국내 평균)
    /** 추천 최소 가격: 300만원 이하 제외 (가격은 만원 단위) */
    private static final int MIN_RECOMMENDATION_PRICE_MAN = 300;

    /** 포스팅 랭킹 연료 가산점 (가솔린 > 디젤 > LPG/기타 0) */
    private static final double FUEL_BONUS_GASOLINE = 20.0;
    private static final double FUEL_BONUS_DIESEL = 14.0;
    /** 원점수 최대값 (base 100 + 가솔린 20) → 100점 만점으로 비율 환산 시 사용 */
    private static final double MAX_RAW_SCORE = 120.0;
    /** 후보 조회 상한. 전량 로딩으로 인한 OOM 방지용 */
    private static final int CANDIDATE_FETCH_LIMIT = 20000;

    /**
     * 모델 기준 주간 Best 매물 순위 선정
     * 
     * @param modelCode 모델 코드. 단일 "1234" 또는 복수 "1234,1111" (쉼표 구분, 공백 제거). null/빈값이면 전체 모델
     * @param trimCode 트림 코드 (선택사항, null이면 모델코드만으로 추천)
     * @param limit 상위 N개 (기본 10개). 복수 모델 시 합쳐서 상위 N개 선정
     * @return 주간 Best 매물 리스트
     */
    /**
     * 모델 코드: 단일 "1234" 또는 복수 "1234,1111" (쉼표 구분, 공백 제거).
     * 복수인 경우 각 모델 후보를 합쳐서 상위 limit개 선정.
     */
    public List<WeeklyBestCarDto> getWeeklyBestCars(String modelCode, String trimCode, int limit) {
        long startTime = System.currentTimeMillis();
        List<String> modelCodes = parseModelCodes(modelCode);
        log.info("[weekly Best] model={}, trim={}, limit={} start (parsed: {} models)", modelCode, trimCode, limit, modelCodes.size());

        // 1. 기본 매물 조회 (복수 모델이면 각각 조회 후 합침, car_image_url 있는 것만, ONSALE만)
        long step1Start = System.currentTimeMillis();
        List<WeeklyBestCarDto> candidates = fetchCandidatesForModels(modelCodes, trimCode);
        long step1Time = System.currentTimeMillis() - step1Start;
        log.info("[weekly Best] [step1] candidate listings fetched: {} ({}ms)", candidates.size(), step1Time);

        if (candidates.isEmpty()) {
            log.warn("[weekly Best] no candidates for model_code={}. Run merge/postProcess(linkToMaster) and retry.", modelCode);
            return Collections.emptyList();
        }

        // 2. 필터링 (탈락 조건 체크)
        long step2Start = System.currentTimeMillis();
        List<WeeklyBestCarDto> filteredCars = applyFilters(candidates);
        long step2Time = System.currentTimeMillis() - step2Start;
        log.info("[weekly Best] [step2] filtering done: {} ({}ms)", filteredCars.size(), step2Time);

        if (filteredCars.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 모델별 통계 계산 (중앙값 가격 등)
        long step3Start = System.currentTimeMillis();
        Map<String, ModelStatistics> modelStats = calculateModelStatistics(filteredCars);
        long step3Time = System.currentTimeMillis() - step3Start;
        log.info("[weekly Best] [step3] model stats done: {} models ({}ms)", modelStats.size(), step3Time);

        // 4. 각 매물에 스코어 계산 (새로운 점수 체계) - 모든 매물에 대해 스코어 계산
        long step4Start = System.currentTimeMillis();
        List<WeeklyBestCarDto> scoredCars = filteredCars.stream()
                .map(car -> calculateScores(car, modelStats, false)) // 평가 사유는 규칙 기반으로 먼저 생성
                .collect(Collectors.toList());
        long step4Time = System.currentTimeMillis() - step4Start;
        log.info("[weekly Best] [step4] score calc done: {} ({}ms, avg {}ms/row)", 
                scoredCars.size(), step4Time, step4Time / Math.max(1, scoredCars.size()));

        // 5. 카리즌 스코어 기준 정렬 및 순위 부여 (동점 시 가솔린·디젤 > LPG 우선)
        long step5Start = System.currentTimeMillis();
        scoredCars.sort((a, b) -> {
            int scoreCmp = b.getCarizonScore().compareTo(a.getCarizonScore());
            if (scoreCmp != 0) return scoreCmp;
            return Integer.compare(fuelPriorityForRanking(a.getFuel()), fuelPriorityForRanking(b.getFuel()));
        });
        
        for (int i = 0; i < scoredCars.size(); i++) {
            scoredCars.get(i).setRank(i + 1);
        }
        long step5Time = System.currentTimeMillis() - step5Start;
        log.info("[weekly Best] [step5] sort and rank done ({}ms)", step5Time);

        // 6. 상위 N개 반환
        long step7Start = System.currentTimeMillis();
        List<WeeklyBestCarDto> result = scoredCars.stream()
                .limit(limit)
                .collect(Collectors.toList());
        long step7Time = System.currentTimeMillis() - step7Start;

        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[weekly Best] [step7] result return done ({}ms)", step7Time);
        log.info("[weekly Best] done: {} selected (total {}ms)", result.size(), totalTime);
        for (WeeklyBestCarDto car : result) {
            log.debug("  [rank {}] {} {} - carizon score: {}, price: {} manwon, mileage: {}km", 
                    car.getRank(), car.getMakerName(), car.getModelName(),
                    car.getCarizonScore() != null ? car.getCarizonScore().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                    car.getPrice(), car.getMileage());
        }

        return result;
    }

    /** "1234" → [1234], "1234,1111" → [1234, 1111], null/빈값 → [] (전체) */
    private List<String> parseModelCodes(String modelCode) {
        if (modelCode == null || modelCode.isBlank()) return List.of();
        return Arrays.stream(modelCode.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /** 복수 모델 지원: 각 모델 후보 조회 후 합침 (동일 platform_car_id 중복 제거) */
    private List<WeeklyBestCarDto> fetchCandidatesForModels(List<String> modelCodes, String trimCode) {
        if (modelCodes.isEmpty()) return fetchCandidates(null, trimCode);
        if (modelCodes.size() == 1) return fetchCandidates(modelCodes.get(0), trimCode);
        Set<Long> seen = new HashSet<>();
        List<WeeklyBestCarDto> merged = new ArrayList<>();
        for (String code : modelCodes) {
            for (WeeklyBestCarDto c : fetchCandidates(code, trimCode)) {
                if (seen.add(c.getPlatformCarId())) merged.add(c);
            }
        }
        return merged;
    }

    /**
     * 후보 매물 조회 (단일 모델 또는 modelCode=null 시 전체)
     * @param modelCode 모델 코드 (null이면 모델 필터 없음)
     * @param trimCode 트림 코드 (선택사항, null이면 모델코드만으로 필터링)
     */
    private List<WeeklyBestCarDto> fetchCandidates(String modelCode, String trimCode) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                cm.car_id,
                pc.platform_car_id,
                pc.platform_name,
                cm.maker_code,
                m.maker_name,
                cm.model_group_code,
                mg.model_group_name,
                cm.model_code,
                mo.model_name,
                cm.trim_code,
                t.trim_name,
                cm.grade_code,
                g.grade_name,
                cm.year,
                COALESCE(pc.km, cm.mileage) AS mileage,
                pc.price,
                COALESCE(pc.fuel, cm.fuel) AS fuel,
                COALESCE(pc.transmission, cm.transmission) AS transmission,
                COALESCE(pc.body_type, cm.body_type) AS body_type,
                COALESCE(pc.region, cm.region) AS region,
                pc.status,
                pc.pc_url,
                pc.m_url,
                pc.last_seen_date,
                pc.car_image_url
            FROM car_master cm
            INNER JOIN platform_car pc ON pc.car_id = cm.car_id
            LEFT JOIN cz_maker m ON m.maker_code = cm.maker_code
            LEFT JOIN cz_model_group mg ON mg.maker_code = cm.maker_code AND mg.model_group_code = cm.model_group_code
            LEFT JOIN cz_model mo ON mo.maker_code = cm.maker_code AND mo.model_group_code = cm.model_group_code AND mo.model_code = cm.model_code
            LEFT JOIN cz_trim t ON t.maker_code = cm.maker_code AND t.model_group_code = cm.model_group_code AND t.model_code = cm.model_code AND t.trim_code = cm.trim_code
            LEFT JOIN cz_grade g ON g.maker_code = cm.maker_code AND g.model_group_code = cm.model_group_code AND g.model_code = cm.model_code AND g.trim_code = cm.trim_code AND g.grade_code = cm.grade_code
            WHERE cm.adv_status = 'ONSALE'
              AND (
                pc.status IN ('ONSALE', 'SALE', 'ADVERTISE')
                OR (pc.platform_name = 'CHUTCHA')
              )
              AND pc.price IS NOT NULL
              AND pc.price > 0
              AND pc.price > 300
              AND pc.car_image_url IS NOT NULL
              AND pc.car_image_url != ''
              AND pc.last_seen_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        """);

        List<Object> params = new ArrayList<>();
        
        if (modelCode != null && !modelCode.isEmpty()) {
            sql.append(" AND cm.model_code = ?");
            params.add(modelCode);
        }
        
        // 트림코드가 있으면 4레벨까지 필터링
        if (trimCode != null && !trimCode.trim().isEmpty()) {
            sql.append(" AND cm.trim_code = ?");
            params.add(trimCode);
        }

        sql.append(" ORDER BY pc.last_seen_date DESC, pc.updated_at DESC");
        sql.append(" LIMIT ?");
        params.add(CANDIDATE_FETCH_LIMIT);

        return jdbc.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            LocalDate lastSeenDate = rs.getDate("last_seen_date") != null 
                    ? rs.getDate("last_seen_date").toLocalDate() 
                    : null;
            
            int daysSinceUpdate = lastSeenDate != null 
                    ? (int) ChronoUnit.DAYS.between(lastSeenDate, LocalDate.now())
                    : MAX_DAYS_SINCE_UPDATE;

            return WeeklyBestCarDto.builder()
                    .carId(rs.getLong("car_id"))
                    .platformCarId(rs.getLong("platform_car_id"))
                    .platformName(rs.getString("platform_name"))
                    .makerCode(rs.getString("maker_code"))
                    .makerName(rs.getString("maker_name"))
                    .modelGroupCode(rs.getString("model_group_code"))
                    .modelGroupName(rs.getString("model_group_name"))
                    .modelCode(rs.getString("model_code"))
                    .modelName(rs.getString("model_name"))
                    .trimCode(rs.getString("trim_code"))
                    .trimName(rs.getString("trim_name"))
                    .gradeCode(rs.getString("grade_code"))
                    .gradeName(rs.getString("grade_name"))
                    .year(rs.getInt("year"))
                    .mileage(rs.getInt("mileage"))
                    .price(rs.getInt("price"))
                    .fuel(rs.getString("fuel"))
                    .transmission(rs.getString("transmission"))
                    .bodyType(rs.getString("body_type"))
                    .region(rs.getString("region"))
                    .status(rs.getString("status"))
                    .pcUrl(rs.getString("pc_url"))
                    .mUrl(rs.getString("m_url"))
                    .lastSeenDate(lastSeenDate)
                    .daysSinceUpdate(daysSinceUpdate)
                    .carImageUrl(rs.getString("car_image_url"))
                    .build();
        });
    }

    /**
     * 포스팅 랭킹 시 연료 우선순위 (작을수록 상위) - 동점 시 타이브레이커용.
     * 0: 가솔린·디젤, 1: LPG, 2: 그 외
     */
    private int fuelPriorityForRanking(String fuel) {
        if (fuel == null || fuel.isBlank()) return 2;
        String f = fuel.trim().toUpperCase();
        if (isGasolineOrDiesel(f)) return 0;
        if (f.contains("LPG") || f.contains("엘피지")) return 1;
        return 2;
    }

    /** 가솔린 여부 */
    private boolean isGasoline(String fuel) {
        if (fuel == null || fuel.isBlank()) return false;
        String f = fuel.trim().toUpperCase();
        return f.contains("가솔린") || f.contains("휘발유") || "GASOLINE".equals(f);
    }

    /** 디젤 여부 */
    private boolean isDiesel(String fuel) {
        if (fuel == null || fuel.isBlank()) return false;
        String f = fuel.trim().toUpperCase();
        return f.contains("디젤") || "DIESEL".equals(f);
    }

    /** 가솔린·디젤 여부 (동점 시 우선순위용) */
    private boolean isGasolineOrDiesel(String fuel) {
        return isGasoline(fuel) || isDiesel(fuel);
    }

    /** 하이브리드 여부 (가격 점수 매리트 적용용) */
    private boolean isHybrid(String fuel) {
        if (fuel == null || fuel.isBlank()) return false;
        String f = fuel.trim().toUpperCase();
        return f.contains("하이브리드") || f.contains("HYBRID") || "HEV".equals(f);
    }

    /**
     * 포스팅 랭킹 연료 가산점 (가솔린 > 디젤 > LPG/기타 0).
     */
    private double fuelBonusForRanking(String fuel) {
        if (isGasoline(fuel)) return FUEL_BONUS_GASOLINE;
        if (isDiesel(fuel)) return FUEL_BONUS_DIESEL;
        return 0.0;
    }

    /**
     * 필터링 적용 (탈락 조건 체크)
     */
    private List<WeeklyBestCarDto> applyFilters(List<WeeklyBestCarDto> candidates) {
        return candidates.stream()
                .filter(car -> {
                    // 1. 업데이트가 너무 오래됨 (14일 이상) - 제외
                    if (car.getDaysSinceUpdate() >= MAX_DAYS_SINCE_UPDATE) {
                        log.debug("[filter] exclude stale update: carId={}, days={}", 
                                car.getCarId(), car.getDaysSinceUpdate());
                        return false;
                    }
                    
                    // 2. 핵심 필드 누락 체크
                    if (car.getPrice() == null || car.getPrice() <= 0) {
                        log.debug("[filter] exclude no price: carId={}", car.getCarId());
                        return false;
                    }
                    if (car.getMileage() == null || car.getMileage() < 0) {
                        log.debug("[filter] exclude no mileage: carId={}", car.getCarId());
                        return false;
                    }
                    if (car.getYear() == null || car.getYear() <= 0) {
                        log.debug("[filter] exclude no year: carId={}", car.getCarId());
                        return false;
                    }
                    
                    // 2-1. 차량 정보 필수 필드 체크 (포스팅에 필요)
                    if (car.getMakerName() == null || car.getMakerName().trim().isEmpty()) {
                        log.debug("[filter] exclude no maker: carId={}", car.getCarId());
                        return false;
                    }
                    if (car.getModelGroupName() == null || car.getModelGroupName().trim().isEmpty()) {
                        log.debug("[filter] exclude no model group: carId={}", car.getCarId());
                        return false;
                    }
                    if (car.getModelName() == null || car.getModelName().trim().isEmpty()) {
                        log.debug("[filter] exclude no model: carId={}", car.getCarId());
                        return false;
                    }
                    
                    // 3. 주행거리/연식 불일치 체크 (과도하게 많은 주행거리)
                    int currentYear = LocalDate.now().getYear();
                    int carAge = Math.max(1, currentYear - car.getYear());
                    int expectedKm = carAge * IDEAL_MILEAGE_PER_YEAR;
                    double kmRatio = expectedKm > 0 ? (double) car.getMileage() / expectedKm : 0;
                    
                    if (kmRatio > MAX_KM_RATIO_FILTER) {
                        log.debug("[filter] exclude high mileage: carId={}, kmRatio={}", car.getCarId(), kmRatio);
                        return false;
                    }
                    
                    // 4. 이상한 가격 패턴 체크 (999만원, 1111만원, 1234만원 등)
                    if (isSuspiciousPrice(car.getPrice())) {
                        log.debug("[filter] exclude suspicious price: carId={}, price={} manwon", car.getCarId(), car.getPrice());
                        return false;
                    }
                    
                    return true;
                })
                .collect(Collectors.toList());
    }

    /**
     * 모델별 통계 계산
     */
    private Map<String, ModelStatistics> calculateModelStatistics(List<WeeklyBestCarDto> cars) {
        Map<String, ModelStatistics> statsMap = new HashMap<>();

        // 모델별 그룹화
        Map<String, List<WeeklyBestCarDto>> byModel = cars.stream()
                .collect(Collectors.groupingBy(WeeklyBestCarDto::getModelCode));

        for (Map.Entry<String, List<WeeklyBestCarDto>> entry : byModel.entrySet()) {
            String modelCode = entry.getKey();
            List<WeeklyBestCarDto> modelCars = entry.getValue();

            // 평균 가격 계산
            double avgPrice = modelCars.stream()
                    .mapToInt(WeeklyBestCarDto::getPrice)
                    .average()
                    .orElse(0.0);

            // 중앙값 가격 계산 (이상치에 덜 민감)
            List<Integer> prices = modelCars.stream()
                    .map(WeeklyBestCarDto::getPrice)
                    .sorted()
                    .collect(Collectors.toList());
            int medianPrice = prices.isEmpty() ? 0 
                    : prices.size() % 2 == 0
                            ? (prices.get(prices.size() / 2 - 1) + prices.get(prices.size() / 2)) / 2
                            : prices.get(prices.size() / 2);

            statsMap.put(modelCode, new ModelStatistics(
                    modelCode,
                    avgPrice,
                    medianPrice,
                    modelCars.size()
            ));
        }

        return statsMap;
    }

    /**
     * 각 매물의 스코어 계산
     * @param useLlm LLM 사용 여부 (성능 최적화를 위해 선택적)
     */
    private WeeklyBestCarDto calculateScores(WeeklyBestCarDto car, Map<String, ModelStatistics> modelStats, boolean useLlm) {
        ModelStatistics stats = modelStats.get(car.getModelCode());
        if (stats == null) {
            log.warn("[weekly Best] no model stats: {}", car.getModelCode());
            return car;
        }

        // 1. 가격 점수 (0~1) - 시세 대비 저렴할수록 높음 (주행거리 고려)
        ScoreResult priceResult = calculatePriceScoreWithReason(car.getPrice(), stats, car.getMileage(), car.getYear());
        // 하이브리드는 가격 매리트 1.2배 (상한 1.0)
        if (isHybrid(car.getFuel())) {
            BigDecimal adjusted = priceResult.score.multiply(BigDecimal.valueOf(1.2)).min(BigDecimal.ONE);
            priceResult = new ScoreResult(adjusted.setScale(4, RoundingMode.HALF_UP), priceResult.reason);
        }

        // 2. 주행거리 점수 (0~1) - 연식 대비 적정할수록 높음
        ScoreResult mileageResult = calculateMileageScoreWithReason(car.getMileage(), car.getYear());

        // 3. 연식 점수 (0~1) - 최신일수록 높음
        ScoreResult ageResult = calculateAgeScoreWithReason(car.getYear());

        // 4. 최신성 점수 (0~1) - 최근 업데이트일수록 높음
        ScoreResult freshnessResult = calculateFreshnessScoreWithReason(car.getDaysSinceUpdate());

        // 5. 패널티 계산
        double penalty = calculatePenalty(car, stats);

        // 카리즌 스코어 계산 (35% 가격, 35% 주행거리, 15% 연식, 15% 최신성 - 패널티 + 연료 가산점)
        BigDecimal baseScore = priceResult.score.multiply(BigDecimal.valueOf(WEIGHT_PRICE))
                .add(mileageResult.score.multiply(BigDecimal.valueOf(WEIGHT_MILEAGE)))
                .add(ageResult.score.multiply(BigDecimal.valueOf(WEIGHT_AGE)))
                .add(freshnessResult.score.multiply(BigDecimal.valueOf(WEIGHT_FRESHNESS)))
                .multiply(BigDecimal.valueOf(100)); // 0~1을 0~100으로 변환
        
        double fuelBonus = fuelBonusForRanking(car.getFuel()); // 가솔린·디젤 가산점, LPG 0
        BigDecimal rawScore = baseScore.subtract(BigDecimal.valueOf(penalty))
                .add(BigDecimal.valueOf(fuelBonus));
        // 100점 만점으로 비율 환산 (원점수 / MAX_RAW_SCORE * 100)
        BigDecimal carizonScore = rawScore.multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(MAX_RAW_SCORE), 2, RoundingMode.HALF_UP);

        if (carizonScore.compareTo(BigDecimal.ZERO) < 0) carizonScore = BigDecimal.ZERO;
        if (carizonScore.compareTo(BigDecimal.valueOf(100)) > 0) carizonScore = BigDecimal.valueOf(100);

        // 카리즌 스코어 종합 사유 생성 (규칙 기반 - LLM은 별도로 AI 평가로 사용)
        String carizonReason = generateCarizonScoreReason(carizonScore, priceResult, mileageResult, 
                ageResult, freshnessResult, penalty, car.getRank());

        car.setPriceScore(priceResult.score.multiply(BigDecimal.valueOf(100))); // 0~100으로 변환
        car.setPriceScoreReason(priceResult.reason);
        car.setMileageScore(mileageResult.score.multiply(BigDecimal.valueOf(100)));
        car.setMileageScoreReason(mileageResult.reason);
        car.setFreshnessScore(freshnessResult.score.multiply(BigDecimal.valueOf(100)));
        car.setFreshnessScoreReason(freshnessResult.reason);
        car.setCarizonScore(carizonScore);
        car.setCarizonScoreReason(carizonReason);

        return car;
    }

    /**
     * 가격 점수 계산 (사유 포함) - 0~1 정규화
     * 시세 대비 저렴할수록 높은 점수
     * 주행거리가 높을수록 가격 점수에 패널티 적용
     */
    private ScoreResult calculatePriceScoreWithReason(Integer price, ModelStatistics stats, Integer mileage, Integer year) {
        if (price == null || price <= 0 || stats.getMedianPrice() <= 0) {
            return new ScoreResult(BigDecimal.ZERO, "가격 정보 없음");
        }

        // 시세 대비 비율 계산
        double priceRatio = (double) (stats.getMedianPrice() - price) / stats.getMedianPrice();
        // +면 시세보다 저렴, -면 시세보다 비쌈
        
        // 과도하게 싼 매물(미끼) 방지를 위해 캡 적용: -0.15 ~ +0.30
        double priceRatioClamped = Math.max(-0.15, Math.min(0.30, priceRatio));
        
        // 0~1로 정규화: (priceRatioClamped + 0.15) / 0.45
        double score = (priceRatioClamped + 0.15) / 0.45;
        
        // 주행거리 패널티 적용: 주행거리가 높을수록 가격 점수 점진적으로 감소
        if (mileage != null && year != null) {
            int currentYear = LocalDate.now().getYear();
            int carAge = Math.max(1, currentYear - year);
            int expectedKm = carAge * IDEAL_MILEAGE_PER_YEAR;
            
            if (expectedKm > 0) {
                double kmRatio = (double) mileage / expectedKm;
                // 주행거리 비율에 따라 점진적으로 패널티 적용
                // kmRatio가 1.0 이상이면 패널티 시작, 점진적으로 증가
                if (kmRatio > 1.0) {
                    // 1.0~1.5: 경미한 패널티 (0~10%)
                    // 1.5~2.0: 중간 패널티 (10~25%)
                    // 2.0 이상: 강한 패널티 (25~40%)
                    double penalty;
                    if (kmRatio <= 1.5) {
                        penalty = (kmRatio - 1.0) * 0.2; // 0~10%
                    } else if (kmRatio <= 2.0) {
                        penalty = 0.1 + (kmRatio - 1.5) * 0.3; // 10~25%
                    } else {
                        penalty = 0.25 + Math.min(0.15, (kmRatio - 2.0) * 0.3); // 25~40%
                    }
                    score = Math.max(0.0, score - penalty);
                }
            }
        }
        
        score = Math.max(0.0, Math.min(1.0, score)); // 0~1 범위 보장
        
        String reason;
        double priceDiffPercent = priceRatio * 100.0;
        if (priceRatio >= 0.25) {
            reason = String.format("시세 대비 %.0f%% 저렴 (매우 우수)", Math.abs(priceDiffPercent));
        } else if (priceRatio >= 0.10) {
            reason = String.format("시세 대비 %.0f%% 저렴 (우수)", Math.abs(priceDiffPercent));
        } else if (priceRatio >= -0.05) {
            reason = String.format("시세 대비 %.0f%% 저렴 (적정)", Math.abs(priceDiffPercent));
        } else {
            reason = String.format("시세 대비 %.0f%% 비쌈", Math.abs(priceDiffPercent));
        }
        
        // 주행거리 패널티가 적용된 경우 사유에 추가
        if (mileage != null && year != null) {
            int currentYear = LocalDate.now().getYear();
            int carAge = Math.max(1, currentYear - year);
            int expectedKm = carAge * IDEAL_MILEAGE_PER_YEAR;
            if (expectedKm > 0) {
                double kmRatio = (double) mileage / expectedKm;
                if (kmRatio > 1.5) {
                    reason += String.format(" (주행거리 과다: 연평균 %.0fkm 초과)", (kmRatio - 1.0) * IDEAL_MILEAGE_PER_YEAR);
                }
            }
        }

        return new ScoreResult(
                BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP),
                reason
        );
    }

    /**
     * 주행거리 점수 계산 (사유 포함) - 0~1 정규화
     * 연식 대비 적정할수록 높은 점수
     */
    private ScoreResult calculateMileageScoreWithReason(Integer mileage, Integer year) {
        if (mileage == null || mileage < MIN_MILEAGE) {
            return new ScoreResult(BigDecimal.ZERO, "주행거리 정보 없음");
        }

        int currentYear = LocalDate.now().getYear();
        int carAge = currentYear - (year != null ? year : currentYear);
        carAge = Math.max(1, carAge); // 최소 1년

        // 연식 대비 예상 주행거리
        int expectedKm = carAge * IDEAL_MILEAGE_PER_YEAR;
        if (expectedKm <= 0) {
            return new ScoreResult(BigDecimal.ZERO, "연식 정보 오류");
        }

        // 연식 대비 주행거리 비율
        double kmRatio = (double) mileage / expectedKm;
        
        // 점수: 저주행은 최고점 부여, 고주행은 가파르게 감점 (선택 시 저주행·적정가 선호 반영)
        double score;
        if (kmRatio <= 0) {
            score = 0.0;
        } else if (kmRatio <= 0.5) {
            // 매우 적은 주행거리: 최고점 (1.0) — 10,000km급이 50,000km급보다 확실히 유리하도록
            score = 1.0;
        } else if (kmRatio <= 0.8) {
            // 적은 주행거리: 0.95~1.0
            score = 0.95 + (kmRatio - 0.5) / 0.3 * 0.05;
        } else if (kmRatio <= 1.0) {
            // 적정 주행거리: 0.95~1.0
            score = 0.95 + (1.0 - kmRatio) / 0.2 * 0.05;
        } else if (kmRatio <= 1.2) {
            // 약간 많은 주행거리: 0.85~0.95
            score = 0.95 - (kmRatio - 1.0) * 0.5;
        } else if (kmRatio <= 1.5) {
            // 다소 많은 주행거리: 0.70~0.85 (가파르게 감점)
            score = 0.85 - (kmRatio - 1.2) * 0.5;
        } else if (kmRatio <= 2.0) {
            // 많은 주행거리: 0.45~0.70
            score = 0.70 - (kmRatio - 1.5) * 0.5;
        } else {
            // 매우 많은 주행거리: 0.0~0.45
            score = Math.max(0.0, 0.45 - (kmRatio - 2.0) * 0.25);
        }
        score = Math.max(0.0, Math.min(1.0, score)); // 0~1 범위 보장

        String reason;
        double avgKmPerYear = (double) mileage / carAge;
        if (kmRatio <= 0.6) {
            reason = String.format("연식 대비 주행거리 매우 적음 (%,dkm, 연평균 %.0fkm)", 
                    mileage, avgKmPerYear);
        } else if (kmRatio <= 1.0) {
            reason = String.format("연식 대비 주행거리 적정 (%,dkm, 연평균 %.0fkm)", 
                    mileage, avgKmPerYear);
        } else if (kmRatio <= 1.4) {
            reason = String.format("연식 대비 주행거리 다소 많음 (%,dkm, 연평균 %.0fkm)", 
                    mileage, avgKmPerYear);
        } else if (kmRatio <= 1.8) {
            reason = String.format("연식 대비 주행거리 많음 (%,dkm, 연평균 %.0fkm)", 
                    mileage, avgKmPerYear);
        } else {
            reason = String.format("연식 대비 주행거리 매우 많음 (%,dkm, 연평균 %.0fkm)", 
                    mileage, avgKmPerYear);
        }

        return new ScoreResult(
                BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP),
                reason
        );
    }
    
    /**
     * 연식 점수 계산 (사유 포함) - 0~1 정규화
     * 최신일수록 높은 점수
     */
    private ScoreResult calculateAgeScoreWithReason(Integer year) {
        if (year == null || year <= 0) {
            return new ScoreResult(BigDecimal.ZERO, "연식 정보 없음");
        }

        int currentYear = LocalDate.now().getYear();
        int ageYears = currentYear - year;
        ageYears = Math.max(0, ageYears);

        // 연식 점수: 점진적으로 감소
        // 0~3년: 최고점 (1.0)
        // 3~5년: 약간 감소 (1.0~0.9)
        // 5~7년: 중간 감소 (0.9~0.75)
        // 7~10년: 강한 감소 (0.75~0.5)
        // 10~15년: 매우 강한 감소 (0.5~0.25)
        // 15년 이상: 최대 감소 (0.25~0.0)
        double score;
        if (ageYears <= 3) {
            score = 1.0;
        } else if (ageYears <= 5) {
            score = 1.0 - (ageYears - 3) * 0.05; // 1.0~0.9
        } else if (ageYears <= 7) {
            score = 0.9 - (ageYears - 5) * 0.075; // 0.9~0.75
        } else if (ageYears <= 10) {
            score = 0.75 - (ageYears - 7) * (0.25 / 3.0); // 0.75~0.5
        } else if (ageYears <= 15) {
            score = 0.5 - (ageYears - 10) * 0.05; // 0.5~0.25
        } else {
            score = Math.max(0.0, 0.25 - (ageYears - 15) * 0.05); // 0.25~0.0
        }
        score = Math.max(0.0, Math.min(1.0, score)); // 0~1 범위 보장

        String reason;
        if (ageYears <= 3) {
            reason = String.format("%d년식 (매우 최신)", year);
        } else if (ageYears <= 5) {
            reason = String.format("%d년식 (최신)", year);
        } else if (ageYears <= 7) {
            reason = String.format("%d년식 (적정 연식)", year);
        } else if (ageYears <= 10) {
            reason = String.format("%d년식 (보통)", year);
        } else if (ageYears <= 15) {
            reason = String.format("%d년식 (다소 오래됨)", year);
        } else {
            reason = String.format("%d년식 (오래됨)", year);
        }

        return new ScoreResult(
                BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP),
                reason
        );
    }

    /**
     * 최신성 점수 계산 (사유 포함) - 0~1 정규화
     * 최근 업데이트일수록 높은 점수
     */
    private ScoreResult calculateFreshnessScoreWithReason(int daysSinceUpdate) {
        if (daysSinceUpdate < 0) {
            daysSinceUpdate = 0;
        }

        // exp(-days / 5) 방식: 0~2일은 거의 만점, 7일은 꽤 떨어짐, 14일은 많이 떨어짐
        double score = Math.exp(-daysSinceUpdate / 5.0);
        score = Math.max(0.0, Math.min(1.0, score)); // 0~1 범위 보장

        // 최신성 사유는 평가 사유에서 제외 (워딩 제거)
        String reason = "";

        return new ScoreResult(
                BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP),
                reason
        );
    }

    /**
     * 패널티 계산
     */
    private double calculatePenalty(WeeklyBestCarDto car, ModelStatistics stats) {
        double penalty = 0.0;
        
        // 1. 주행거리 과다 패널티
        int currentYear = LocalDate.now().getYear();
        int carAge = Math.max(1, currentYear - (car.getYear() != null ? car.getYear() : currentYear));
        int expectedKm = carAge * IDEAL_MILEAGE_PER_YEAR;
        if (expectedKm > 0) {
            double kmRatio = (double) car.getMileage() / expectedKm;
            if (kmRatio > MAX_KM_RATIO_PENALTY) {
                penalty += 5.0;
                if (kmRatio > MAX_KM_RATIO_FILTER) {
                    penalty += 5.0; // 총 10점
                }
            }
        }
        
        // 2. 시세 대비 너무 싼 매물 패널티 (미끼 가능성)
        if (stats.getMedianPrice() > 0 && car.getPrice() != null) {
            double priceRatio = (double) (stats.getMedianPrice() - car.getPrice()) / stats.getMedianPrice();
            if (priceRatio > MAX_PRICE_DISCOUNT) {
                penalty += 8.0;
            }
        }
        
        // 3. 연식 오래된 차량 패널티
        if (car.getYear() != null) {
            int ageYears = currentYear - car.getYear();
            if (ageYears >= MAX_CAR_AGE) {
                penalty += 5.0;
            }
        }
        
        // 4. 데이터 누락 패널티 (이미 필터링에서 걸러지지만 추가 체크)
        if (car.getPrice() == null || car.getMileage() == null || car.getYear() == null) {
            penalty += 10.0;
        }
        
        return penalty;
    }

    /**
     * 카리즌 스코어 종합 사유 생성 (규칙 기반 - 다양하고 구체적인 평가)
     */
    private String generateCarizonScoreReason(BigDecimal carizonScore, 
                                             ScoreResult priceResult,
                                             ScoreResult mileageResult,
                                             ScoreResult ageResult,
                                             ScoreResult freshnessResult,
                                             double penalty,
                                             Integer rank) {
        // 강점 항목 수집 (0.7 이상)
        List<String> strengths = new ArrayList<>();
        if (priceResult.score.doubleValue() >= 0.7 && !priceResult.reason.isEmpty()) {
            strengths.add(priceResult.reason);
        }
        if (mileageResult.score.doubleValue() >= 0.7 && !mileageResult.reason.isEmpty()) {
            strengths.add(mileageResult.reason);
        }
        if (ageResult.score.doubleValue() >= 0.7 && !ageResult.reason.isEmpty()) {
            strengths.add(ageResult.reason);
        }
        
        // 시작 문구 다양화 (1등에게만 "최적의 선택지" 문구 사용)
        String opening;
        if (rank != null && rank == 1) {
            // 1등 전용 문구 (최적의 선택지 포함)
            String[] firstPlaceTemplates = {
                "여러 중고차 플랫폼을 종합적으로 분석한 결과, 이 매물이 최적의 선택지입니다.",
                "여러 플랫폼을 비교한 결과, 이 매물이 가장 합리적인 선택지로 평가됩니다.",
                "중고차 시장에서 찾기 어려운 우수한 조건을 갖춘 매물입니다.",
                "가격 대비 매우 우수한 매물로, 구매를 고려해볼 만한 가치가 있습니다.",
                "시세 대비 유리한 조건과 적정한 주행거리를 갖춘 매력적인 매물입니다.",
                "동급 모델 대비 경쟁력이 뛰어난 차량으로 평가됩니다.",
                "시장 평균 대비 우수한 조건을 갖춘 매력적인 매물입니다.",
                "가성비와 상품성을 모두 갖춘 추천 매물입니다."
            };
            opening = firstPlaceTemplates[(int)(Math.random() * firstPlaceTemplates.length)];
        } else {
            // 2등 이하 문구 (최적의 선택지 제외)
            String[] otherTemplates = {
                "여러 플랫폼을 비교한 결과, 이 매물이 가장 합리적인 선택지로 평가됩니다.",
                "중고차 시장에서 찾기 어려운 우수한 조건을 갖춘 매물입니다.",
                "가격 대비 매우 우수한 매물로, 구매를 고려해볼 만한 가치가 있습니다.",
                "시세 대비 유리한 조건과 적정한 주행거리를 갖춘 매력적인 매물입니다.",
                "동급 모델 대비 경쟁력이 뛰어난 차량으로 평가됩니다.",
                "시장 평균 대비 우수한 조건을 갖춘 매력적인 매물입니다.",
                "가성비와 상품성을 모두 갖춘 추천 매물입니다."
            };
            opening = otherTemplates[(int)(Math.random() * otherTemplates.length)];
        }
        
        // 강점 나열
        if (!strengths.isEmpty()) {
            opening += " 특히 ";
            if (strengths.size() == 1) {
                opening += strengths.get(0);
            } else if (strengths.size() == 2) {
                opening += strengths.get(0) + ", " + strengths.get(1);
            } else {
                opening += strengths.get(0) + ", " + strengths.get(1) + " 등";
            }
            opening += "이 이 매물의 주요 강점입니다.";
        }
        
        // 패널티 정보 추가
        if (penalty > 0) {
            opening += String.format(" (참고: 일부 항목에서 %.0f점 감점)", penalty);
        }
        
        return opening;
    }
    
    /**
     * 이상한 가격 패턴 체크 (999만원, 1111만원, 111만원, 1234만원, 2222만원, 3333만원, 4444만원 등)
     */
    private boolean isSuspiciousPrice(Integer price) {
        if (price == null) {
            return false;
        }
        
        String priceStr = String.valueOf(price);
        
        // 1. 같은 숫자가 반복되는 패턴 (111, 222, 333, 444, 1111, 2222, 3333, 4444 등)
        if (priceStr.length() >= 3) {
            char firstChar = priceStr.charAt(0);
            boolean allSame = true;
            for (int i = 1; i < priceStr.length(); i++) {
                if (priceStr.charAt(i) != firstChar) {
                    allSame = false;
                    break;
                }
            }
            if (allSame) {
                return true; // 111, 222, 333, 444, 1111, 2222, 3333, 4444 등
            }
        }
        
        // 2. 특정 패턴 (999만원)
        if (price == 999) {
            return true;
        }
        
        // 3. 연속된 숫자 패턴 (1234, 2345, 3456 등) - 4자리 이상
        if (priceStr.length() >= 4) {
            boolean isSequential = true;
            for (int i = 0; i < priceStr.length() - 1; i++) {
                int current = Character.getNumericValue(priceStr.charAt(i));
                int next = Character.getNumericValue(priceStr.charAt(i + 1));
                if (next != current + 1) {
                    isSequential = false;
                    break;
                }
            }
            if (isSequential) {
                return true; // 1234, 2345 등
            }
        }
        
        return false;
    }

    /**
     * 점수와 사유를 함께 담는 내부 클래스
     */
    private static class ScoreResult {
        final BigDecimal score;
        final String reason;

        ScoreResult(BigDecimal score, String reason) {
            this.score = score;
            this.reason = reason;
        }
    }

    /**
     * 모델 통계 내부 클래스
     */
    private static class ModelStatistics {
        private final String modelCode;
        private final double avgPrice;
        private final int medianPrice;
        private final int count;

        public ModelStatistics(String modelCode, double avgPrice, int medianPrice, int count) {
            this.modelCode = modelCode;
            this.avgPrice = avgPrice;
            this.medianPrice = medianPrice;
            this.count = count;
        }

        @SuppressWarnings("unused")
        public String getModelCode() { return modelCode; }
        @SuppressWarnings("unused")
        public double getAvgPrice() { return avgPrice; }
        public int getMedianPrice() { return medianPrice; }
        public int getCount() { return count; }
    }
}
