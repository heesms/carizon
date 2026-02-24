package com.carizon.search.kafka;

import com.carizon.search.dto.CarIndexSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 차량 검색 인덱스 동기화 요청을 Kafka로 전송. app.kafka.enabled=true 일 때만 빈 생성.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class CarIndexSyncProducer {

    private final KafkaTemplate<String, CarIndexSyncMessage> kafkaTemplate;

    @Value("${app.kafka.car-sync-topic:car-index-sync}")
    private String topic;

    /**
     * 지정한 차량 ID들을 검색 인덱스 동기화 토픽으로 전송 (Consumer에서 ES 인덱싱)
     */
    public void sendSyncCarIds(List<Long> carIds) {
        if (carIds == null || carIds.isEmpty()) {
            return;
        }
        try {
            kafkaTemplate.send(topic, new CarIndexSyncMessage(carIds));
            log.debug("[CarIndexSync] sent carIds count={}", carIds.size());
        } catch (Exception e) {
            log.error("[CarIndexSync] send failed: carIds={}", carIds, e);
            throw new RuntimeException("차량 인덱스 동기화 메시지 전송 실패", e);
        }
    }

    /**
     * 단일 차량 ID 동기화 요청
     */
    public void sendSyncCar(long carId) {
        sendSyncCarIds(List.of(carId));
    }
}
