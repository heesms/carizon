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
import java.util.ArrayList;
import java.util.List;

/**
 * 임베딩 생성 서비스 (Hugging Face 또는 Ollama 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService implements EmbeddingServiceInterface {
    
    private final RagProperties ragProperties;
    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;
    
    /**
     * 텍스트를 벡터 임베딩으로 변환
     */
    public float[] generateEmbedding(String text) throws IOException {
        String provider = ragProperties.getEmbedding().getProvider();
        
        if ("huggingface".equalsIgnoreCase(provider)) {
            return generateHuggingFaceEmbedding(text);
        } else if ("ollama".equalsIgnoreCase(provider)) {
            return generateOllamaEmbedding(text);
        } else {
            throw new IllegalArgumentException("Unknown embedding provider: " + provider);
        }
    }
    
    /**
     * Hugging Face Inference API를 사용한 임베딩 생성
     */
    private float[] generateHuggingFaceEmbedding(String text) throws IOException {
        RagProperties.Embedding.HuggingFace config = ragProperties.getEmbedding().getHuggingface();
        String apiKey = config.getApiKey();
        String model = config.getModel();
        
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Hugging Face API key is not configured. Set HUGGINGFACE_API_KEY environment variable.");
        }
        
        String url = "https://api-inference.huggingface.co/pipeline/feature-extraction/" + model;
        
        Headers headers = new Headers.Builder()
                .add("Authorization", "Bearer " + apiKey)
                .add("Content-Type", "application/json")
                .build();
        
        try (Response response = httpClientService.postJson(url, new EmbeddingRequest(text), headers)) {
            if (!response.isSuccessful()) {
                throw new IOException("Hugging Face API error: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.isArray()) {
                return convertToFloatArray(jsonNode);
            } else if (jsonNode.has("error")) {
                throw new IOException("Hugging Face API error: " + jsonNode.get("error").asText());
            } else {
                throw new IOException("Unexpected response format from Hugging Face API");
            }
        }
    }
    
    /**
     * Ollama를 사용한 임베딩 생성 (로컬 실행 필요)
     */
    private float[] generateOllamaEmbedding(String text) throws IOException {
        RagProperties.Embedding.Ollama config = ragProperties.getEmbedding().getOllama();
        String baseUrl = config.getBaseUrl();
        String model = config.getModel();
        
        String url = baseUrl + "/api/embeddings";
        
        try (Response response = httpClientService.postJson(url, new OllamaEmbeddingRequest(model, text))) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama API error: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            if (jsonNode.has("embedding")) {
                return convertToFloatArray(jsonNode.get("embedding"));
            } else {
                throw new IOException("Unexpected response format from Ollama API");
            }
        }
    }
    
    private float[] convertToFloatArray(JsonNode jsonNode) {
        List<Float> list = new ArrayList<>();
        if (jsonNode.isArray()) {
            for (JsonNode node : jsonNode) {
                list.add((float) node.asDouble());
            }
        }
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }
    
    // DTO 클래스들
    private record EmbeddingRequest(String inputs) {}
    private record OllamaEmbeddingRequest(String model, String prompt) {}
}
