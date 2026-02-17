package com.carizon.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 좋아요 서비스 (Redis 기반)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String LIKE_KEY_PREFIX = "car:like:";
    private static final String USER_LIKE_KEY_PREFIX = "user:like:";
    private static final long LIKE_EXPIRE_DAYS = 365; // 1년 후 만료
    
    /**
     * 차량에 좋아요 추가 (한 번만, 취소 없음). 이미 눌렀으면 그대로 유지.
     * @param carId 차량 ID
     * @param userId 사용자 ID (IP 주소 또는 세션 ID)
     * @return 항상 true (좋아요 상태)
     */
    public boolean addLikeOnce(Long carId, String userId) {
        String likeKey = LIKE_KEY_PREFIX + carId;
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId + ":" + carId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(userLikeKey))) {
            log.debug("Like already exists: carId={}, userId={}", carId, userId);
            return true;
        }
        redisTemplate.opsForSet().add(likeKey, userId);
        redisTemplate.opsForValue().set(userLikeKey, "1", LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
        redisTemplate.expire(likeKey, LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
        log.debug("Like added: carId={}, userId={}", carId, userId);
        return true;
    }
    
    /**
     * 차량의 좋아요 개수 조회
     * @param carId 차량 ID
     * @return 좋아요 개수
     */
    public long getLikeCount(Long carId) {
        String likeKey = LIKE_KEY_PREFIX + carId;
        Long count = redisTemplate.opsForSet().size(likeKey);
        return count != null ? count : 0;
    }
    
    /**
     * 사용자가 해당 차량에 좋아요를 눌렀는지 확인
     * @param carId 차량 ID
     * @param userId 사용자 ID
     * @return 좋아요 여부
     */
    public boolean hasLiked(Long carId, String userId) {
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId + ":" + carId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(userLikeKey));
    }
    
    /**
     * 여러 차량의 좋아요 개수 일괄 조회
     * @param carIds 차량 ID 목록
     * @return 차량 ID별 좋아요 개수 맵
     */
    public java.util.Map<Long, Long> getLikeCounts(java.util.List<Long> carIds) {
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        for (Long carId : carIds) {
            result.put(carId, getLikeCount(carId));
        }
        return result;
    }
}
