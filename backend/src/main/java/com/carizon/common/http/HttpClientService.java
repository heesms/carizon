package com.carizon.common.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 통합 HTTP 클라이언트 서비스
 * OkHttp 기반으로 통일 (크롤러와 RAG 서비스 모두 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HttpClientService {
    
    private final ObjectMapper objectMapper;
    
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    /**
     * GET 요청
     */
    public Response get(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        return httpClient.newCall(request).execute();
    }
    
    /**
     * POST 요청 (JSON)
     */
    public Response postJson(String url, Object body) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(jsonBody, JSON);
        
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build();
        
        return httpClient.newCall(request).execute();
    }
    
    /**
     * POST 요청 (JSON + 헤더)
     */
    public Response postJson(String url, Object body, Headers headers) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(body);
        RequestBody requestBody = RequestBody.create(jsonBody, JSON);
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(requestBody);
        
        if (headers != null) {
            requestBuilder.headers(headers);
        } else {
            requestBuilder.addHeader("Content-Type", "application/json");
        }
        
        return httpClient.newCall(requestBuilder.build()).execute();
    }
    
    /**
     * 응답을 객체로 변환
     */
    public <T> T parseResponse(Response response, Class<T> clazz) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException("HTTP error: " + response.code() + " " + response.message());
        }
        
        String body = response.body() != null ? response.body().string() : null;
        if (body == null || body.isEmpty()) {
            return null;
        }
        
        return objectMapper.readValue(body, clazz);
    }
    
    /**
     * OkHttpClient 직접 접근 (필요한 경우)
     */
    public OkHttpClient getClient() {
        return httpClient;
    }
}
