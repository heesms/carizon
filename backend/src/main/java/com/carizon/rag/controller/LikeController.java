package com.carizon.rag.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.notification.VisitorNotificationService;
import com.carizon.domain.mapper.CarMapper;
import com.carizon.rag.service.LikeService;
import com.carizon.search.service.ElasticsearchCarSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 좋아요 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
@Tag(name = "좋아요", description = "차량 추천 좋아요 API")
public class LikeController {
    
    private final LikeService likeService;
    private final ElasticsearchCarSearchService elasticsearchCarSearchService;
    private final CarMapper carMapper;
    private final VisitorNotificationService visitorNotificationService;
    private static final Pattern SAFE_CLIENT_ID = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");
    private static final String CLIENT_ID_COOKIE_NAME = "carizon_client_id";
    
    /**
     * 사용자 ID 생성.
     * 1) 프론트에서 전달한 브라우저 client id(X-Client-Id) 우선
     * 2) 쿠키(carizon_client_id) fallback
     * 3) 그래도 없으면 세션 ID fallback (브라우저별 분리)
     */
    private String getUserId(HttpServletRequest request) {
        String clientId = request.getHeader("X-Client-Id");
        if (clientId != null) {
            String normalized = clientId.trim();
            if (SAFE_CLIENT_ID.matcher(normalized).matches()) {
                return normalized;
            }
            log.debug("Invalid X-Client-Id received: {}", normalized);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie == null) continue;
                if (!CLIENT_ID_COOKIE_NAME.equals(cookie.getName())) continue;
                String value = cookie.getValue();
                if (value == null) continue;
                String normalized = value.trim();
                if (SAFE_CLIENT_ID.matcher(normalized).matches()) {
                    return normalized;
                }
            }
        }

        String sessionId = request.getSession(true).getId();
        return "sid_" + sessionId;
    }
    
    @PostMapping("/{carId}")
    @Operation(summary = "좋아요 토글", description = "좋아요 상태를 토글합니다. 이미 좋아요면 취소, 아니면 추가합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addLike(
            @PathVariable Long carId,
            HttpServletRequest request) {

        String userId = getUserId(request);

        // ES 조회 (carNo 추출 + 알림용 차량 정보)
        List<Map<String, Object>> carRows = (carId != null && carId > 0)
                ? elasticsearchCarSearchService.findCarsByIds(List.of(carId))
                : List.of();
        String carNo = carRows.isEmpty() ? null : normalizeCarNo(asText(carRows.get(0).get("carNo")));
        if (carNo == null) {
            carNo = resolveCarNoFromDb(carId);
            if (carNo != null && (carRows == null || carRows.isEmpty())) {
                carRows = nullSafeResolveCarRowsFromDb(carId);
            }
        }

        if (carNo == null) {
            log.warn("[like] carNo not found for carId={}, like request ignored", carId);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                "liked", false,
                "count", 0L
            )));
        }

        boolean liked = likeService.toggleLike(carNo, userId);
        long count = likeService.getLikeCount(carNo);

        Map<String, Object> result = Map.of(
            "liked", liked,
            "count", count
        );

        // Slack 알림
        try {
            Map<String, Object> car = carRows.get(0);
            String maker = asText(car.get("maker"));
            String model = asText(car.get("model"));
            Object year = car.get("year");
            Object priceMin = car.get("priceMin");
            String carInfo = String.format("carId=%d %s %s %s년식 %s만원",
                    carId,
                    maker != null ? maker : "",
                    model != null ? model : "",
                    year != null ? year : "",
                    priceMin != null ? priceMin : "-");
            String ip = VisitorNotificationService.extractClientIp(request);
            String ua = request.getHeader("User-Agent");
            visitorNotificationService.notifyUserAction(ip, ua, "❤️", "찜 토글",
                    String.format("🚗 %s\n👤 userId: %s\n상태: %s", carInfo, userId, liked ? "추가됨" : "취소됨"));
        } catch (Exception e) {
            log.warn("[slack-notify] like notify error: {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{carId}")
    @Operation(summary = "좋아요 정보 조회", description = "차량 추천의 좋아요 개수와 사용자 좋아요 여부를 조회합니다")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLikeInfo(
            @PathVariable Long carId,
            HttpServletRequest request) {
        
        String userId = getUserId(request);
        String carNo = resolveCarNo(carId);
        if (carNo == null) {
            log.warn("[like] carNo not found for carId={}, like status query ignored", carId);
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                "count", 0L,
                "liked", false
            )));
        }

        long count = likeService.getLikeCount(carNo);
        boolean liked = likeService.hasLiked(carNo, userId);
        
        Map<String, Object> result = Map.of(
            "count", count,
            "liked", liked
        );
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @PostMapping("/batch")
    @Operation(summary = "일괄 좋아요 개수 조회", description = "여러 차량의 좋아요 개수를 일괄 조회합니다")
    public ResponseEntity<ApiResponse<Map<Long, Long>>> getLikeCounts(@RequestBody Map<String, java.util.List<Long>> request) {
        java.util.List<Long> carIds = request.get("carIds");
        if (carIds == null || carIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(Map.of()));
        }

        List<Map<String, Object>> rows = elasticsearchCarSearchService.findCarsByIds(carIds);
        Map<Long, String> carNoByCarId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long foundCarId = toLong(row.get("carId"));
            String carNo = normalizeCarNo(asText(row.get("carNo")));
            if (foundCarId == null || carNo == null) continue;
            carNoByCarId.put(foundCarId, carNo);
        }
        Map<String, Long> countsByCarNo = likeService.getLikeCountsByCarNos(new ArrayList<>(carNoByCarId.values()));
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Long carId : carIds) {
            if (carId == null) continue;
            String carNo = carNoByCarId.get(carId);
            if (carNo == null) {
                counts.put(carId, 0L);
                continue;
            }
            counts.put(carId, countsByCarNo.getOrDefault(carNo, 0L));
        }
        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    @GetMapping("/me")
    @Operation(summary = "내 좋아요 목록", description = "현재 사용자가 좋아요한 차량 ID 목록과 좋아요 수를 조회합니다")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyLikes(
            HttpServletRequest request,
            @RequestParam(defaultValue = "100") int limit) {

        String userId = getUserId(request);
        List<String> carNos = likeService.getLikedCarNos(userId, limit);
        if (carNos.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                "carIds", List.of(),
                "counts", Map.of()
            )));
        }

        List<Map<String, Object>> rows = elasticsearchCarSearchService.findCarsByCarNos(carNos);
        Map<String, Map<String, Object>> rowByCarNo = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String carNo = normalizeCarNo(asText(row.get("carNo")));
            if (carNo == null) continue;
            rowByCarNo.put(carNo, row);
        }

        Map<String, Long> countsByCarNo = likeService.getLikeCountsByCarNos(carNos);
        List<Long> carIds = new ArrayList<>();
        Map<Long, Long> counts = new LinkedHashMap<>();
        int removedMissing = 0;

        for (String carNo : carNos) {
            Map<String, Object> row = rowByCarNo.get(carNo);
            if (row == null) {
                likeService.removeLike(userId, carNo);
                removedMissing++;
                continue;
            }
            Long carId = toLong(row.get("carId"));
            if (carId == null || carId <= 0) {
                likeService.removeLike(userId, carNo);
                removedMissing++;
                continue;
            }
            carIds.add(carId);
            counts.put(carId, countsByCarNo.getOrDefault(carNo, 0L));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("carIds", carIds);
        result.put("counts", counts);
        result.put("removedMissing", removedMissing);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/me/cars")
    @Operation(summary = "내 좋아요 차량 목록", description = "현재 사용자가 좋아요한 차량의 카드 목록을 조회합니다")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyLikedCars(
            HttpServletRequest request,
            @RequestParam(defaultValue = "100") int limit) {

        String userId = getUserId(request);
        List<String> carNos = likeService.getLikedCarNos(userId, limit);
        if (carNos.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        List<Map<String, Object>> rows = elasticsearchCarSearchService.findCarsByCarNos(carNos);
        Map<String, Map<String, Object>> byCarNo = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String carNo = normalizeCarNo(asText(row.get("carNo")));
            if (carNo == null) continue;
            byCarNo.put(carNo, row);
        }
        Map<String, Long> countsByCarNo = likeService.getLikeCountsByCarNos(carNos);

        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (String carNo : carNos) {
            Map<String, Object> row = byCarNo.get(carNo);
            if (row == null) {
                likeService.removeLike(userId, carNo);
                continue;
            }
            Long carId = toLong(row.get("carId"));
            if (carId == null || carId <= 0) {
                likeService.removeLike(userId, carNo);
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("carId", carId);
            item.put("maker", asText(row.get("maker")));
            item.put("model", asText(row.get("model")));
            item.put("trim", asText(row.get("trim")));
            item.put("year", row.get("year"));
            item.put("km", row.get("km"));
            item.put("priceMin", row.get("priceMin"));
            item.put("priceMax", row.get("priceMax"));
            item.put("representativeImageUrl", asText(row.get("representativeImageUrl")));
            item.put("modelCode", asText(row.get("modelCode")));
            item.put("fuel", asText(row.get("fuel")));
            item.put("region", asText(row.get("region")));
            item.put("likesCount", countsByCarNo.getOrDefault(carNo, 0L));
            out.add(item);
        }
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    private String resolveCarNo(Long carId) {
        if (carId == null || carId <= 0) return null;
        List<Map<String, Object>> rows = elasticsearchCarSearchService.findCarsByIds(List.of(carId));
        if (!rows.isEmpty()) {
            return normalizeCarNo(asText(rows.get(0).get("carNo")));
        }

        String carNo = resolveCarNoFromDb(carId);
        if (carNo != null) {
            return carNo;
        }

        rows = nullSafeResolveCarRowsFromDb(carId);
        if (rows.isEmpty()) return null;
        return normalizeCarNo(asText(rows.get(0).get("carNo")));
    }

    private List<Map<String, Object>> nullSafeResolveCarRowsFromDb(Long carId) {
        if (carId == null || carId <= 0) return List.of();
        try {
            return carMapper.selectCarsForIndexingById(Map.of("carId", carId));
        } catch (Exception e) {
            log.warn("[like] fallback db lookup failed for carId={}", carId, e);
            return List.of();
        }
    }

    private String resolveCarNoFromDb(Long carId) {
        if (carId == null || carId <= 0) return null;
        try {
            String carNo = carMapper.selectCarNoByCarId(carId);
            if (carNo != null && !carNo.isBlank()) {
                return normalizeCarNo(carNo);
            }
        } catch (Exception e) {
            log.warn("[like] fallback car_no lookup failed for carId={}", carId, e);
        }
        return null;
    }

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String asText(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String normalizeCarNo(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isBlank()) return null;
        return normalized;
    }
}
