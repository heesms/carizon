package com.carizon.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final String USER_LIKED_SET_PREFIX = "user:likes:";
    private static final long LIKE_EXPIRE_DAYS = 365; // 1년 후 만료

    private static final String LIKE_KEY_PATTERN = LIKE_KEY_PREFIX + "*";
    private static final String USER_LIKE_KEY_PATTERN = USER_LIKE_KEY_PREFIX + "*";
    private static final String USER_LIKED_SET_PATTERN = USER_LIKED_SET_PREFIX + "*";
    
    /**
     * 차량 좋아요 토글.
     * 이미 좋아요한 상태면 취소하고, 아니면 좋아요를 추가한다.
     *
     * @param carId 차량 ID
     * @param userId 사용자 ID (브라우저 client id 또는 fallback 식별자)
     * @return 토글 후 좋아요 상태 (true=좋아요, false=취소)
     */
    public boolean toggleLike(Long carId, String userId) {
        String likeKey = LIKE_KEY_PREFIX + carId;
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId + ":" + carId;
        String userLikedSetKey = USER_LIKED_SET_PREFIX + userId;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(userLikeKey))) {
            redisTemplate.delete(userLikeKey);
            redisTemplate.opsForSet().remove(likeKey, userId);
            redisTemplate.opsForSet().remove(userLikedSetKey, String.valueOf(carId));
            log.debug("Like removed: carId={}, userId={}", carId, userId);
            return false;
        }
        redisTemplate.opsForSet().add(likeKey, userId);
        redisTemplate.opsForValue().set(userLikeKey, "1", LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
        redisTemplate.expire(likeKey, LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForSet().add(userLikedSetKey, String.valueOf(carId));
        redisTemplate.expire(userLikedSetKey, LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
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
        if (carIds == null || carIds.isEmpty()) return result;
        for (Long carId : carIds) {
            if (carId == null) continue;
            result.put(carId, getLikeCount(carId));
        }
        return result;
    }

    /**
     * 현재 사용자가 좋아요한 차량 ID 목록 조회
     */
    public List<Long> getLikedCarIds(String userId, int limit) {
        if (userId == null || userId.isBlank()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 500));

        String userLikedSetKey = USER_LIKED_SET_PREFIX + userId;
        Set<String> members = redisTemplate.opsForSet().members(userLikedSetKey);
        List<Long> carIds = parseLongList(members);

        if (carIds.isEmpty()) {
            // 기존 user:like:{userId}:{carId} 키 구조 fallback
            String pattern = USER_LIKE_KEY_PREFIX + userId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    int idx = key.lastIndexOf(':');
                    if (idx < 0 || idx >= key.length() - 1) continue;
                    try {
                        long carId = Long.parseLong(key.substring(idx + 1));
                        carIds.add(carId);
                        redisTemplate.opsForSet().add(userLikedSetKey, String.valueOf(carId));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (!carIds.isEmpty()) {
                    redisTemplate.expire(userLikedSetKey, LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
                }
            }
        }

        if (carIds.isEmpty()) return List.of();

        carIds.sort(Comparator.reverseOrder());
        if (carIds.size() > safeLimit) {
            return new ArrayList<>(carIds.subList(0, safeLimit));
        }
        return carIds;
    }

    private static List<Long> parseLongList(Set<String> values) {
        if (values == null || values.isEmpty()) return new ArrayList<>();
        List<Long> result = new ArrayList<>(values.size());
        for (String value : values) {
            try {
                result.add(Long.parseLong(value));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /**
     * 재빌드 시 Redis 좋아요 전체 삭제.
     * 삭제 대상:
     * - car:like:*
     * - user:like:*
     * - user:likes:*
     *
     * @return 패턴별 삭제 건수
     */
    public Map<String, Long> clearAllLikes() {
        Map<String, Long> result = new LinkedHashMap<>();
        long carLikeDeleted = deleteByPattern(LIKE_KEY_PATTERN);
        long userLikeDeleted = deleteByPattern(USER_LIKE_KEY_PATTERN);
        long userLikedSetDeleted = deleteByPattern(USER_LIKED_SET_PATTERN);
        long total = carLikeDeleted + userLikeDeleted + userLikedSetDeleted;

        result.put("carLikeKeys", carLikeDeleted);
        result.put("userLikeKeys", userLikeDeleted);
        result.put("userLikedSetKeys", userLikedSetDeleted);
        result.put("totalDeleted", total);

        log.warn("[Like] clearAllLikes done: {}", result);
        return result;
    }

    private long deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) return 0L;
        Long deleted = redisTemplate.delete(keys);
        if (deleted != null) return deleted;
        return keys.size();
    }
}
