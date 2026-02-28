package com.carizon.api;

import com.carizon.common.dto.ApiResponse;
import com.carizon.notification.VisitorNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 방문자 이벤트 수집 엔드포인트
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final VisitorNotificationService visitorNotificationService;

    @PostMapping("/visit")
    public ApiResponse<Void> visit(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String ip = resolveClientIp(request);
        String page    = body.getOrDefault("page", "");
        String query   = body.getOrDefault("query", "");
        String referer = body.getOrDefault("referer", request.getHeader("Referer"));
        String ua      = request.getHeader("User-Agent");

        visitorNotificationService.notifyVisit(ip, page, query, ua, referer);
        return ApiResponse.success(null);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
