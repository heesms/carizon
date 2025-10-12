package com.carizon.api.service;

import com.carizon.api.dto.CodeItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CarCodeService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get all makers
     */
    public List<CodeItemDto> getMakers() {
        String sql = "SELECT maker_code, maker_name FROM cz_maker ORDER BY maker_name";
        return jdbcTemplate.query(sql, (rs, rowNum) -> 
            new CodeItemDto(rs.getString("maker_code"), rs.getString("maker_name"))
        );
    }

    /**
     * Get model groups with optional maker filter
     */
    public List<CodeItemDto> getModelGroups(String maker) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT model_group_code, model_group_name FROM cz_model_group");
        
        if (maker != null && !maker.isBlank()) {
            sql.append(" WHERE maker_code = ?");
            params.add(maker);
        }
        
        sql.append(" ORDER BY model_group_name");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> 
            new CodeItemDto(rs.getString("model_group_code"), rs.getString("model_group_name"))
        );
    }

    /**
     * Get models with optional maker and modelGroup filters
     */
    public List<CodeItemDto> getModels(String maker, String modelGroup) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT model_code, model_name FROM cz_model WHERE 1=1");
        
        if (maker != null && !maker.isBlank()) {
            sql.append(" AND maker_code = ?");
            params.add(maker);
        }
        
        if (modelGroup != null && !modelGroup.isBlank()) {
            sql.append(" AND model_group_code = ?");
            params.add(modelGroup);
        }
        
        sql.append(" ORDER BY model_name");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> 
            new CodeItemDto(rs.getString("model_code"), rs.getString("model_name"))
        );
    }

    /**
     * Get trims with optional filters
     */
    public List<CodeItemDto> getTrims(String maker, String modelGroup, String model) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT trim_code, trim_name FROM cz_trim WHERE 1=1");
        
        if (maker != null && !maker.isBlank()) {
            sql.append(" AND maker_code = ?");
            params.add(maker);
        }
        
        if (modelGroup != null && !modelGroup.isBlank()) {
            sql.append(" AND model_group_code = ?");
            params.add(modelGroup);
        }
        
        if (model != null && !model.isBlank()) {
            sql.append(" AND model_code = ?");
            params.add(model);
        }
        
        sql.append(" ORDER BY trim_name");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> 
            new CodeItemDto(rs.getString("trim_code"), rs.getString("trim_name"))
        );
    }

    /**
     * Get grades with optional filters
     */
    public List<CodeItemDto> getGrades(String maker, String modelGroup, String model, String trim) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT grade_code, grade_name FROM cz_grade WHERE 1=1");
        
        if (maker != null && !maker.isBlank()) {
            sql.append(" AND maker_code = ?");
            params.add(maker);
        }
        
        if (modelGroup != null && !modelGroup.isBlank()) {
            sql.append(" AND model_group_code = ?");
            params.add(modelGroup);
        }
        
        if (model != null && !model.isBlank()) {
            sql.append(" AND model_code = ?");
            params.add(model);
        }
        
        if (trim != null && !trim.isBlank()) {
            sql.append(" AND trim_code = ?");
            params.add(trim);
        }
        
        sql.append(" ORDER BY grade_name");

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> 
            new CodeItemDto(rs.getString("grade_code"), rs.getString("grade_name"))
        );
    }
}
