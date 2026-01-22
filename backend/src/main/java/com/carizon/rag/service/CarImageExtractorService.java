package com.carizon.rag.service;

import com.carizon.common.http.HttpClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 플랫폼 URL에서 차량 이미지를 추출하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarImageExtractorService {
    
    private final HttpClientService httpClientService;
    
    /**
     * URL에서 차량 이미지 추출
     * @param url 플랫폼 URL (pcUrl 또는 mUrl)
     * @return 이미지 URL, 추출 실패 시 null
     */
    public String extractImageFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        
        try {
            // URL 도메인별로 다른 처리
            if (url.contains("encar.com")) {
                return extractEncarImage(url);
            } else if (url.contains("chachacha.co.kr") || url.contains("chachacha.com")) {
                return extractChachachaImage(url);
            } else if (url.contains("chutcha.co.kr") || url.contains("chutcha.com")) {
                return extractChutchaImage(url);
            } else if (url.contains("kcar.com")) {
                return extractKcarImage(url);
            } else if (url.contains("tcar.co.kr") || url.contains("tcar.com")) {
                return extractTcarImage(url);
            } else if (url.contains("charancha.co.kr") || url.contains("charancha.com")) {
                return extractCharanchaImage(url);
            }
            
            // 일반적인 HTML 파싱 시도
            return extractImageFromHtml(url);
            
        } catch (Exception e) {
            log.warn("Failed to extract image from URL: {}", url, e);
            return null;
        }
    }
    
    /**
     * 엔카 이미지 추출
     */
    private String extractEncarImage(String url) throws IOException {
        // 엔카는 API를 통해 이미지를 가져올 수 있음
        // URL에서 차량 ID 추출
        Pattern pattern = Pattern.compile("/(\\d+)(?:\\?|$)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String carId = matcher.group(1);
            // 엔카 이미지 API (예시)
            return "https://ci.encar.com/cars/" + carId + "/main.jpg";
        }
        return extractImageFromHtml(url);
    }
    
    /**
     * 차차차 이미지 추출
     */
    private String extractChachachaImage(String url) throws IOException {
        return extractImageFromHtml(url);
    }
    
    /**
     * 캐치카 이미지 추출
     */
    private String extractChutchaImage(String url) throws IOException {
        return extractImageFromHtml(url);
    }
    
    /**
     * KCar 이미지 추출
     */
    private String extractKcarImage(String url) throws IOException {
        return extractImageFromHtml(url);
    }
    
    /**
     * TCar 이미지 추출
     */
    private String extractTcarImage(String url) throws IOException {
        return extractImageFromHtml(url);
    }
    
    /**
     * 차란차 이미지 추출
     */
    private String extractCharanchaImage(String url) throws IOException {
        return extractImageFromHtml(url);
    }
    
    /**
     * HTML에서 이미지 추출 (일반적인 방법)
     */
    private String extractImageFromHtml(String url) throws IOException {
        try (Response response = httpClientService.get(url)) {
            if (!response.isSuccessful()) {
                return null;
            }
            
            String html = response.body().string();
            Document doc = Jsoup.parse(html);
            
            // 여러 선택자로 이미지 찾기
            String[] selectors = {
                "img[class*='main']",
                "img[class*='thumb']",
                "img[class*='image']",
                "img[class*='photo']",
                "img[class*='car']",
                ".main-image img",
                ".thumbnail img",
                ".car-image img",
                "meta[property='og:image']",
                "meta[name='twitter:image']"
            };
            
            for (String selector : selectors) {
                if (selector.startsWith("meta")) {
                    Element meta = doc.selectFirst(selector);
                    if (meta != null) {
                        String content = meta.attr("content");
                        if (content != null && !content.isEmpty() && isImageUrl(content)) {
                            return normalizeImageUrl(content, url);
                        }
                    }
                } else {
                    Elements imgs = doc.select(selector);
                    for (Element img : imgs) {
                        String src = img.attr("src");
                        if (src == null || src.isEmpty()) {
                            src = img.attr("data-src"); // lazy loading
                        }
                        if (src != null && !src.isEmpty() && isImageUrl(src)) {
                            return normalizeImageUrl(src, url);
                        }
                    }
                }
            }
            
            // 마지막으로 모든 img 태그에서 첫 번째 이미지
            Element firstImg = doc.selectFirst("img[src]");
            if (firstImg != null) {
                String src = firstImg.attr("src");
                if (src != null && !src.isEmpty() && isImageUrl(src)) {
                    return normalizeImageUrl(src, url);
                }
            }
        }
        
        return null;
    }
    
    /**
     * 이미지 URL인지 확인
     */
    private boolean isImageUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains(".jpg") || lower.contains(".jpeg") || 
               lower.contains(".png") || lower.contains(".webp") ||
               lower.contains(".gif") || lower.contains("image") ||
               lower.contains("photo") || lower.contains("img");
    }
    
    /**
     * 상대 URL을 절대 URL로 변환
     */
    private String normalizeImageUrl(String imgUrl, String baseUrl) {
        if (imgUrl.startsWith("http://") || imgUrl.startsWith("https://")) {
            return imgUrl;
        }
        if (imgUrl.startsWith("//")) {
            return "https:" + imgUrl;
        }
        if (imgUrl.startsWith("/")) {
            try {
                java.net.URL url = new java.net.URL(baseUrl);
                return url.getProtocol() + "://" + url.getHost() + imgUrl;
            } catch (Exception e) {
                return imgUrl;
            }
        }
        try {
            java.net.URL base = new java.net.URL(baseUrl);
            return new java.net.URL(base, imgUrl).toString();
        } catch (Exception e) {
            return imgUrl;
        }
    }
}
