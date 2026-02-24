package com.carizon.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 차량 임베딩 데이터
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarEmbeddingDto {
    private Long carId;
    private Long platformCarId;
    private String text; // 임베딩을 생성할 텍스트
    private float[] embedding; // 벡터 임베딩
    private String metadata; // JSON 형태의 메타데이터
    private Map<String, String> metadataMap; // Chroma 저장용 평탄 메타데이터 (문자열 키/값)
}
