package com.carizon.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    
    private static final String LIKE_KEY_PREFIX = "carno:like:";
    private static final String USER_LIKE_KEY_PREFIX = "user:like:carno:";
    private static final String USER_LIKED_SET_PREFIX = "user:likes:carno:";
    private static final long LIKE_EXPIRE_DAYS = 365; // 1년 후 만료

    private static final String LIKE_KEY_PATTERN = LIKE_KEY_PREFIX + "*";
    private static final String USER_LIKE_KEY_PATTERN = USER_LIKE_KEY_PREFIX + "*";
    private static final String USER_LIKED_SET_PATTERN = USER_LIKED_SET_PREFIX + "*";
    private static final String LEGACY_LIKE_KEY_PATTERN = "car:like:*";
    private static final String LEGACY_USER_LIKE_KEY_PATTERN = "user:like:*";
    private static final String LEGACY_USER_LIKED_SET_PATTERN = "user:likes:*";
    
    /**
     * 차량 좋아요 토글.
     * 이미 좋아요한 상태면 취소하고, 아니면 좋아요를 추가한다.
     *
     * @param carNo 차량번호
     * @param userId 사용자 ID (브라우저 client id 또는 fallback 식별자)
     * @return 토글 후 좋아요 상태 (true=좋아요, false=취소)
     */
    public boolean toggleLike(String carNo, String userId) {
        String normalizedCarNo = normalizeCarNo(carNo);
        if (normalizedCarNo == null || userId == null || userId.isBlank()) {
            return false;
        }

        String likeKey = LIKE_KEY_PREFIX + normalizedCarNo;
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId + ":" + normalizedCarNo;
        String userLikedSetKey = USER_LIKED_SET_PREFIX + userId;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(userLikeKey))) {
            redisTemplate.delete(userLikeKey);
            redisTemplate.opsForSet().remove(likeKey, userId);
            redisTemplate.opsForSet().remove(userLikedSetKey, normalizedCarNo);
            log.debug("Like removed: carNo={}, userId={}", normalizedCarNo, userId);
            return false;
        }
        redisTemplate.opsForSet().add(likeKey, userId);
        redisTemplate.opsForValue().set(userLikeKey, "1", LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
        redisTemplate.expire(likeKey, LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForSet().add(userLikedSetKey, normalizedCarNo);
        redisTemplate.expire(userLikedSetKey, LIKE_EXPIRE_DAYS, TimeUnit.DAYS);
        log.debug("Like added: carNo={}, userId={}", normalizedCarNo, userId);
        return true;
    }
    
    /**
     * 차량의 좋아요 개수 조회
     * @param carNo 차량번호
     * @return 좋아요 개수
     */
    public long getLikeCount(String carNo) {
        String normalizedCarNo = normalizeCarNo(carNo);
        if (normalizedCarNo == null) return 0L;
        String likeKey = LIKE_KEY_PREFIX + normalizedCarNo;
        Long count = redisTemplate.opsForSet().size(likeKey);
        return count != null ? count : 0;
    }
    
    /**
     * 사용자가 해당 차량에 좋아요를 눌렀는지 확인
     * @param carNo 차량번호
     * @param userId 사용자 ID
     * @return 좋아요 여부
     */
    public boolean hasLiked(String carNo, String userId) {
        String normalizedCarNo = normalizeCarNo(carNo);
        if (normalizedCarNo == null || userId == null || userId.isBlank()) return false;
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId + ":" + normalizedCarNo;
        return Boolean.TRUE.equals(redisTemplate.hasKey(userLikeKey));
    }
    
    /**
     * 여러 차량의 좋아요 개수 일괄 조회
     * @param carNos 차량번호 목록
     * @return 차량번호별 좋아요 개수 맵
     */
    public Map<String, Long> getLikeCountsByCarNos(List<String> carNos) {
        Map<String, Long> result = new HashMap<>();
        if (carNos == null || carNos.isEmpty()) return result;
        for (String carNo : carNos) {
            String normalizedCarNo = normalizeCarNo(carNo);
            if (normalizedCarNo == null) continue;
            result.put(normalizedCarNo, getLikeCount(normalizedCarNo));
        }
        return result;
    }

    /**
     * 현재 사용자가 좋아요한 차량번호 목록 조회
     */
    public List<String> getLikedCarNos(String userId, int limit) {
        if (userId == null || userId.isBlank()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 500));

        String userLikedSetKey = USER_LIKED_SET_PREFIX + userId;
        Set<String> members = redisTemplate.opsForSet().members(userLikedSetKey);
        List<String> carNos = parseCarNoList(members);
        if (carNos.isEmpty()) return List.of();

        carNos.sort(Comparator.reverseOrder());
        if (carNos.size() > safeLimit) {
            return new ArrayList<>(carNos.subList(0, safeLimit));
        }
        return carNos;
    }

    public void removeLike(String userId, String carNo) {
        String normalizedCarNo = normalizeCarNo(carNo);
        if (normalizedCarNo == null || userId == null || userId.isBlank()) return;

        String likeKey = LIKE_KEY_PREFIX + normalizedCarNo;
        String userLikeKey = USER_LIKE_KEY_PREFIX + userId + ":" + normalizedCarNo;
        String userLikedSetKey = USER_LIKED_SET_PREFIX + userId;

        redisTemplate.delete(userLikeKey);
        redisTemplate.opsForSet().remove(likeKey, userId);
        redisTemplate.opsForSet().remove(userLikedSetKey, normalizedCarNo);
    }

    private static List<String> parseCarNoList(Set<String> values) {
        if (values == null || values.isEmpty()) return new ArrayList<>();
        List<String> result = new ArrayList<>(values.size());
        for (String value : values) {
            String normalized = normalizeCarNo(value);
            if (normalized == null) continue;
            result.add(normalized);
        }
        return result;
    }

    private static String normalizeCarNo(String carNo) {
        if (carNo == null) return null;
        String normalized = carNo.trim();
        if (normalized.isBlank()) return null;
        return normalized;
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
        long legacyCarLikeDeleted = deleteByPattern(LEGACY_LIKE_KEY_PATTERN);
        long legacyUserLikeDeleted = deleteByPattern(LEGACY_USER_LIKE_KEY_PATTERN);
        long legacyUserLikedSetDeleted = deleteByPattern(LEGACY_USER_LIKED_SET_PATTERN);
        long total = carLikeDeleted + userLikeDeleted + userLikedSetDeleted
            + legacyCarLikeDeleted + legacyUserLikeDeleted + legacyUserLikedSetDeleted;

        result.put("carLikeKeys", carLikeDeleted);
        result.put("userLikeKeys", userLikeDeleted);
        result.put("userLikedSetKeys", userLikedSetDeleted);
        result.put("legacyCarLikeKeys", legacyCarLikeDeleted);
        result.put("legacyUserLikeKeys", legacyUserLikeDeleted);
        result.put("legacyUserLikedSetKeys", legacyUserLikedSetDeleted);
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
