package com.carizon.rag.service;

import com.carizon.common.http.HttpClientService;
import com.carizon.rag.config.RagProperties;
import com.carizon.rag.dto.CarEmbeddingDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Chroma 벡터 DB와의 통신 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChromaVectorStoreService {
    
    private final RagProperties ragProperties;
    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;
    
    /**
     * 컬렉션이 존재하는지 확인하고 없으면 생성
     */
    public void ensureCollection() throws IOException {
        String collectionName = ragProperties.getChroma().getCollectionName();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        
        // 컬렉션 목록 조회
        String listUrl = baseUrl + "/api/v1/collections";
        
        try (Response response = httpClientService.get(listUrl)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to list collections: " + response.code());
            }
            
            JsonNode collections = objectMapper.readTree(response.body().string());
            boolean exists = false;
            
            if (collections.has("data")) {
                for (JsonNode collection : collections.get("data")) {
                    if (collection.has("name") && collectionName.equals(collection.get("name").asText())) {
                        exists = true;
                        break;
                    }
                }
            }
            
            if (!exists) {
                // 컬렉션 생성
                createCollection(collectionName);
            }
        }
    }
    
    /**
     * 컬렉션 생성
     */
    private void createCollection(String collectionName) throws IOException {
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v1/collections";
        
        Map<String, Object> body = new HashMap<>();
        body.put("name", collectionName);
        body.put("metadata", new HashMap<>());
        
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to create collection: " + response.code() + " " + response.message());
            }
            log.info("Created Chroma collection: {}", collectionName);
        }
    }
    
    /**
     * 차량 임베딩을 벡터 DB에 저장
     */
    public void addCarEmbedding(CarEmbeddingDto carEmbedding) throws IOException {
        ensureCollection();
        
        String collectionName = ragProperties.getChroma().getCollectionName();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v1/collections/" + collectionName + "/add";
        
        Map<String, Object> body = new HashMap<>();
        body.put("ids", Collections.singletonList("car_" + carEmbedding.getCarId()));
        body.put("embeddings", Collections.singletonList(Arrays.asList(convertToDoubleArray(carEmbedding.getEmbedding()))));
        body.put("documents", Collections.singletonList(carEmbedding.getText()));
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("carId", String.valueOf(carEmbedding.getCarId()));
        metadata.put("platformCarId", String.valueOf(carEmbedding.getPlatformCarId()));
        if (carEmbedding.getMetadata() != null) {
            metadata.put("metadata", carEmbedding.getMetadata());
        }
        body.put("metadatas", Collections.singletonList(metadata));
        
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Failed to add embedding: " + response.code() + " " + errorBody);
            }
        }
    }
    
    /**
     * 벡터 유사도 검색
     */
    public List<SearchResult> searchSimilar(float[] queryEmbedding, int nResults) throws IOException {
        ensureCollection();
        
        String collectionName = ragProperties.getChroma().getCollectionName();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v1/collections/" + collectionName + "/query";
        
        Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", Collections.singletonList(Arrays.asList(convertToDoubleArray(queryEmbedding))));
        body.put("n_results", nResults);
        
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to search: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            return parseSearchResults(jsonNode);
        }
    }
    
    private List<SearchResult> parseSearchResults(JsonNode jsonNode) {
        List<SearchResult> results = new ArrayList<>();
        
        if (jsonNode.has("ids") && jsonNode.has("distances") && jsonNode.has("documents") && jsonNode.has("metadatas")) {
            JsonNode ids = jsonNode.get("ids").get(0);
            JsonNode distances = jsonNode.get("distances").get(0);
            JsonNode documents = jsonNode.get("documents").get(0);
            JsonNode metadatas = jsonNode.get("metadatas").get(0);
            
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i).asText();
                double distance = distances.get(i).asDouble();
                String document = documents.get(i).asText();
                JsonNode metadata = metadatas.get(i);
                
                SearchResult result = new SearchResult();
                result.setId(id);
                result.setScore(1.0 - distance); // 거리를 유사도 점수로 변환 (1 - distance)
                result.setDocument(document);
                result.setCarId(metadata.has("carId") ? Long.parseLong(metadata.get("carId").asText()) : null);
                result.setPlatformCarId(metadata.has("platformCarId") ? Long.parseLong(metadata.get("platformCarId").asText()) : null);
                
                results.add(result);
            }
        }
        
        return results;
    }
    
    private Double[] convertToDoubleArray(float[] floatArray) {
        Double[] doubleArray = new Double[floatArray.length];
        for (int i = 0; i < floatArray.length; i++) {
            doubleArray[i] = (double) floatArray[i];
        }
        return doubleArray;
    }
    
    /**
     * 검색 결과 DTO
     */
    public static class SearchResult {
        private String id;
        private Double score;
        private String document;
        private Long carId;
        private Long platformCarId;
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
        public String getDocument() { return document; }
        public void setDocument(String document) { this.document = document; }
        public Long getCarId() { return carId; }
        public void setCarId(Long carId) { this.carId = carId; }
        public Long getPlatformCarId() { return platformCarId; }
        public void setPlatformCarId(Long platformCarId) { this.platformCarId = platformCarId; }
    }
}
