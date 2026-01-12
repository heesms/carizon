package com.carizon.rag.service;

import com.carizon.dto.CarDetailRow;
import com.carizon.rag.dto.CarEmbeddingDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 차량 데이터를 RAG에 사용할 텍스트로 변환하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarTextConverterService {
    
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 차량 상세 정보를 텍스트로 변환
     */
    public String convertCarToText(CarDetailRow car) {
        StringBuilder text = new StringBuilder();
        
        text.append("차량 정보: ");
        if (car.makerName() != null) text.append(car.makerName()).append(" ");
        if (car.modelGroupName() != null) text.append(car.modelGroupName()).append(" ");
        if (car.modelName() != null) text.append(car.modelName()).append(" ");
        if (car.trimName() != null) text.append(car.trimName()).append(" ");
        text.append("\n");
        
        if (car.year() != null) {
            text.append("연식: ").append(car.year()).append("년\n");
        }
        if (car.mileage() != null) {
            text.append("주행거리: ").append(String.format("%,d", car.mileage())).append("km\n");
        }
        if (car.price() != null) {
            text.append("가격: ").append(String.format("%,d", car.price())).append("만원\n");
        }
        if (car.fuel() != null) {
            text.append("연료: ").append(car.fuel()).append("\n");
        }
        if (car.transmission() != null) {
            text.append("변속기: ").append(car.transmission()).append("\n");
        }
        if (car.color() != null) {
            text.append("색상: ").append(car.color()).append("\n");
        }
        if (car.bodyType() != null) {
            text.append("차종: ").append(car.bodyType()).append("\n");
        }
        if (car.region() != null) {
            text.append("지역: ").append(car.region()).append("\n");
        }
        if (car.status() != null) {
            text.append("판매상태: ").append(car.status()).append("\n");
        }
        
        return text.toString();
    }
    
    /**
     * 차량 ID로 CarEmbeddingDto 생성
     */
    public CarEmbeddingDto createCarEmbedding(Long carId) {
        String sql = """
            SELECT cm.car_id AS carId,
                   cm.maker_name, cm.model_group_name, cm.model_name, cm.trim_name,
                   cm.year, cm.mileage, cm.displacement, cm.fuel, cm.transmission, 
                   cm.color, cm.body_type, cm.region,
                   pc.platform_car_id AS platformCarId, pc.platform_name, 
                   pc.price, pc.status, pc.pc_url, pc.m_url
            FROM car_master cm
            LEFT JOIN platform_car pc ON pc.car_id = cm.car_id
            WHERE cm.car_id = ?
            LIMIT 1
            """;
        
        List<CarDetailRow> cars = jdbcTemplate.query(sql, (rs, rowNum) -> 
            new CarDetailRow(
                rs.getLong("carId"),
                rs.getString("maker_name"),
                rs.getString("model_group_name"),
                rs.getString("model_name"),
                rs.getString("trim_name"),
                rs.getObject("year", Integer.class),
                rs.getObject("mileage", Integer.class),
                rs.getObject("displacement", Integer.class),
                rs.getString("fuel"),
                rs.getString("transmission"),
                rs.getString("color"),
                rs.getString("body_type"),
                rs.getString("region"),
                rs.getObject("platformCarId", Long.class),
                rs.getString("platform_name"),
                rs.getObject("price", Integer.class),
                rs.getString("status"),
                rs.getString("pc_url"),
                rs.getString("m_url"),
                null
            ), carId);
        
        if (cars.isEmpty()) {
            return null;
        }
        
        CarDetailRow car = cars.get(0);
        String text = convertCarToText(car);
        
        // 메타데이터 JSON 생성
        String metadata = null;
        try {
            metadata = objectMapper.writeValueAsString(Map.of(
                "carId", car.carId(),
                "platformCarId", car.platformCarId() != null ? car.platformCarId() : 0,
                "maker", car.makerName() != null ? car.makerName() : "",
                "model", car.modelName() != null ? car.modelName() : "",
                "price", car.price() != null ? car.price() : 0,
                "year", car.year() != null ? car.year() : 0
            ));
        } catch (Exception e) {
            log.warn("Failed to create metadata JSON", e);
        }
        
        return CarEmbeddingDto.builder()
                .carId(car.carId())
                .platformCarId(car.platformCarId())
                .text(text)
                .metadata(metadata)
                .build();
    }
}
