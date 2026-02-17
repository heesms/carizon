package com.carizon.search.kafka;

import com.carizon.search.dto.CarIndexSyncMessage;
import com.carizon.search.service.CarIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka에서 차량 인덱스 동기화 메시지를 수신해 Elasticsearch에 반영. app.kafka.enabled=true 일 때만 구독.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class CarIndexSyncConsumer {

    private final CarIndexingService indexingService;

    @Value("${app.kafka.car-sync-topic:car-index-sync}")
    private String topic;

    @KafkaListener(topics = "${app.kafka.car-sync-topic:car-index-sync}", containerFactory = "carIndexSyncListenerContainerFactory")
    public void onCarIndexSync(CarIndexSyncMessage message) {
        if (message == null || message.getCarIds() == null || message.getCarIds().isEmpty()) {
            log.warn("[CarIndexSync] ignored empty message");
            return;
        }
        List<Long> carIds = message.getCarIds();
        log.info("[CarIndexSync] received carIds count={}", carIds.size());
        for (Long carId : carIds) {
            try {
                indexingService.indexCar(carId);
            } catch (Exception e) {
                log.error("[CarIndexSync] index failed carId={}", carId, e);
                // 계속 다음 차량 처리
            }
        }
        log.debug("[CarIndexSync] done carIds count={}", carIds.size());
    }
}
