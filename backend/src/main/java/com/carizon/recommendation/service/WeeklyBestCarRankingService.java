package com.carizon.recommendation.service;

import com.carizon.rag.service.LlmService;
import com.carizon.recommendation.dto.WeeklyBestCarDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
 * 1. 가격 점수 (30%): 모델별 평균 가격 대비 저렴할수록 높음
 * 2. 주행거리 점수 (25%): 낮을수록 높음 (연식 고려)
 * 3. 최신성 점수 (15%): 최근 업데이트일수록 높음
 * 4. 가격 변동 추세 (20%): 하락 추세일수록 높음
 * 5. 모델 인기도 (10%): 적정 수준의 매물 수일 때 높음
 * 
 * 카리즌 스코어 = 가중 평균 (0~100점)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyBestCarRankingService {

    private final JdbcTemplate jdbc;
    private final LlmService llmService; // Ollama를 사용한 평가 사유 생성
    
    // LLM 사용 여부 (기본값: false, true로 설정 시 Ollama 사용)
    private static final boolean USE_LLM_FOR_REASON = true;

    // 스코어 가중치 (총합 1.0) - 새로운 구조
    private static final double WEIGHT_PRICE = 0.40;      // 가격 40%
    private static final double WEIGHT_MILEAGE = 0.25;    // 주행거리 25%
    private static final double WEIGHT_AGE = 0.20;        // 연식 20%
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

    /**
     * 모델 기준 주간 Best 매물 순위 선정
     * 
     * @param modelCode 모델 코드 (null이면 전체 모델)
     * @param limit 상위 N개 (기본 10개)
     * @return 주간 Best 매물 리스트
     */
    public List<WeeklyBestCarDto> getWeeklyBestCars(String modelCode, int limit) {
        long startTime = System.currentTimeMillis();
        log.info("[주간 Best] 모델={}, limit={} 시작", modelCode, limit);

        // 1. 기본 매물 조회 (car_image_url 있는 것만, ONSALE만)
        long step1Start = System.currentTimeMillis();
        List<WeeklyBestCarDto> candidates = fetchCandidates(modelCode);
        long step1Time = System.currentTimeMillis() - step1Start;
        log.info("[주간 Best] [1단계] 후보 매물 조회 완료: {}건 (소요: {}ms)", candidates.size(), step1Time);

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 필터링 (탈락 조건 체크)
        long step2Start = System.currentTimeMillis();
        List<WeeklyBestCarDto> filteredCars = applyFilters(candidates);
        long step2Time = System.currentTimeMillis() - step2Start;
        log.info("[주간 Best] [2단계] 필터링 완료: {}건 (소요: {}ms)", filteredCars.size(), step2Time);

        if (filteredCars.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 모델별 통계 계산 (중앙값 가격 등)
        long step3Start = System.currentTimeMillis();
        Map<String, ModelStatistics> modelStats = calculateModelStatistics(filteredCars);
        long step3Time = System.currentTimeMillis() - step3Start;
        log.info("[주간 Best] [3단계] 모델별 통계 계산 완료: {}개 모델 (소요: {}ms)", modelStats.size(), step3Time);

        // 4. 각 매물에 스코어 계산 (새로운 점수 체계) - 모든 매물에 대해 스코어 계산
        long step4Start = System.currentTimeMillis();
        List<WeeklyBestCarDto> scoredCars = filteredCars.stream()
                .map(car -> calculateScores(car, modelStats, false)) // 평가 사유는 규칙 기반으로 먼저 생성
                .collect(Collectors.toList());
        long step4Time = System.currentTimeMillis() - step4Start;
        log.info("[주간 Best] [4단계] 스코어 계산 완료: {}건 (소요: {}ms, 평균: {}ms/건)", 
                scoredCars.size(), step4Time, step4Time / Math.max(1, scoredCars.size()));

        // 5. 카리즌 스코어 기준 정렬 및 순위 부여
        long step5Start = System.currentTimeMillis();
        scoredCars.sort((a, b) -> b.getCarizonScore().compareTo(a.getCarizonScore()));
        
        for (int i = 0; i < scoredCars.size(); i++) {
            scoredCars.get(i).setRank(i + 1);
        }
        long step5Time = System.currentTimeMillis() - step5Start;
        log.info("[주간 Best] [5단계] 정렬 및 순위 부여 완료 (소요: {}ms)", step5Time);

        // 6. 상위 N개만 LLM으로 평가 사유 재생성 (성능 최적화)
        long step6Start = System.currentTimeMillis();
        if (USE_LLM_FOR_REASON) {
            int llmLimit = Math.min(limit * 2, scoredCars.size()); // 상위 2배만 LLM 처리
            log.info("[주간 Best] [6단계] LLM 평가 사유 생성 시작: 상위 {}건", llmLimit);
            int successCount = 0;
            int failCount = 0;
            
            for (int i = 0; i < llmLimit; i++) {
                WeeklyBestCarDto car = scoredCars.get(i);
                long llmCallStart = System.currentTimeMillis();
                try {
                    // 스코어는 이미 계산되어 있으므로, 평가 사유만 LLM으로 재생성
                    ScoreResult priceResult = calculatePriceScoreWithReason(car.getPrice(), 
                            modelStats.get(car.getModelCode()), car.getMileage(), car.getYear());
                    ScoreResult mileageResult = calculateMileageScoreWithReason(car.getMileage(), car.getYear());
                    ScoreResult ageResult = calculateAgeScoreWithReason(car.getYear());
                    ScoreResult freshnessResult = calculateFreshnessScoreWithReason(car.getDaysSinceUpdate());
                    double penalty = calculatePenalty(car, modelStats.get(car.getModelCode()));
                    
                    String llmReason = generateCarizonScoreReasonWithLLM(car, car.getCarizonScore(), 
                            priceResult, mileageResult, ageResult, freshnessResult, penalty);
                    car.setCarizonScoreReason(llmReason);
                    long llmCallTime = System.currentTimeMillis() - llmCallStart;
                    successCount++;
                    if (i < 5) { // 처음 5개만 상세 로그
                        log.debug("[주간 Best] [6단계] LLM 호출 완료 (순위 {}): {}ms", car.getRank(), llmCallTime);
                    }
                } catch (Exception e) {
                    long llmCallTime = System.currentTimeMillis() - llmCallStart;
                    failCount++;
                    log.warn("[주간 Best] [6단계] LLM 평가 사유 생성 실패 (순위 {}, {}ms): carId={}", 
                            car.getRank(), llmCallTime, car.getCarId(), e);
                    // 기존 규칙 기반 사유 유지
                }
            }
            long step6Time = System.currentTimeMillis() - step6Start;
            log.info("[주간 Best] [6단계] LLM 평가 사유 생성 완료: 성공 {}건, 실패 {}건 (총 소요: {}ms, 평균: {}ms/건)", 
                    successCount, failCount, step6Time, step6Time / Math.max(1, llmLimit));
        } else {
            log.info("[주간 Best] [6단계] LLM 비활성화 - 규칙 기반 평가 사유 사용");
        }

        // 7. 상위 N개 반환
        long step7Start = System.currentTimeMillis();
        List<WeeklyBestCarDto> result = scoredCars.stream()
                .limit(limit)
                .collect(Collectors.toList());
        long step7Time = System.currentTimeMillis() - step7Start;
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("[주간 Best] [7단계] 결과 반환 완료 (소요: {}ms)", step7Time);
        log.info("[주간 Best] 전체 완료: {}건 선정 (총 소요: {}ms)", result.size(), totalTime);
        for (WeeklyBestCarDto car : result) {
            log.debug("  [{}위] {} {} - 카리즌 스코어: {}, 가격: {}만원, 주행: {}km", 
                    car.getRank(), car.getMakerName(), car.getModelName(),
                    car.getCarizonScore() != null ? car.getCarizonScore().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                    car.getPrice(), car.getMileage());
        }

        return result;
    }

    /**
     * 후보 매물 조회
     */
    private List<WeeklyBestCarDto> fetchCandidates(String modelCode) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
                cm.car_id,
                pc.platform_car_id,
                pc.platform_name,
                cm.maker_code,
                (SELECT maker_name FROM cz_maker WHERE maker_code = cm.maker_code) AS maker_name,
                cm.model_group_code,
                (SELECT model_group_name FROM cz_model_group WHERE maker_code = cm.maker_code AND model_group_code = cm.model_group_code) AS model_group_name,
                cm.model_code,
                (SELECT model_name FROM cz_model WHERE maker_code = cm.maker_code AND model_group_code = cm.model_group_code AND model_code = cm.model_code) AS model_name,
                cm.trim_code,
                (SELECT trim_name FROM cz_trim WHERE maker_code = cm.maker_code AND model_group_code = cm.model_group_code AND model_code = cm.model_code AND trim_code = cm.trim_code) AS trim_name,
                cm.grade_code,
                (SELECT grade_name FROM cz_grade WHERE maker_code = cm.maker_code AND model_group_code = cm.model_group_code AND model_code = cm.model_code AND trim_code = cm.trim_code AND grade_code = cm.grade_code) AS grade_name,
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
            WHERE cm.adv_status = 'ONSALE'
              AND (
                pc.status IN ('ONSALE', 'SALE', 'ADVERTISE')
                OR (pc.platform_name = 'CHUTCHA')
              )
              AND pc.price IS NOT NULL
              AND pc.price > 0
              AND pc.car_image_url IS NOT NULL
              AND pc.car_image_url != ''
              AND pc.last_seen_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
        """);

        List<Object> params = new ArrayList<>();
        
        if (modelCode != null && !modelCode.isEmpty()) {
            sql.append(" AND cm.model_code = ?");
            params.add(modelCode);
        }

        sql.append(" ORDER BY pc.last_seen_date DESC, pc.updated_at DESC");

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
     * 필터링 적용 (탈락 조건 체크)
     */
    private List<WeeklyBestCarDto> applyFilters(List<WeeklyBestCarDto> candidates) {
        return candidates.stream()
                .filter(car -> {
                    // 1. 업데이트가 너무 오래됨 (14일 이상) - 제외
                    if (car.getDaysSinceUpdate() >= MAX_DAYS_SINCE_UPDATE) {
                        log.debug("[필터] 업데이트 오래됨 제외: carId={}, days={}", 
                                car.getCarId(), car.getDaysSinceUpdate());
                        return false;
                    }
                    
                    // 2. 핵심 필드 누락 체크
                    if (car.getPrice() == null || car.getPrice() <= 0) {
                        log.debug("[필터] 가격 정보 없음 제외: carId={}", car.getCarId());
                        return false;
                    }
                    if (car.getMileage() == null || car.getMileage() < 0) {
                        log.debug("[필터] 주행거리 정보 없음 제외: carId={}", car.getCarId());
                        return false;
                    }
                    if (car.getYear() == null || car.getYear() <= 0) {
                        log.debug("[필터] 연식 정보 없음 제외: carId={}", car.getCarId());
                        return false;
                    }
                    
                    // 3. 주행거리/연식 불일치 체크 (과도하게 많은 주행거리)
                    int currentYear = LocalDate.now().getYear();
                    int carAge = Math.max(1, currentYear - car.getYear());
                    int expectedKm = carAge * IDEAL_MILEAGE_PER_YEAR;
                    double kmRatio = expectedKm > 0 ? (double) car.getMileage() / expectedKm : 0;
                    
                    if (kmRatio > MAX_KM_RATIO_FILTER) {
                        log.debug("[필터] 주행거리 과다 제외: carId={}, kmRatio={}", car.getCarId(), kmRatio);
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
            log.warn("[주간 Best] 모델 통계 없음: {}", car.getModelCode());
            return car;
        }

        // 1. 가격 점수 (0~1) - 시세 대비 저렴할수록 높음 (주행거리 고려)
        ScoreResult priceResult = calculatePriceScoreWithReason(car.getPrice(), stats, car.getMileage(), car.getYear());

        // 2. 주행거리 점수 (0~1) - 연식 대비 적정할수록 높음
        ScoreResult mileageResult = calculateMileageScoreWithReason(car.getMileage(), car.getYear());

        // 3. 연식 점수 (0~1) - 최신일수록 높음
        ScoreResult ageResult = calculateAgeScoreWithReason(car.getYear());

        // 4. 최신성 점수 (0~1) - 최근 업데이트일수록 높음
        ScoreResult freshnessResult = calculateFreshnessScoreWithReason(car.getDaysSinceUpdate());

        // 5. 패널티 계산
        double penalty = calculatePenalty(car, stats);

        // 카리즌 스코어 계산 (새로운 구조: 40% 가격, 25% 주행거리, 20% 연식, 15% 최신성 - 패널티)
        BigDecimal baseScore = priceResult.score.multiply(BigDecimal.valueOf(WEIGHT_PRICE))
                .add(mileageResult.score.multiply(BigDecimal.valueOf(WEIGHT_MILEAGE)))
                .add(ageResult.score.multiply(BigDecimal.valueOf(WEIGHT_AGE)))
                .add(freshnessResult.score.multiply(BigDecimal.valueOf(WEIGHT_FRESHNESS)))
                .multiply(BigDecimal.valueOf(100)); // 0~1을 0~100으로 변환
        
        BigDecimal carizonScore = baseScore.subtract(BigDecimal.valueOf(penalty))
                .setScale(2, RoundingMode.HALF_UP);
        
        // 최소 0점 보장
        if (carizonScore.compareTo(BigDecimal.ZERO) < 0) {
            carizonScore = BigDecimal.ZERO;
        }

        // 카리즌 스코어 종합 사유 생성 (LLM 사용 옵션 - 성능 최적화를 위해 선택적)
        String carizonReason;
        if (useLlm && USE_LLM_FOR_REASON) {
            try {
                carizonReason = generateCarizonScoreReasonWithLLM(car, carizonScore, priceResult, 
                        mileageResult, ageResult, freshnessResult, penalty);
            } catch (Exception e) {
                log.warn("[주간 Best] LLM 평가 사유 생성 실패, 규칙 기반으로 대체: carId={}", 
                        car.getCarId(), e);
                carizonReason = generateCarizonScoreReason(carizonScore, priceResult, mileageResult, 
                        ageResult, freshnessResult, penalty);
            }
        } else {
            carizonReason = generateCarizonScoreReason(carizonScore, priceResult, mileageResult, 
                    ageResult, freshnessResult, penalty);
        }

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
        
        // 점진적 점수 계산: kmRatio에 따라 점진적으로 감소
        // kmRatio가 1.0일 때 최고점, 높아질수록 점진적으로 감소
        double score;
        if (kmRatio <= 0) {
            score = 0.0;
        } else if (kmRatio <= 0.8) {
            // 매우 적은 주행거리: 약간 감점 (0.8~1.0)
            score = 0.8 + (kmRatio / 0.8) * 0.2;
        } else if (kmRatio <= 1.0) {
            // 적정 주행거리: 최고점 (1.0)
            score = 1.0;
        } else if (kmRatio <= 1.2) {
            // 약간 많은 주행거리: 약간 감점 (1.0~0.9)
            score = 1.0 - (kmRatio - 1.0) * 0.5;
        } else if (kmRatio <= 1.5) {
            // 다소 많은 주행거리: 중간 감점 (0.9~0.7)
            score = 0.9 - (kmRatio - 1.2) * (0.2 / 0.3);
        } else if (kmRatio <= 2.0) {
            // 많은 주행거리: 강한 감점 (0.7~0.4)
            score = 0.7 - (kmRatio - 1.5) * (0.3 / 0.5);
        } else {
            // 매우 많은 주행거리: 최대 감점 (0.4~0.0)
            score = Math.max(0.0, 0.4 - (kmRatio - 2.0) * 0.2);
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
     * Ollama를 사용한 평가 사유 생성 (자연스럽고 다양한 평가)
     */
    private String generateCarizonScoreReasonWithLLM(WeeklyBestCarDto car,
                                                     BigDecimal carizonScore,
                                                     ScoreResult priceResult,
                                                     ScoreResult mileageResult,
                                                     ScoreResult ageResult,
                                                     ScoreResult freshnessResult,
                                                     double penalty) throws IOException {
        // 매물 정보 요약
        String carInfo = String.format(
            "%s %s %s (%d년식), 주행거리: %,dkm, 가격: %,d만원",
            car.getMakerName() != null ? car.getMakerName() : "",
            car.getModelName() != null ? car.getModelName() : "",
            car.getTrimName() != null ? car.getTrimName() : "",
            car.getYear() != null ? car.getYear() : 0,
            car.getMileage() != null ? car.getMileage() : 0,
            car.getPrice() != null ? car.getPrice() : 0
        );
        
        // 각 항목별 평가 요약
        StringBuilder evaluationSummary = new StringBuilder();
        if (car.getRank() != null) {
            evaluationSummary.append("순위: ").append(car.getRank()).append("위\n");
        }
        evaluationSummary.append("가격 평가: ").append(priceResult.reason).append("\n");
        evaluationSummary.append("주행거리 평가: ").append(mileageResult.reason).append("\n");
        evaluationSummary.append("연식 평가: ").append(ageResult.reason).append("\n");
        evaluationSummary.append("최신성 평가: ").append(freshnessResult.reason).append("\n");
        if (penalty > 0) {
            evaluationSummary.append("참고사항: 일부 항목에서 ").append(String.format("%.0f", penalty)).append("점 감점\n");
        }
        
        // 프롬프트 생성
        String prompt = String.format("""
            다음은 중고차 매물에 대한 평가 정보입니다. 
            이 정보를 바탕으로 구매자에게 친절하고 자연스러운 한국어로만 평가 사유를 작성해주세요.
            
            매물 정보: %s
            종합 점수: %.1f점 (100점 만점)
            
            상세 평가:
            %s
            
            작성 요구사항:
            1. 반드시 한국어로만 작성 (영어 단어 사용 금지)
            2. 3줄 정도의 길이로 작성 (약 150-250자)
            3. 매물의 주요 강점을 자연스럽게 강조
            4. 약점이 있다면 신중하게 언급
            5. 구매를 고려할 수 있도록 긍정적이면서도 객관적인 톤 유지
            6. "종합 평가:", "주요 강점:" 같은 딱딱한 표현 대신 자연스러운 문장 사용
            7. 문장이 중간에 잘리지 않도록 완전한 문장으로 마무리
            
            평가 사유 (한국어로만, 3줄 정도):
            """, carInfo, carizonScore.doubleValue(), evaluationSummary.toString());
        
        String llmResponse = llmService.generateResponse(prompt);
        
        // LLM 응답 정리
        String cleaned = llmResponse.trim();
        
        // 줄바꿈 정리 (연속된 줄바꿈을 하나로)
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        
        // 문장이 중간에 잘리지 않도록 처리
        // 마지막 문장이 불완전하면 (마침표, 느낌표, 물음표로 끝나지 않으면) 이전 문장까지만 사용
        if (!cleaned.isEmpty()) {
            // 마지막 문장이 완전한지 확인
            String lastChar = cleaned.substring(cleaned.length() - 1);
            if (!lastChar.matches("[。.！!？?]")) {
                // 마지막 문장이 불완전하면 마지막 마침표 위치까지 자르기
                int lastPeriod = Math.max(
                    Math.max(cleaned.lastIndexOf("."), cleaned.lastIndexOf("。")),
                    Math.max(cleaned.lastIndexOf("!"), cleaned.lastIndexOf("！"))
                );
                if (lastPeriod > cleaned.length() * 0.5) { // 마지막 문장이 전체의 50% 이상이면
                    cleaned = cleaned.substring(0, lastPeriod + 1);
                }
            }
        }
        
        // 너무 길면 (300자 초과) 마지막 완전한 문장까지만 사용
        if (cleaned.length() > 300) {
            int lastPeriod = Math.max(
                Math.max(cleaned.lastIndexOf("."), cleaned.lastIndexOf("。")),
                Math.max(cleaned.lastIndexOf("!"), cleaned.lastIndexOf("！"))
            );
            if (lastPeriod > 100) { // 최소 100자 이상은 유지
                cleaned = cleaned.substring(0, lastPeriod + 1);
            } else {
                // 완전한 문장을 찾을 수 없으면 250자까지만 자르고 마침표 추가
                cleaned = cleaned.substring(0, 250).trim();
                if (!cleaned.endsWith(".") && !cleaned.endsWith("。")) {
                    cleaned += ".";
                }
            }
        }
        
        return cleaned;
    }
    
    /**
     * 카리즌 스코어 종합 사유 생성 (규칙 기반 - 다양하고 구체적인 평가)
     */
    private String generateCarizonScoreReason(BigDecimal carizonScore, 
                                             ScoreResult priceResult,
                                             ScoreResult mileageResult,
                                             ScoreResult ageResult,
                                             ScoreResult freshnessResult,
                                             double penalty) {
        double score = carizonScore.doubleValue();
        List<ScoreResult> results = List.of(priceResult, mileageResult, ageResult, freshnessResult);
        
        // 점수별로 정렬하여 강점/약점 파악
        List<ScoreResult> sorted = results.stream()
                .sorted((a, b) -> b.score.compareTo(a.score))
                .collect(java.util.stream.Collectors.toList());
        
        // 가장 높은 점수와 가장 낮은 점수 찾기
        ScoreResult best = sorted.get(0);
        ScoreResult worst = sorted.get(sorted.size() - 1);
        
        // 다양한 평가 템플릿 선택 (점수 구간별)
        String evaluation;
        if (score >= 85.0) {
            evaluation = getHighScoreEvaluation(best, worst, priceResult, mileageResult, ageResult, freshnessResult);
        } else if (score >= 75.0) {
            evaluation = getGoodScoreEvaluation(best, worst, priceResult, mileageResult, ageResult, freshnessResult);
        } else if (score >= 65.0) {
            evaluation = getAverageScoreEvaluation(best, worst, priceResult, mileageResult, ageResult, freshnessResult);
        } else if (score >= 50.0) {
            evaluation = getBelowAverageEvaluation(best, worst, priceResult, mileageResult, ageResult, freshnessResult);
        } else {
            evaluation = getLowScoreEvaluation(best, worst, priceResult, mileageResult, ageResult, freshnessResult);
        }
        
        // 패널티 정보 추가
        if (penalty > 0) {
            evaluation += String.format(" (참고: 일부 항목에서 %.0f점 감점)", penalty);
        }
        
        return evaluation;
    }
    
    /**
     * 고득점 매물 평가 (85점 이상)
     */
    private String getHighScoreEvaluation(ScoreResult best, ScoreResult worst,
                                         ScoreResult price, ScoreResult mileage, 
                                         ScoreResult age, ScoreResult freshness) {
        String[] templates = {
            "이 매물은 시장에서 찾기 어려운 우수한 조건을 갖추고 있습니다. ",
            "가격 대비 매우 우수한 매물로, 구매를 고려해볼 만한 가치가 있습니다. ",
            "여러 플랫폼을 비교한 결과, 이 매물이 가장 합리적인 선택지로 평가됩니다. ",
            "시세 대비 유리한 조건과 적정한 주행거리를 갖춘 매력적인 매물입니다. "
        };
        
        String base = templates[(int)(Math.random() * templates.length)];
        return base + buildDetailedReason(best, worst, price, mileage, age, freshness, true);
    }
    
    /**
     * 양호한 점수 매물 평가 (75-84점)
     */
    private String getGoodScoreEvaluation(ScoreResult best, ScoreResult worst,
                                         ScoreResult price, ScoreResult mileage, 
                                         ScoreResult age, ScoreResult freshness) {
        String[] templates = {
            "전반적으로 우수한 조건의 매물입니다. ",
            "시장 평균 대비 경쟁력 있는 매물로 평가됩니다. ",
            "가격과 주행거리 등 주요 항목에서 균형 잡힌 매물입니다. ",
            "구매를 검토해볼 만한 수준의 매물입니다. "
        };
        
        String base = templates[(int)(Math.random() * templates.length)];
        return base + buildDetailedReason(best, worst, price, mileage, age, freshness, true);
    }
    
    /**
     * 보통 점수 매물 평가 (65-74점)
     */
    private String getAverageScoreEvaluation(ScoreResult best, ScoreResult worst,
                                            ScoreResult price, ScoreResult mileage, 
                                            ScoreResult age, ScoreResult freshness) {
        String[] templates = {
            "시장 평균 수준의 매물입니다. ",
            "일부 항목에서 강점을 보이지만, 전반적으로 보통 수준입니다. ",
            "가격과 조건이 시장 평균과 유사한 매물입니다. ",
            "추가 검토가 필요한 매물입니다. "
        };
        
        String base = templates[(int)(Math.random() * templates.length)];
        return base + buildDetailedReason(best, worst, price, mileage, age, freshness, false);
    }
    
    /**
     * 평균 이하 점수 매물 평가 (50-64점)
     */
    private String getBelowAverageEvaluation(ScoreResult best, ScoreResult worst,
                                            ScoreResult price, ScoreResult mileage, 
                                            ScoreResult age, ScoreResult freshness) {
        String[] templates = {
            "시장 평균 대비 일부 항목에서 아쉬운 점이 있습니다. ",
            "가격이나 주행거리 등에서 개선 여지가 있는 매물입니다. ",
            "전반적인 조건이 시장 평균에 미치지 못하는 매물입니다. ",
            "신중한 검토가 필요한 매물입니다. "
        };
        
        String base = templates[(int)(Math.random() * templates.length)];
        return base + buildDetailedReason(best, worst, price, mileage, age, freshness, false);
    }
    
    /**
     * 저득점 매물 평가 (50점 미만)
     */
    private String getLowScoreEvaluation(ScoreResult best, ScoreResult worst,
                                        ScoreResult price, ScoreResult mileage, 
                                        ScoreResult age, ScoreResult freshness) {
        String[] templates = {
            "시장 평균 대비 여러 항목에서 불리한 조건을 보입니다. ",
            "가격, 주행거리, 연식 등 주요 항목에서 개선이 필요한 매물입니다. ",
            "구매 전 충분한 검토와 비교가 권장되는 매물입니다. ",
            "시장에서 경쟁력이 다소 낮은 매물로 평가됩니다. "
        };
        
        String base = templates[(int)(Math.random() * templates.length)];
        return base + buildDetailedReason(best, worst, price, mileage, age, freshness, false);
    }
    
    /**
     * 구체적인 평가 사유 생성
     */
    private String buildDetailedReason(ScoreResult best, ScoreResult worst,
                                      ScoreResult price, ScoreResult mileage, 
                                      ScoreResult age, ScoreResult freshness,
                                      boolean highlightStrengths) {
        StringBuilder reason = new StringBuilder();
        
        // 강점 항목 (0.7 이상) - 최신성은 워딩 제거로 제외
        List<String> strengths = new ArrayList<>();
        if (price.score.doubleValue() >= 0.7 && !price.reason.isEmpty()) {
            strengths.add(price.reason);
        }
        if (mileage.score.doubleValue() >= 0.7 && !mileage.reason.isEmpty()) {
            strengths.add(mileage.reason);
        }
        if (age.score.doubleValue() >= 0.7 && !age.reason.isEmpty()) {
            strengths.add(age.reason);
        }
        // freshness는 워딩 제거로 평가 사유에서 제외
        
        // 약점 항목 (0.5 미만) - 최신성은 워딩 제거로 제외
        List<String> weaknesses = new ArrayList<>();
        if (price.score.doubleValue() < 0.5 && !price.reason.isEmpty()) {
            weaknesses.add(price.reason);
        }
        if (mileage.score.doubleValue() < 0.5 && !mileage.reason.isEmpty()) {
            weaknesses.add(mileage.reason);
        }
        if (age.score.doubleValue() < 0.5 && !age.reason.isEmpty()) {
            weaknesses.add(age.reason);
        }
        // freshness는 워딩 제거로 평가 사유에서 제외
        
        if (highlightStrengths && !strengths.isEmpty()) {
            if (strengths.size() == 1) {
                reason.append("특히 ").append(strengths.get(0)).append("는 이 매물의 주요 강점입니다.");
            } else if (strengths.size() == 2) {
                reason.append("특히 ").append(strengths.get(0)).append("와 ").append(strengths.get(1)).append("가 이 매물의 주요 강점입니다.");
            } else {
                reason.append("특히 ").append(strengths.get(0)).append(", ").append(strengths.get(1))
                      .append(" 등이 이 매물의 주요 강점입니다.");
            }
        } else if (!strengths.isEmpty()) {
            reason.append("강점으로는 ").append(strengths.get(0));
            if (strengths.size() > 1) {
                reason.append(" 등이 있습니다");
            } else {
                reason.append("가 있습니다");
            }
        }
        
        if (!weaknesses.isEmpty() && !highlightStrengths) {
            if (reason.length() > 0) {
                reason.append(" 다만, ");
            }
            reason.append(weaknesses.get(0));
            if (weaknesses.size() > 1) {
                reason.append(" 등에서 개선 여지가 있습니다");
            } else {
                reason.append("에서 개선 여지가 있습니다");
            }
        }
        
        // 최신성 정보는 워딩 제거로 인해 평가 사유에서 제외
        
        return reason.toString();
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
