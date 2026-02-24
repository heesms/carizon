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
    private static final int MAX_METADATA_VALUE_LEN = 1000;

    /** 모델 전용 컬렉션 ID 캐시 (전체 임베딩 시 매 건마다 HTTP 조회 방지) */
    private volatile String modelCollectionIdCache;

    /**
     * 컬렉션 UUID를 가져오거나 생성 (v2 API는 UUID 필요)
     */
    private String getOrCreateCollectionId() throws IOException {
        return getOrCreateCollectionIdByName(ragProperties.getChroma().getCollectionName());
    }

    /**
     * 모델 전용 컬렉션 UUID를 가져오거나 생성 (model_descriptions). 한 번 조회 후 캐시.
     */
    public String getOrCreateModelCollectionId() throws IOException {
        String cached = modelCollectionIdCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (modelCollectionIdCache == null) {
                modelCollectionIdCache = getOrCreateCollectionIdByName(ragProperties.getChroma().getModelCollectionName());
            }
            return modelCollectionIdCache;
        }
    }

    private String getOrCreateCollectionIdByName(String collectionName) throws IOException {
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
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
        return createCollection(collectionName);
    }

    /**
     * Chroma 컬렉션 전체 삭제 (전체 재임베딩 전용).
     * 삭제 후 다음 add 시 getOrCreateCollectionId()가 새 컬렉션을 생성한다.
     */
    public boolean deleteCollection() throws IOException {
        String collectionName = ragProperties.getChroma().getCollectionName();
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String getUrl = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionName;
        String collectionId;
        try (Response getRes = httpClientService.get(getUrl)) {
            if (!getRes.isSuccessful() || getRes.code() == 404) {
                log.info("[ChromaDB] collection not found (already empty): {}", collectionName);
                return true;
            }
            JsonNode collection = objectMapper.readTree(getRes.body().string());
            if (!collection.has("id")) {
                throw new IOException("Collection response has no id");
            }
            collectionId = collection.get("id").asText();
        }
        String deleteUrl = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId;
        try (Response response = httpClientService.delete(deleteUrl)) {
            if (response.isSuccessful()) {
                log.info("[ChromaDB] collection deleted: {}", collectionName);
                return true;
            }
            if (response.code() == 404) {
                log.info("[ChromaDB] collection not found (already empty): {}", collectionName);
                return true;
            }
            String errorBody = response.body() != null ? response.body().string() : "";
            throw new IOException("Failed to delete collection: " + response.code() + " " + errorBody);
        }
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
        Map<String, String> metadata = buildMetadataMap(carEmbedding);
        body.put("metadatas", Collections.singletonList(metadata));
        
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Failed to add embedding: " + response.code() + " " + errorBody);
            }
        }
    }

    /**
     * 모델 전용 컬렉션에 모델코드별 임베딩 1건 저장 (id = model_{modelCode}, 동일 id 재호출 시 덮어쓰기)
     */
    public void addModelEmbedding(String modelCode, String document, float[] embedding,
                                  Map<String, String> metadata) throws IOException {
        String collectionId = getOrCreateModelCollectionId();
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/add";
        Map<String, Object> body = new HashMap<>();
        body.put("ids", Collections.singletonList("model_" + modelCode));
        body.put("embeddings", Collections.singletonList(Arrays.asList(convertToDoubleArray(embedding))));
        body.put("documents", Collections.singletonList(document != null ? document : ""));
        Map<String, String> meta = new HashMap<>(metadata != null ? metadata : Map.of());
        meta.put("modelCode", modelCode);
        body.put("metadatas", Collections.singletonList(meta));
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Failed to add model embedding: " + response.code() + " " + errorBody);
            }
        }
    }

    /**
     * 모델 전용 컬렉션에서 유사도 검색 (AI 추천 시 활용)
     */
    public List<SearchResult> searchSimilarInModelCollection(float[] queryEmbedding, int nResults,
                                                            Map<String, Object> where) throws IOException {
        String collectionId = getOrCreateModelCollectionId();
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/query";
        Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", Collections.singletonList(Arrays.asList(convertToDoubleArray(queryEmbedding))));
        body.put("n_results", nResults);
        body.put("include", Arrays.asList("metadatas", "documents", "distances"));
        if (where != null && !where.isEmpty()) body.put("where", where);
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to search model collection: " + response.code() + " " + response.message());
            }
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            return parseSearchResults(jsonNode);
        }
    }
    
    /**
     * 벡터 유사도 검색 (v2 API).
     * @param where 메타데이터 필터 (예: maker=볼보), null이면 미적용
     * @param whereDocument 문서 본문 필터 (예: {"$contains": "XC60"}), null이면 미적용
     */
    public List<SearchResult> searchSimilar(float[] queryEmbedding, int nResults,
                                            Map<String, Object> where, Map<String, Object> whereDocument) throws IOException {
        String collectionId = getOrCreateCollectionId();
        
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/query";
        
        Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", Collections.singletonList(Arrays.asList(convertToDoubleArray(queryEmbedding))));
        body.put("n_results", nResults);
        body.put("include", Arrays.asList("metadatas", "documents", "distances"));
        if (where != null && !where.isEmpty()) {
            body.put("where", where);
            log.info("[ChromaDB] query with where: {}", where);
        }
        if (whereDocument != null && !whereDocument.isEmpty()) {
            body.put("where_document", whereDocument);
            log.info("[ChromaDB] query with where_document: {}", whereDocument);
        }
        
        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to search: " + response.code() + " " + response.message());
            }
            
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            return parseSearchResults(jsonNode);
        }
    }
    
    /** 벡터 유사도 검색 (where만 사용, where_document 없음) */
    public List<SearchResult> searchSimilar(float[] queryEmbedding, int nResults, Map<String, Object> where) throws IOException {
        return searchSimilar(queryEmbedding, nResults, where, null);
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

    /**
     * Chroma에 저장된 RAG 임베딩 목록 조회 (실제 들어가 있는 차량 확인용).
     * @param limit 최대 건수 (기본 100, 최대 2000)
     * @param maker 메이커 필터 (예: "현대") - null 가능
     * @param model 모델 필터 (예: "XC60") - null 가능
     * @param modelGroup 모델그룹 필터 - null 가능
     * @return id, carId, maker, model, status, platformName 등 메타데이터 + document 요약
     */
    public List<Map<String, Object>> listEmbeddings(
            int limit,
            String maker,
            String model,
            String modelGroup,
            String platformName,
            String status,
            String bodyType,
            Long carId) throws IOException {
        String collectionId = getOrCreateCollectionId();
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/get";

        int cappedLimit = Math.min(Math.max(limit, 1), 2000);
        Map<String, Object> body = new HashMap<>();
        body.put("limit", cappedLimit);
        body.put("include", Arrays.asList("metadatas", "documents"));
        Map<String, Object> where = buildWhereForList(maker, model, modelGroup, platformName, status, bodyType, carId);
        if (where != null && !where.isEmpty()) {
            body.put("where", where);
        }

        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Chroma get failed: " + response.code() + " " + response.message());
            }
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            List<Map<String, Object>> results = new ArrayList<>();
            if (jsonNode.has("ids") && jsonNode.has("metadatas") && jsonNode.has("documents")) {
                JsonNode ids = jsonNode.get("ids");
                JsonNode metadatas = jsonNode.get("metadatas");
                JsonNode documents = jsonNode.get("documents");
                for (int i = 0; i < ids.size(); i++) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", ids.get(i).asText());
                    if (metadatas.has(i)) {
                        JsonNode meta = metadatas.get(i);
                        if (meta.isObject()) {
                            Map<String, Object> flat = new LinkedHashMap<>();
                            meta.fields().forEachRemaining(e -> {
                                String k = e.getKey();
                                JsonNode v = e.getValue();
                                flat.put(k, v.isTextual() ? v.asText() : v.isNumber() ? v.asInt() : v.toString());
                            });
                            item.put("carId", flat.get("carId"));
                            item.put("maker", flat.get("maker"));
                            item.put("model", flat.get("model"));
                            item.put("modelGroup", flat.get("modelGroup"));
                            item.put("trim", flat.get("trim"));
                            item.put("status", flat.get("status"));
                            item.put("platformName", flat.get("platformName"));
                            item.put("price", flat.get("price"));
                            item.put("year", flat.get("year"));
                            item.put("metadata", flat);
                        }
                    }
                    if (documents.has(i)) {
                        String doc = documents.get(i).asText();
                        item.put("documentPreview", doc.length() > 200 ? doc.substring(0, 200) + "..." : doc);
                    }
                    results.add(item);
                }
            }
            log.info("[ChromaDB] listEmbeddings: limit={}, maker={}, model={}, modelGroup={}, platformName={}, status={}, bodyType={}, carId={}, returned={}",
                    cappedLimit, maker, model, modelGroup, platformName, status, bodyType, carId, results.size());
            return results;
        }
    }

    /**
     * Chroma metadata where 조건으로 직접 조회 (디버깅/운영 확인용).
     * 예: {"$and":[{"platformName":{"$eq":"ENCAR"}},{"status":{"$eq":"ONSALE"}}]}
     */
    public List<Map<String, Object>> searchEmbeddingsByMetadata(Map<String, Object> where, int limit) throws IOException {
        String collectionId = getOrCreateCollectionId();
        String tenant = ragProperties.getChroma().getTenant();
        String database = ragProperties.getChroma().getDatabase();
        String baseUrl = ragProperties.getChroma().getBaseUrl();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + collectionId + "/get";

        int cappedLimit = Math.min(Math.max(limit, 1), 2000);
        Map<String, Object> body = new HashMap<>();
        body.put("limit", cappedLimit);
        body.put("include", Arrays.asList("metadatas", "documents"));
        if (where != null && !where.isEmpty()) {
            body.put("where", where);
        }

        try (Response response = httpClientService.postJson(url, body)) {
            if (!response.isSuccessful()) {
                throw new IOException("Chroma metadata search failed: " + response.code() + " " + response.message());
            }
            JsonNode jsonNode = objectMapper.readTree(response.body().string());
            List<Map<String, Object>> results = new ArrayList<>();
            if (jsonNode.has("ids") && jsonNode.has("metadatas") && jsonNode.has("documents")) {
                JsonNode ids = jsonNode.get("ids");
                JsonNode metadatas = jsonNode.get("metadatas");
                JsonNode documents = jsonNode.get("documents");
                for (int i = 0; i < ids.size(); i++) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", ids.get(i).asText());
                    if (metadatas.has(i)) {
                        JsonNode meta = metadatas.get(i);
                        if (meta.isObject()) {
                            Map<String, Object> flat = new LinkedHashMap<>();
                            meta.fields().forEachRemaining(e -> {
                                String k = e.getKey();
                                JsonNode v = e.getValue();
                                flat.put(k, v.isTextual() ? v.asText() : v.isNumber() ? v.asInt() : v.toString());
                            });
                            item.put("metadata", flat);
                            item.put("carId", flat.get("carId"));
                            item.put("maker", flat.get("maker"));
                            item.put("model", flat.get("model"));
                            item.put("status", flat.get("status"));
                            item.put("platformName", flat.get("platformName"));
                        }
                    }
                    if (documents.has(i)) {
                        String doc = documents.get(i).asText();
                        item.put("documentPreview", doc.length() > 200 ? doc.substring(0, 200) + "..." : doc);
                    }
                    results.add(item);
                }
            }
            log.info("[ChromaDB] metadata search: limit={}, where={}, returned={}", cappedLimit, where, results.size());
            return results;
        }
    }

    /** listEmbeddings용 where 조건 (maker / model / modelGroup 조합) */
    private Map<String, Object> buildWhereForList(
            String maker,
            String model,
            String modelGroup,
            String platformName,
            String status,
            String bodyType,
            Long carId) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (maker != null && !maker.isBlank()) {
            conditions.add(Map.of("maker", Map.of("$eq", maker.trim())));
        }
        if (model != null && !model.isBlank()) {
            conditions.add(Map.of("model", Map.of("$eq", model.trim())));
        }
        if (modelGroup != null && !modelGroup.isBlank()) {
            conditions.add(Map.of("modelGroup", Map.of("$eq", modelGroup.trim())));
        }
        if (platformName != null && !platformName.isBlank()) {
            conditions.add(Map.of("platformName", Map.of("$eq", platformName.trim())));
        }
        if (status != null && !status.isBlank()) {
            conditions.add(Map.of("status", Map.of("$eq", status.trim())));
        }
        if (bodyType != null && !bodyType.isBlank()) {
            conditions.add(Map.of("bodyType", Map.of("$eq", bodyType.trim())));
        }
        if (carId != null) {
            conditions.add(Map.of("carId", Map.of("$eq", String.valueOf(carId))));
        }
        if (conditions.isEmpty()) return null;
        if (conditions.size() == 1) return conditions.get(0);
        return Map.of("$and", conditions);
    }

    private List<SearchResult> parseSearchResults(JsonNode jsonNode) {
        List<SearchResult> results = new ArrayList<>();
        
        if (jsonNode.has("ids") && jsonNode.has("distances") && jsonNode.has("documents") && jsonNode.has("metadatas")) {
            JsonNode ids = jsonNode.get("ids").get(0);
            JsonNode distances = jsonNode.get("distances").get(0);
            JsonNode documents = jsonNode.get("documents").get(0);
            JsonNode metadatas = jsonNode.get("metadatas").get(0);
            
            log.info("[ChromaDB] search results: {}", ids.size());
            
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i).asText();
                double distance = distances.get(i).asDouble();
                String document = documents.get(i).asText();
                JsonNode metadata = metadatas.get(i);
                
                // Chroma가 준 거리(distance) → 유사도(0~1) 변환
                // 기본 L2 사용 시, 정규화된 벡터면 L2² = 2(1 - cos_sim) → 유사도 = 1 - distance²/2 (0~1, 동일하면 100%)
                // cosine 공간이면 거리 0~2 → 유사도 = 1 - distance/2
                // 단위 맞춤: "XC60" vs XC60 문서면 유사도 50% 이상 나오도록
                double similarity;
                if (distance < 0) {
                    similarity = 0.0;
                    log.warn("[ChromaDB] negative distance: id={}, distance={}", id, distance);
                } else if (distance <= 1.5) {
                    // 정규화 L2 구간: 유사도 = 1 - d²/2 (d=0→100%, d=1→50%, d=√2→0%)
                    similarity = Math.max(0.0, 1.0 - (distance * distance) / 2.0);
                } else if (distance <= 2.0) {
                    // cosine 거리 구간
                    similarity = Math.max(0.0, 1.0 - distance / 2.0);
                } else {
                    similarity = 1.0 / (1.0 + distance);
                }
                
                log.debug("  [{}] id={}, distance={}, similarity={} ({}%)", 
                    i + 1, id, distance, similarity, String.format("%.1f", similarity * 100));
                
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
                if (metadata.has("bodyTypeCategory")) {
                    result.setBodyTypeCategory(metadata.get("bodyTypeCategory").asText());
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
                if (metadata.has("optionArray")) {
                    result.setOptionArray(metadata.get("optionArray").asText());
                }
                if (metadata.has("selOptionArray")) {
                    result.setSelOptionArray(metadata.get("selOptionArray").asText());
                }
                if (metadata.has("myAccidentCnt")) {
                    result.setMyAccidentCnt(metadata.get("myAccidentCnt").asInt());
                }
                if (metadata.has("floodTotalLossCnt")) {
                    result.setFloodTotalLossCnt(metadata.get("floodTotalLossCnt").asInt());
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

        log.debug("[ChromaDB] batch save start: {}", items.size());

        List<String> ids = new ArrayList<>(items.size());
        List<List<Double>> embeddings = new ArrayList<>(items.size());
        List<String> documents = new ArrayList<>(items.size());
        List<Map<String, String>> metadatas = new ArrayList<>(items.size());

        for (CarEmbeddingDto dto : items) {
            ids.add("car_" + dto.getCarId());
            embeddings.add(Arrays.asList(convertToDoubleArray(dto.getEmbedding())));
            documents.add(dto.getText());

            metadatas.add(buildMetadataMap(dto));
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
            log.debug("[ChromaDB] batch save done: {}", items.size());
        }
    }

    private Map<String, String> buildMetadataMap(CarEmbeddingDto dto) {
        Map<String, String> metadata = new HashMap<>();
        if (dto.getCarId() != null) {
            metadata.put("carId", String.valueOf(dto.getCarId()));
        }
        if (dto.getPlatformCarId() != null) {
            metadata.put("platformCarId", String.valueOf(dto.getPlatformCarId()));
        }

        Map<String, String> flatMap = dto.getMetadataMap();
        if (flatMap != null && !flatMap.isEmpty()) {
            flatMap.forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    metadata.put(k, truncateMetadataValue(v));
                }
            });
            return metadata;
        }

        if (dto.getMetadata() != null && !dto.getMetadata().isEmpty()) {
            try {
                JsonNode metadataJson = objectMapper.readTree(dto.getMetadata());
                metadataJson.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    if (key == null || value == null || value.isNull()) return;
                    if (value.isTextual()) {
                        metadata.put(key, truncateMetadataValue(value.asText()));
                    } else if (value.isNumber()) {
                        metadata.put(key, String.valueOf(value.numberValue()));
                    } else {
                        metadata.put(key, truncateMetadataValue(value.asText()));
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON for carId {}: {}", dto.getCarId(), dto.getMetadata(), e);
            }
        }
        return metadata;
    }

    private String truncateMetadataValue(String value) {
        if (value == null || value.length() <= MAX_METADATA_VALUE_LEN) {
            return value;
        }
        return value.substring(0, MAX_METADATA_VALUE_LEN);
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
        private String bodyTypeCategory;
        private String region;
        private String platformName;
        private Integer price;
        private String status;
        private String pcUrl;
        private String mUrl;
        private String imageUrl;
        private String optionArray;
        private String selOptionArray;
        private Integer myAccidentCnt;
        private Integer floodTotalLossCnt;
        
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
        public String getBodyTypeCategory() { return bodyTypeCategory; }
        public void setBodyTypeCategory(String bodyTypeCategory) { this.bodyTypeCategory = bodyTypeCategory; }
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
        public String getOptionArray() { return optionArray; }
        public void setOptionArray(String optionArray) { this.optionArray = optionArray; }
        public String getSelOptionArray() { return selOptionArray; }
        public void setSelOptionArray(String selOptionArray) { this.selOptionArray = selOptionArray; }
        public Integer getMyAccidentCnt() { return myAccidentCnt; }
        public void setMyAccidentCnt(Integer myAccidentCnt) { this.myAccidentCnt = myAccidentCnt; }
        public Integer getFloodTotalLossCnt() { return floodTotalLossCnt; }
        public void setFloodTotalLossCnt(Integer floodTotalLossCnt) { this.floodTotalLossCnt = floodTotalLossCnt; }
    }
}
