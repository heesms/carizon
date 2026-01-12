package com.carizon.rag.service;

import com.carizon.common.http.HttpClientService;
import com.carizon.rag.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM 서비스 (Ollama 또는 Hugging Face 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {
    
    private final RagProperties ragProperties;
    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;
    
    /**
     * 프롬프트를 LLM에 전달하고 응답 받기
     */
    public String generateResponse(String prompt) throws IOException {
        String provider = ragProperties.getLlm().getProvider();
        
        if ("ollama".equalsIgnoreCase(provider)) {
            return generateOllamaResponse(prompt);
        } else if ("huggingface".equalsIgnoreCase(provider)) {
            return generateHuggingFaceResponse(prompt);
        } else {
            throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        }
    }
    
    /**
     * Ollama를 사용한 응답 생성 (로컬 실행 필요)
     */
    private String generateOllamaResponse(String prompt) throws IOException {
        RagProperties.Llm.Ollama config = ragProperties.getLlm().getOllama();
        String baseUrl = config.getBaseUrl();
        String model = config.getModel();
        
        String url = baseUrl + "/api/generate";
        
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama API error: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.has("response")) {
                return jsonNode.get("response").asText();
            } else {
                throw new IOException("Unexpected response format from Ollama API");
            }
        }
    }
    
    /**
     * Hugging Face Inference API를 사용한 응답 생성
     */
    private String generateHuggingFaceResponse(String prompt) throws IOException {
        RagProperties.Llm.HuggingFace config = ragProperties.getLlm().getHuggingface();
        String apiKey = config.getApiKey();
        String model = config.getModel();
        
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Hugging Face API key is not configured. Set HUGGINGFACE_API_KEY environment variable.");
        }
        
        String url = "https://api-inference.huggingface.co/models/" + model;
        
        Map<String, Object> body = new HashMap<>();
        body.put("inputs", prompt);
        body.put("parameters", Map.of(
            "max_new_tokens", 500,
            "return_full_text", false
        ));
        
        Headers headers = new Headers.Builder()
                .add("Authorization", "Bearer " + apiKey)
                .build();
        
        try (Response response = httpClientService.postJson(url, body, headers)) {
            if (!response.isSuccessful()) {
                throw new IOException("Hugging Face API error: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.isArray() && jsonNode.size() > 0) {
                JsonNode first = jsonNode.get(0);
                if (first.has("generated_text")) {
                    return first.get("generated_text").asText();
                }
            }
            throw new IOException("Unexpected response format from Hugging Face API");
        }
    }
}
