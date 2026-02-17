package com.carizon.admin;

import com.carizon.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 데이터 조회 관리 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/admin/data")
@RequiredArgsConstructor
@Tag(name = "데이터 조회", description = "car_master, platform_car 데이터 조회")
public class DataAdminController {

    private final JdbcTemplate jdbc;

    @GetMapping("/car-master")
    @Operation(summary = "차량 마스터 조회", description = "car_master 테이블 데이터 조회")
    public ApiResponse<Map<String, Object>> getCarMaster(
            @RequestParam(required = false) Long carId,
            @RequestParam(required = false) String carNo,
            @RequestParam(required = false) String makerCode,
            @RequestParam(required = false) String modelCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            StringBuilder sql = new StringBuilder("""
                SELECT car_id, car_no, maker_code, model_group_code, model_code, 
                       trim_code, grade_code, year, mileage, color, transmission, 
                       fuel, adv_status, region, displacement, body_type,
                       created_at, updated_at, last_seen_date
                FROM car_master
                WHERE 1=1
                """);
            
            List<Object> params = new java.util.ArrayList<>();
            
            if (carId != null) {
                sql.append(" AND car_id = ?");
                params.add(carId);
            }
            if (carNo != null && !carNo.isEmpty()) {
                sql.append(" AND car_no = ?");
                params.add(carNo);
            }
            if (makerCode != null && !makerCode.isEmpty()) {
                sql.append(" AND maker_code = ?");
                params.add(makerCode);
            }
            if (modelCode != null && !modelCode.isEmpty()) {
                sql.append(" AND model_code = ?");
                params.add(modelCode);
            }
            
            sql.append(" ORDER BY updated_at DESC");
            sql.append(" LIMIT ? OFFSET ?");
            params.add(size);
            params.add(page * size);
            
            List<Map<String, Object>> data = jdbc.queryForList(sql.toString(), params.toArray());
            
            // 전체 개수 조회
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM car_master WHERE 1=1");
            List<Object> countParams = new java.util.ArrayList<>();
            if (carId != null) {
                countSql.append(" AND car_id = ?");
                countParams.add(carId);
            }
            if (carNo != null && !carNo.isEmpty()) {
                countSql.append(" AND car_no = ?");
                countParams.add(carNo);
            }
            if (makerCode != null && !makerCode.isEmpty()) {
                countSql.append(" AND maker_code = ?");
                countParams.add(makerCode);
            }
            if (modelCode != null && !modelCode.isEmpty()) {
                countSql.append(" AND model_code = ?");
                countParams.add(modelCode);
            }
            
            Long total = jdbc.queryForObject(countSql.toString(), Long.class, countParams.toArray());
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("content", data);
            result.put("totalElements", total);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", (int) Math.ceil(total / (double) size));
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[data] car_master fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }

    @GetMapping("/platform-car")
    @Operation(summary = "플랫폼 차량 조회", description = "platform_car 테이블 데이터 조회")
    public ApiResponse<Map<String, Object>> getPlatformCar(
            @RequestParam(required = false) Long platformCarId,
            @RequestParam(required = false) String platformName,
            @RequestParam(required = false) Long carId,
            @RequestParam(required = false) String carNo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            StringBuilder sql = new StringBuilder("""
                SELECT platform_car_id, platform_name, platform_car_key, car_no, car_id,
                       maker_code, model_code, maker_name, model_name, price, km, 
                       status, fuel, transmission, body_type, region,
                       created_at, updated_at, last_seen_date
                FROM platform_car
                WHERE 1=1
                """);
            
            List<Object> params = new java.util.ArrayList<>();
            
            if (platformCarId != null) {
                sql.append(" AND platform_car_id = ?");
                params.add(platformCarId);
            }
            if (platformName != null && !platformName.isEmpty()) {
                sql.append(" AND platform_name = ?");
                params.add(platformName);
            }
            if (carId != null) {
                sql.append(" AND car_id = ?");
                params.add(carId);
            }
            if (carNo != null && !carNo.isEmpty()) {
                sql.append(" AND car_no = ?");
                params.add(carNo);
            }
            
            sql.append(" ORDER BY updated_at DESC");
            sql.append(" LIMIT ? OFFSET ?");
            params.add(size);
            params.add(page * size);
            
            List<Map<String, Object>> data = jdbc.queryForList(sql.toString(), params.toArray());
            
            // 전체 개수 조회
            StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM platform_car WHERE 1=1");
            List<Object> countParams = new java.util.ArrayList<>();
            if (platformCarId != null) {
                countSql.append(" AND platform_car_id = ?");
                countParams.add(platformCarId);
            }
            if (platformName != null && !platformName.isEmpty()) {
                countSql.append(" AND platform_name = ?");
                countParams.add(platformName);
            }
            if (carId != null) {
                countSql.append(" AND car_id = ?");
                countParams.add(carId);
            }
            if (carNo != null && !carNo.isEmpty()) {
                countSql.append(" AND car_no = ?");
                countParams.add(carNo);
            }
            
            Long total = jdbc.queryForObject(countSql.toString(), Long.class, countParams.toArray());
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("content", data);
            result.put("totalElements", total);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", (int) Math.ceil(total / (double) size));
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[data] platform_car fetch failed", e);
            return ApiResponse.error("조회 실패: " + e.getMessage());
        }
    }
}
