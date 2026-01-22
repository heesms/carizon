package com.carizon.rag.controller;

import com.carizon.common.dto.ApiResponse;
import com.carizon.rag.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    
    /**
     * 사용자 ID 생성 (IP 주소 기반)
     */
    private String getUserId(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            ip = forwarded.split(",")[0].trim();
        }
        return ip;
    }
    
    @PostMapping("/{carId}")
    @Operation(summary = "좋아요 토글", description = "차량 추천에 좋아요를 추가하거나 제거합니다")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleLike(
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
}
