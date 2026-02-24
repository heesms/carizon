package com.carizon.rag.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        boolean liked = likeService.toggleLike(carId, userId);
        long count = likeService.getLikeCount(carId);
        
        Map<String, Object> result = Map.of(
            "liked", liked,
            "count", count
        );
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/{carId}")
    @Operation(summary = "좋아요 정보 조회", description = "차량 추천의 좋아요 개수와 사용자 좋아요 여부를 조회합니다")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLikeInfo(
            @PathVariable Long carId,
            HttpServletRequest request) {
        
        String userId = getUserId(request);
        long count = likeService.getLikeCount(carId);
        boolean liked = likeService.hasLiked(carId, userId);
        
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
        Map<Long, Long> counts = likeService.getLikeCounts(carIds);
        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    @GetMapping("/me")
    @Operation(summary = "내 좋아요 목록", description = "현재 사용자가 좋아요한 차량 ID 목록과 좋아요 수를 조회합니다")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyLikes(
            HttpServletRequest request,
            @RequestParam(defaultValue = "100") int limit) {

        String userId = getUserId(request);
        List<Long> carIds = likeService.getLikedCarIds(userId, limit);
        Map<Long, Long> counts = likeService.getLikeCounts(carIds);

        Map<String, Object> result = Map.of(
            "carIds", carIds,
            "counts", counts
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
