package com.carizon.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Kafka 차량 인덱스 동기화 메시지
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CarIndexSyncMessage {
    private List<Long> carIds;
}
