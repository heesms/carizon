package com.carizon.rag.service;

import java.util.List;

/**
 * 임베딩 생성 서비스 인터페이스
 * 나중에 별도 서비스로 분리하기 쉽도록 인터페이스로 정의
 */
public interface EmbeddingServiceInterface {
    /**
     * 텍스트를 벡터 임베딩으로 변환
     */
    float[] generateEmbedding(String text) throws Exception;

    /**
     * 텍스트 목록을 배치로 벡터 임베딩으로 변환 (HTTP 왕복 최소화)
     */
    List<float[]> generateEmbeddingsBatch(List<String> texts) throws Exception;
}
