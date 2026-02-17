package com.carizon.recommendation.service;

import com.carizon.recommendation.dto.WeeklyBestCarDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 플랫폼 링크 전환 서비스
 * 모바일/PC 환경에 따라 적절한 링크 반환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformLinkService {

    /**
     * User-Agent 기반으로 모바일/PC 판단하여 적절한 링크 반환
     * 
     * @param car 매물 정보
     * @param userAgent User-Agent 문자열 (null이면 PC로 간주)
     * @return 적절한 플랫폼 링크
     */
    public String getPlatformLink(WeeklyBestCarDto car, String userAgent) {
        if (car == null) {
            return null;
        }

        boolean isMobile = isMobileDevice(userAgent);

        if (isMobile && car.getMUrl() != null && !car.getMUrl().isEmpty()) {
            return car.getMUrl();
        } else if (car.getPcUrl() != null && !car.getPcUrl().isEmpty()) {
            return car.getPcUrl();
        } else if (car.getMUrl() != null && !car.getMUrl().isEmpty()) {
            // PC 링크가 없으면 모바일 링크라도 반환
            return car.getMUrl();
        }

        log.warn("[platform link] no link: carId={}, platform={}", car.getCarId(), car.getPlatformName());
        return null;
    }

    /**
     * User-Agent 문자열로 모바일 기기 판단
     */
    private boolean isMobileDevice(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return false;
        }

        String ua = userAgent.toLowerCase();
        
        // 모바일 기기 패턴
        return ua.contains("mobile") 
                || ua.contains("android")
                || ua.contains("iphone")
                || ua.contains("ipad")
                || ua.contains("ipod")
                || ua.contains("blackberry")
                || ua.contains("windows phone")
                || ua.contains("opera mini");
    }

    /**
     * 워드프레스 포스팅용 링크 생성 (HTML 형식)
     * JavaScript로 클라이언트에서 모바일/PC 구분하여 적절한 URL로 이동
     * 
     * @param car 매물 정보
     * @param userAgent User-Agent (블로그 포스팅에서는 null, JavaScript로 처리)
     * @return HTML 링크 태그 (JavaScript 포함)
     */
    public String generateWordPressLink(WeeklyBestCarDto car, String userAgent) {
        if (car == null) {
            return "";
        }

        String pcUrl = car.getPcUrl();
        String mUrl = car.getMUrl();
        
        // 두 URL이 모두 없으면 빈 문자열 반환
        if ((pcUrl == null || pcUrl.isEmpty()) && (mUrl == null || mUrl.isEmpty())) {
            log.warn("[platform link] no link: carId={}, platform={}", car.getCarId(), car.getPlatformName());
            return "";
        }

        // 플랫폼 이름 가져오기
        String platformName = getPlatformDisplayName(car.getPlatformName());
        String linkText = "👉 매물 바로보기";
        if (platformName != null && !platformName.isEmpty()) {
            linkText += " (" + platformName + ")";
        }

        // JavaScript로 모바일/PC 구분하여 적절한 URL로 이동
        // data 속성에 두 URL을 모두 저장하고, 클릭 시 JavaScript로 선택
        StringBuilder html = new StringBuilder();
        html.append("<a href=\"#\" ");
        html.append("data-pc-url=\"").append(escapeHtml(pcUrl != null ? pcUrl : "")).append("\" ");
        html.append("data-m-url=\"").append(escapeHtml(mUrl != null ? mUrl : "")).append("\" ");
        html.append("onclick=\"");
        html.append("var isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent); ");
        html.append("var url = isMobile && this.getAttribute('data-m-url') ? this.getAttribute('data-m-url') : (this.getAttribute('data-pc-url') || this.getAttribute('data-m-url')); ");
        html.append("if (url) { window.open(url, '_blank', 'noopener,noreferrer'); } ");
        html.append("return false;");
        html.append("\" ");
        html.append("style=\"cursor: pointer; text-decoration: underline; color: #1890ff;\" ");
        html.append(">").append(linkText).append("</a>");

        return html.toString();
    }
    
    /**
     * HTML 이스케이프 처리
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * 플랫폼 이름을 표시용 이름으로 변환
     */
    private String getPlatformDisplayName(String platformName) {
        if (platformName == null) {
            return null;
        }
        
        return switch (platformName.toUpperCase()) {
            case "CHACHACHA" -> "KB차차차";
            case "ENCAR" -> "엔카";
            case "KCAR" -> "케이카";
            case "CHUTCHA" -> "첫차";
            case "CHARANCHA" -> "차란차";
            case "TCAR" -> "티카";
            default -> platformName;
        };
    }
}
