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
 * Chroma 벡터 DB와의 통신 서비스 (v2 API 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChromaVectorStoreService {
    
    private final RagProperties ragProperties;
    private final HttpClientService httpClientService;
    private final ObjectMapper objectMapper;
    
    /**
     * 컬렉션 UUID를 가져오거나 생성 (v2 API는 UUID 필요)
     */
    private String getOrCreateCollectionId() throws IOException {
        String collectionName = ragProperties.getChroma().getCollectionName();
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        
        // 컬렉션을 이름으로 조회 (v2 API는 이름으로도 조회 가능)
        String getUrl = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionName;
        
        try (Response response = httpClientService.get(getUrl)) {
            if (response.isSuccessful()) {
                JsonNode collection = objectMapper.readTree(response.body().string());
                if (collection.has("id")) {
                    String collectionId = collection.get("id").asText();
                    log.debug("Found existing collection: {} (ID: {})", collectionName, collectionId);
                    return collectionId;
                }
            }
        } catch (Exception e) {
            log.debug("Collection not found by name, will create: {}", collectionName);
        }
        
        // 컬렉션이 없으면 생성
        return createCollection(collectionName);
    }
    
    /**
     * 컬렉션 생성 (v2 API) - UUID 반환
     */
    private String createCollection(String collectionName) throws IOException {
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections";
        
        Map<String, Object> body = new HashMap<>();
        body.put("name", collectionName);
        // v2 API에서는 빈 metadata를 허용하지 않으므로 metadata 필드를 제거
        
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Failed to create collection: " + response.code() + " " + errorBody);
            }
            
            // 생성된 컬렉션의 UUID 반환
            JsonNode result = objectMapper.readTree(response.body().string());
            if (result.has("id")) {
                String collectionId = result.get("id").asText();
                log.info("Created Chroma collection: {} (ID: {})", collectionName, collectionId);
                return collectionId;
            } else {
                throw new IOException("Collection created but no ID returned");
            }
        }
    }
    
    /**
     * 차량 임베딩을 벡터 DB에 저장 (v2 API)
     */
    public void addCarEmbedding(CarEmbeddingDto carEmbedding) throws IOException {
        String collectionId = getOrCreateCollectionId();
        
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/add";
        
        Map<String, Object> body = new HashMap<>();
        body.put("ids", Collections.singletonList("car_" + carEmbedding.getCarId()));
        body.put("embeddings", Collections.singletonList(Arrays.asList(convertToDoubleArray(carEmbedding.getEmbedding()))));
        body.put("documents", Collections.singletonList(carEmbedding.getText()));
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("carId", String.valueOf(carEmbedding.getCarId()));
        metadata.put("platformCarId", String.valueOf(carEmbedding.getPlatformCarId()));
        
        // JSON 문자열을 파싱해서 개별 필드로 추가 (모든 필드 포함)
        if (carEmbedding.getMetadata() != null && !carEmbedding.getMetadata().isEmpty()) {
            try {
                JsonNode metadataJson = objectMapper.readTree(carEmbedding.getMetadata());
                // JSON의 모든 필드를 metadata에 추가
                metadataJson.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    if (!value.isNull()) {
                        if (value.isTextual()) {
                            metadata.put(key, value.asText());
                        } else if (value.isNumber()) {
                            metadata.put(key, String.valueOf(value.asInt()));
                        } else {
                            metadata.put(key, value.asText());
                        }
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON: {}", carEmbedding.getMetadata(), e);
            }
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
     * 벡터 유사도 검색 (v2 API)
     */
    public List<SearchResult> searchSimilar(float[] queryEmbedding, int nResults) throws IOException {
        String collectionId = getOrCreateCollectionId();
        
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/query";
        
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
    
    /**
     * 컬렉션의 임베딩 개수 조회 (v2 API)
     * 컬렉션이 없거나 조회 실패 시 0 반환
     */
    public long getCollectionCount() throws IOException {
        try {
            String collectionId = getOrCreateCollectionId();
            String tenant = ragProperties.getChroma().getTenant();
            String database = ragProperties.getChroma().getDatabase();
            String baseUrl = ragProperties.getChroma().getBaseUrl();
            String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/count";
            
            try (Response response = httpClientService.get(url)) {
                if (!response.isSuccessful()) {
                    // 404는 컬렉션이 없는 경우이므로 0 반환
                    if (response.code() == 404) {
                        log.debug("Chroma collection not found, returning 0");
                        return 0;
                    }
                    throw new IOException("Failed to get collection count: " + response.code() + " " + response.message());
                }
                
                JsonNode jsonNode = objectMapper.readTree(response.body().string());
                if (jsonNode.has("count")) {
                    return jsonNode.get("count").asLong();
                }
                return 0;
            }
        } catch (Exception e) {
            // 컬렉션 조회 실패 시에도 0 반환 (서비스 중단 방지)
            log.debug("Failed to get collection count, returning 0: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 컬렉션 정보 조회 (메타데이터 포함)
     */
    public Map<String, Object> getCollectionInfo() throws IOException {
        String collectionId = getOrCreateCollectionId();
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId;
        
        try (Response response = httpClientService.get(url)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get collection info: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            Map<String, Object> info = new HashMap<>();
            if (jsonNode.has("id")) info.put("id", jsonNode.get("id").asText());
            if (jsonNode.has("name")) info.put("name", jsonNode.get("name").asText());
            if (jsonNode.has("metadata")) info.put("metadata", jsonNode.get("metadata"));
            
            // 개수도 함께 조회
            try {
                long count = getCollectionCount();
                info.put("count", count);
            } catch (Exception e) {
                log.warn("Failed to get count: {}", e.getMessage());
            }
            
            return info;
        }
    }

    /**
     * 샘플 임베딩 데이터 조회 (메타데이터 확인용)
     */
    public List<Map<String, Object>> getSampleEmbeddings(int limit) throws IOException {
        String collectionId = getOrCreateCollectionId();
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/get";
        
        Map<String, Object> body = new HashMap<>();
        body.put("limit", limit);
        body.put("include", Arrays.asList("metadatas", "documents"));
        
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get sample embeddings: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            List<Map<String, Object>> results = new ArrayList<>();
            
            if (jsonNode.has("ids") && jsonNode.has("metadatas") && jsonNode.has("documents")) {
                JsonNode ids = jsonNode.get("ids");
                JsonNode metadatas = jsonNode.get("metadatas");
                JsonNode documents = jsonNode.get("documents");
                
                for (int i = 0; i < ids.size(); i++) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", ids.get(i).asText());
                    if (metadatas.has(i)) {
                        item.put("metadata", metadatas.get(i));
                    }
                    if (documents.has(i)) {
                        item.put("document", documents.get(i).asText());
                    }
                    results.add(item);
                }
            }
            
            return results;
        }
    }

    private List<SearchResult> parseSearchResults(JsonNode jsonNode) {
        List<SearchResult> results = new ArrayList<>();
        
        if (jsonNode.has("ids") && jsonNode.has("distances") && jsonNode.has("documents") && jsonNode.has("metadatas")) {
            JsonNode ids = jsonNode.get("ids").get(0);
            JsonNode distances = jsonNode.get("distances").get(0);
            JsonNode documents = jsonNode.get("documents").get(0);
            JsonNode metadatas = jsonNode.get("metadatas").get(0);
            
            log.info("[ChromaDB] 검색 결과: {}개", ids.size());
            
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i).asText();
                double distance = distances.get(i).asDouble();
                String document = documents.get(i).asText();
                JsonNode metadata = metadatas.get(i);
                
                // 거리를 유사도 점수로 변환
                // ChromaDB는 기본적으로 L2 distance를 사용하므로, 거리가 작을수록 유사함
                // 유사도 = 1 / (1 + distance) 또는 cosine similarity 사용 시 1 - distance
                // 하지만 distance가 1보다 크면 음수가 되므로, 안전한 변환 필요
                double similarity;
                if (distance < 0) {
                    // 음수 거리는 비정상, 0으로 처리
                    similarity = 0.0;
                    log.warn("[경고] 음수 거리 감지: id={}, distance={}", id, distance);
                } else if (distance > 1.0) {
                    // L2 distance인 경우, 1/(1+distance)로 변환
                    similarity = 1.0 / (1.0 + distance);
                } else {
                    // Cosine distance인 경우 (0~2 범위), 1 - distance로 변환
                    similarity = 1.0 - distance;
                }
                
                log.debug("  [{}] id={}, distance={}, similarity={} ({}%)", 
                    i + 1, id, distance, similarity, String.format("%.2f", similarity * 100));
                
                SearchResult result = new SearchResult();
                result.setId(id);
                result.setScore(similarity);
                result.setDocument(document);
                
                // Metadata에서 모든 필드 추출 (DB 조회 없이 사용)
                if (metadata.has("carId")) {
                    result.setCarId(Long.parseLong(metadata.get("carId").asText()));
                }
                if (metadata.has("platformCarId")) {
                    result.setPlatformCarId(Long.parseLong(metadata.get("platformCarId").asText()));
                }
                if (metadata.has("maker")) {
                    result.setMaker(metadata.get("maker").asText());
                }
                if (metadata.has("modelGroup")) {
                    result.setModelGroup(metadata.get("modelGroup").asText());
                }
                if (metadata.has("model")) {
                    result.setModel(metadata.get("model").asText());
                }
                if (metadata.has("trim")) {
                    result.setTrim(metadata.get("trim").asText());
                }
                if (metadata.has("year")) {
                    result.setYear(metadata.get("year").asInt());
                }
                if (metadata.has("mileage")) {
                    result.setMileage(metadata.get("mileage").asInt());
                }
                if (metadata.has("displacement")) {
                    result.setDisplacement(metadata.get("displacement").asInt());
                }
                if (metadata.has("fuel")) {
                    result.setFuel(metadata.get("fuel").asText());
                }
                if (metadata.has("transmission")) {
                    result.setTransmission(metadata.get("transmission").asText());
                }
                if (metadata.has("color")) {
                    result.setColor(metadata.get("color").asText());
                }
                if (metadata.has("bodyType")) {
                    result.setBodyType(metadata.get("bodyType").asText());
                }
                if (metadata.has("region")) {
                    result.setRegion(metadata.get("region").asText());
                }
                if (metadata.has("platformName")) {
                    result.setPlatformName(metadata.get("platformName").asText());
                }
                if (metadata.has("price")) {
                    result.setPrice(metadata.get("price").asInt());
                }
                if (metadata.has("status")) {
                    result.setStatus(metadata.get("status").asText());
                }
                if (metadata.has("pcUrl")) {
                    result.setPcUrl(metadata.get("pcUrl").asText());
                }
                if (metadata.has("mUrl")) {
                    result.setMUrl(metadata.get("mUrl").asText());
                }
                if (metadata.has("imageUrl")) {
                    result.setImageUrl(metadata.get("imageUrl").asText());
                } else if (metadata.has("representativeImageUrl")) {
                    result.setImageUrl(metadata.get("representativeImageUrl").asText());
                }
                
                results.add(result);
            }
        }
        
        return results;
    }

    /**
     * 여러 건을 한 번에 추가 (네트워크 왕복 최소화) (v2 API)
     */
    public void addCarEmbeddingsBatch(List<CarEmbeddingDto> items) throws IOException {
        if (items == null || items.isEmpty()) return;
        String collectionId = getOrCreateCollectionId();

        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/add";

        log.info("[ChromaDB] 배치 저장 시작: {}개", items.size());
        if (!items.isEmpty()) {
            CarEmbeddingDto first = items.get(0);
            log.info("   - 첫 번째 항목 [carId={}] Metadata: {}", first.getCarId(), first.getMetadata());
        }

        List<String> ids = new ArrayList<>(items.size());
        List<List<Double>> embeddings = new ArrayList<>(items.size());
        List<String> documents = new ArrayList<>(items.size());
        List<Map<String, String>> metadatas = new ArrayList<>(items.size());

        for (CarEmbeddingDto dto : items) {
            ids.add("car_" + dto.getCarId());
            embeddings.add(Arrays.asList(convertToDoubleArray(dto.getEmbedding())));
            documents.add(dto.getText());

            Map<String, String> metadata = new HashMap<>();
            metadata.put("carId", String.valueOf(dto.getCarId()));
            metadata.put("platformCarId", String.valueOf(dto.getPlatformCarId()));
            
            // JSON 문자열을 파싱해서 개별 필드로 추가 (모든 필드 포함)
            if (dto.getMetadata() != null && !dto.getMetadata().isEmpty()) {
                try {
                    JsonNode metadataJson = objectMapper.readTree(dto.getMetadata());
                    // JSON의 모든 필드를 metadata에 추가
                    metadataJson.fields().forEachRemaining(entry -> {
                        String key = entry.getKey();
                        JsonNode value = entry.getValue();
                        if (!value.isNull()) {
                            if (value.isTextual()) {
                                metadata.put(key, value.asText());
                            } else if (value.isNumber()) {
                                metadata.put(key, String.valueOf(value.asInt()));
                            } else {
                                metadata.put(key, value.asText());
                            }
                        }
                    });
                } catch (Exception e) {
                    log.warn("Failed to parse metadata JSON for carId {}: {}", dto.getCarId(), dto.getMetadata(), e);
                }
            }
            metadatas.add(metadata);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("ids", ids);
        body.put("embeddings", embeddings);
        body.put("documents", documents);
        body.put("metadatas", metadatas);

        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Failed to add embeddings batch: " + response.code() + " " + errorBody);
            }
            log.info("[ChromaDB] 배치 저장 완료: {}개", items.size());
        }
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
        
        // Metadata 필드들 (DB 조회 없이 사용)
        private String maker;
        private String modelGroup;
        private String model;
        private String trim;
        private Integer year;
        private Integer mileage;
        private Integer displacement;
        private String fuel;
        private String transmission;
        private String color;
        private String bodyType;
        private String region;
        private String platformName;
        private Integer price;
        private String status;
        private String pcUrl;
        private String mUrl;
        private String imageUrl;
        
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
        
        public String getMaker() { return maker; }
        public void setMaker(String maker) { this.maker = maker; }
        public String getModelGroup() { return modelGroup; }
        public void setModelGroup(String modelGroup) { this.modelGroup = modelGroup; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getTrim() { return trim; }
        public void setTrim(String trim) { this.trim = trim; }
        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        public Integer getMileage() { return mileage; }
        public void setMileage(Integer mileage) { this.mileage = mileage; }
        public Integer getDisplacement() { return displacement; }
        public void setDisplacement(Integer displacement) { this.displacement = displacement; }
        public String getFuel() { return fuel; }
        public void setFuel(String fuel) { this.fuel = fuel; }
        public String getTransmission() { return transmission; }
        public void setTransmission(String transmission) { this.transmission = transmission; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getBodyType() { return bodyType; }
        public void setBodyType(String bodyType) { this.bodyType = bodyType; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getPlatformName() { return platformName; }
        public void setPlatformName(String platformName) { this.platformName = platformName; }
        public Integer getPrice() { return price; }
        public void setPrice(Integer price) { this.price = price; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getPcUrl() { return pcUrl; }
        public void setPcUrl(String pcUrl) { this.pcUrl = pcUrl; }
        public String getMUrl() { return mUrl; }
        public void setMUrl(String mUrl) { this.mUrl = mUrl; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
}
