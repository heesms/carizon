package com.carizon.recommendation.service;

import com.carizon.common.http.HttpClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * WordPress REST API 연동 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordPressService {

    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;

    @Value("${blog.wordpress.api-url:}")
    private String wordPressApiUrl;

    @Value("${blog.wordpress.username:}")
    private String wordPressUsername;

    @Value("${blog.wordpress.password:}")
    private String wordPressPassword;

    @Value("${blog.wordpress.application-password:}")
    private String applicationPassword;

    /**
     * WordPress에 포스팅 생성
     * 
     * @param title 포스팅 제목
     * @param content 포스팅 내용 (HTML)
     * @param status 포스팅 상태 (draft, publish 등)
     * @return 생성된 포스팅 ID
     */
    public Long createPost(String title, String content, String status) throws IOException {
        if (wordPressApiUrl == null || wordPressApiUrl.isEmpty()) {
            throw new IllegalStateException("WordPress API URL이 설정되지 않았습니다. blog.wordpress.api-url 설정을 확인하세요.");
        }

        String apiUrl = wordPressApiUrl.endsWith("/") 
                ? wordPressApiUrl + "wp-json/wp/v2/posts"
                : wordPressApiUrl + "/wp-json/wp/v2/posts";

        // WordPress REST API 요청 본문
        // content에서 가장 큰 감싸는 div 제거 (예: <div>...</div> 형태)
        String cleanedContent = removeOuterDiv(content);
        
        Map<String, Object> postData = new HashMap<>();
        postData.put("title", title);
        postData.put("content", cleanedContent);
        postData.put("status", status != null ? status : "draft"); // 기본값: draft
        postData.put("format", "standard");

        // 카테고리, 태그 등 추가 가능
        // postData.put("categories", List.of(1, 2));
        // postData.put("tags", List.of("중고차", "추천"));

        String jsonBody = objectMapper.writeValueAsString(postData);

        // Basic Auth 헤더 생성
        String credentials = getCredentials();
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());

        RequestBody requestBody = RequestBody.create(
                jsonBody, 
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(apiUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .build();

        log.info("[WordPress] post create request: {}", title);
        
        try (Response response = httpClientService.getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                log.error("[WordPress] post create failed: {} - {}", response.code(), errorBody);
                throw new IOException("WordPress API 오류: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body() != null ? response.body().string() : null;
            if (responseBody == null) {
                throw new IOException("WordPress API 응답이 비어있습니다.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            Long postId = result.get("id") != null 
                    ? Long.valueOf(result.get("id").toString()) 
                    : null;

            log.info("[WordPress] post create success: postId={}, title={}", postId, title);
            return postId;
        }
    }

    /**
     * content에서 가장 큰 감싸는 div 제거
     * 예: <div>...</div> -> ...
     */
    private String removeOuterDiv(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        String trimmed = content.trim();
        
        // <div로 시작하고 </div>로 끝나는 경우 제거
        if (trimmed.startsWith("<div") && trimmed.endsWith("</div>")) {
            // <div> 태그의 끝 위치 찾기
            int divEnd = trimmed.indexOf(">");
            if (divEnd > 0) {
                // </div> 태그 제거
                String inner = trimmed.substring(divEnd + 1);
                if (inner.endsWith("</div>")) {
                    inner = inner.substring(0, inner.length() - 6).trim();
                    return inner;
                }
            }
        }
        
        return content;
    }

    /**
     * WordPress 인증 정보 생성
     * Application Password 또는 Username:Password 사용
     */
    private String getCredentials() {
        if (applicationPassword != null && !applicationPassword.isEmpty()) {
            // Application Password 사용 (권장)
            return wordPressUsername + ":" + applicationPassword;
        } else if (wordPressPassword != null && !wordPressPassword.isEmpty()) {
            // 일반 비밀번호 사용
            return wordPressUsername + ":" + wordPressPassword;
        } else {
            throw new IllegalStateException("WordPress 인증 정보가 설정되지 않았습니다. " +
                    "blog.wordpress.application-password 또는 blog.wordpress.password를 설정하세요.");
        }
    }

    /**
     * WordPress 포스팅 조회
     */
    public Map<String, Object> getPost(Long postId) throws IOException {
        String apiUrl = wordPressApiUrl.endsWith("/") 
                ? wordPressApiUrl + "wp-json/wp/v2/posts/" + postId
                : wordPressApiUrl + "/wp-json/wp/v2/posts/" + postId;

        String credentials = getCredentials();
        String authHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());

        Request request = new Request.Builder()
                .url(apiUrl)
                .get()
                .addHeader("Authorization", authHeader)
                .build();

        try (Response response = httpClientService.getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("WordPress API 오류: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : null;
            if (responseBody == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
            return result;
        }
    }
}
