package com.carizon.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 차차차 코드 동기화
 * - cz_maker         : /public/search/carMaker.json
 * - cz_model_group   : /public/search/carClass.json?makerCode=...
 * - cz_model(=car)   : /public/search/carName.json?makerCode=...&classCode=...
 * - cz_trim          : /public/search/carModel.json?makerCode=...&classCode=...&carCode=...  → codeModel[].modelCode
 * - cz_grade         : /public/search/carModel.json?makerCode=...&classCode=...&carCode=...  → codeGrade[].gradeCode (model별)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChachaCodeSyncService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper om = new ObjectMapper();

    private final WebClient webClient;
    private static final String HOST = "https://www.kbchachacha.com";



    /* ======================= PUBLIC ENTRY ======================= */

    public void syncAll() {
        List<Maker> makers = fetchMakers();
        upsertMakers(makers);

        for (Maker mk : makers) {
            List<ModelGroup> groups = fetchGroups(mk.code);
            upsertGroups(mk.code, groups);

            for (ModelGroup mg : groups) {
                List<CarModel> cars = fetchCars(mk.code, mg.code);
                upsertModels(mk.code, mg.code, cars);

                for (CarModel car : cars) {
                    // === 중요: TRIM = codeModel, GRADE = codeGrade ===
                    CodeModelGrade cmg = fetchCodeModelGrade(mk.code, mg.code, car.code);

                    // 1) TRIM: codeModel 기준
                    List<Trim> trims = cmg.trims();
                    upsertTrims(mk.code, mg.code, car.code, trims);

                    // 2) GRADE: codeGrade 기준
                    List<Grade> grades = cmg.grades();
                    upsertGrades(mk.code, mg.code, car.code, grades);
                }
            }
        }
        log.info("[CHACHA] full sync done.");
    }

    /* ======================= FETCHERS ======================= */

    private List<Maker> fetchMakers() {
        JsonNode root = get("/public/search/carMaker.json");
        List<Maker> out = new ArrayList<>();
        for (String key : List.of("국산", "수입")) {
            JsonNode arr = root.path("result").path(key);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String code = n.path("makerCode").asText(null);
                    String name = n.path("makerName").asText(null);
                    Integer ord = n.path("makerOrder").isMissingNode() ? null : n.path("makerOrder").asInt();
                    if (code != null && name != null) out.add(new Maker(code, name, key, ord));
                }
            }
        }
        return out;
    }

    private List<ModelGroup> fetchGroups(String makerCode) {
        JsonNode root = get("/public/search/carClass.json?makerCode=" + makerCode);
        JsonNode code = root.path("result").path("code");
        List<ModelGroup> out = new ArrayList<>();
        if (code.isArray()) {
            for (JsonNode n : code) {
                String classCode = n.path("classCode").asText(null);
                String className = n.path("className").asText(null);
                if (classCode != null && className != null) {
                    out.add(new ModelGroup(classCode, className));
                }
            }
        }
        return out;
    }

    private List<CarModel> fetchCars(String makerCode, String classCode) {
        JsonNode root = get("/public/search/carName.json?makerCode=" + makerCode + "&classCode=" + classCode);
        JsonNode code = root.path("result").path("code");
        List<CarModel> out = new ArrayList<>();
        if (code.isArray()) {
            for (JsonNode n : code) {
                String carCode = n.path("carCode").asText(null);
                String carName = n.path("carName").asText(null);
                if (carCode != null && carName != null) {
                    out.add(new CarModel(carCode, carName));
                }
            }
        }
        return out;
    }

    /** carModel.json 파싱: TRIM=codeModel, GRADE=codeGrade (없을 경우 안전 폴백) */
    private CodeModelGrade fetchCodeModelGrade(String makerCode, String classCode, String carCode) {
        String url = "/public/search/carModel.json?makerCode=" + makerCode + "&classCode=" + classCode + "&carCode=" + carCode;
        JsonNode root = get(url);

        // 1) TRIM: codeModel 배열 사용 (modelCode/modelName)
        List<Trim> trims = new ArrayList<>();
        JsonNode codeModel = root.path("result").path("codeModel");
        if (codeModel.isArray() && codeModel.size() > 0) {
            for (JsonNode n : codeModel) {
                String modelCode = n.path("modelCode").asText(null);
                String modelName = n.path("modelName").asText(null);
                if (modelCode != null && modelName != null) {
                    trims.add(new Trim(modelCode, modelName));
                }
            }
        }

        // 만약 codeModel이 없다면(간혹 케이스) → codeGrade에서 (modelCode, modelName) 중복 제거해 폴백
        if (trims.isEmpty()) {
            JsonNode codeGrade = root.path("result").path("codeGrade");
            if (codeGrade.isArray()) {
                Map<String, String> tmp = new LinkedHashMap<>();
                for (JsonNode n : codeGrade) {
                    String modelCode = n.path("modelCode").asText(null);
                    String modelName = n.path("modelName").asText(null);
                    if (modelCode != null && modelName != null) {
                        tmp.putIfAbsent(modelCode, modelName);
                    }
                }
                trims = tmp.entrySet().stream().map(e -> new Trim(e.getKey(), e.getValue())).collect(Collectors.toList());
            }
        }

        // 2) GRADE: codeGrade에서 (modelCode, gradeCode, gradeName)
        List<Grade> grades = new ArrayList<>();
        JsonNode codeGrade = root.path("result").path("codeGrade");
        if (codeGrade.isArray()) {
            for (JsonNode n : codeGrade) {
                String modelCode = n.path("modelCode").asText(null);
                String gradeCode = n.path("gradeCode").asText(null);
                String gradeName = n.path("gradeName").asText(null);
                if (modelCode != null && gradeCode != null && gradeName != null) {
                    grades.add(new Grade(modelCode, gradeCode, gradeName));
                }
            }
        }

        return new CodeModelGrade(trims, grades);
    }

    private JsonNode get(String urlOrPath) {
        try {
            // 절대경로 보정: "http"로 시작하지 않으면 HOST 붙임
            String url = urlOrPath.startsWith("http") ? urlOrPath : HOST + urlOrPath;

            String json = webClient.get()
                    .uri(url)
                    .header("Referer", HOST) // 일부 사이트가 Referer 체크
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return om.readTree(json);
        } catch (Exception e) {
            log.error("[CHACHA] GET {} failed: {}", urlOrPath, e.toString());
            return om.createObjectNode();
        }
    }

    /* ======================= UPSERTS ======================= */

    private void upsertMakers(List<Maker> list) {
        if (list.isEmpty()) return;
        String sql = """
            INSERT INTO cz_maker (maker_code, maker_name)
            VALUES (?,?)
            ON DUPLICATE KEY UPDATE maker_name=VALUES(maker_name)
        """;
        batch(list.size(), sql, (ps, i) -> {
            Maker m = list.get(i);
            ps.setString(1, m.code); ps.setString(2, m.name);
        });
        log.info("[CHACHA] makers upsert={}", list.size());
    }

    private void upsertGroups(String makerCode, List<ModelGroup> list) {
        if (list.isEmpty()) return;
        String sql = """
            INSERT INTO cz_model_group (maker_code, model_group_code, model_group_name)
            VALUES (?,?,?)
            ON DUPLICATE KEY UPDATE model_group_name=VALUES(model_group_name)
        """;
        batch(list.size(), sql, (ps, i) -> {
            ModelGroup g = list.get(i);
            ps.setString(1, makerCode);
            ps.setString(2, g.code);
            ps.setString(3, g.name);
        });
        log.info("[CHACHA] groups upsert mk={} n={}", makerCode, list.size());
    }

    private void upsertModels(String makerCode, String groupCode, List<CarModel> list) {
        if (list.isEmpty()) return;
        String sql = """
            INSERT INTO cz_model (maker_code, model_group_code, model_code, model_name)
            VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE model_name=VALUES(model_name)
        """;
        batch(list.size(), sql, (ps, i) -> {
            CarModel m = list.get(i);
            ps.setString(1, makerCode);
            ps.setString(2, groupCode);
            ps.setString(3, m.code);
            ps.setString(4, m.name);
        });
        log.info("[CHACHA] models(car) upsert mk={},mg={} n={}", makerCode, groupCode, list.size());
    }

    /** ✅ TRIM: carModel.json의 codeModel 기반 */
    private void upsertTrims(String makerCode, String groupCode, String modelCode, List<Trim> list) {
        if (list.isEmpty()) return;
        String sql = """
            INSERT INTO cz_trim (maker_code, model_group_code, model_code, trim_code, trim_name)
            VALUES (?,?,?,?,?)
            ON DUPLICATE KEY UPDATE trim_name=VALUES(trim_name)
        """;
        batch(list.size(), sql, (ps, i) -> {
            Trim t = list.get(i);
            ps.setString(1, makerCode);
            ps.setString(2, groupCode);
            ps.setString(3, modelCode);
            ps.setString(4, t.code); // ← modelCode 가 trim_code 로 들어간다
            ps.setString(5, t.name);
        });
        log.info("[CHACHA] trims upsert mk={},mg={},model={} n={}", makerCode, groupCode, modelCode, list.size());
    }

    /** ✅ GRADE: carModel.json의 codeGrade 기반 */
    private void upsertGrades(String makerCode, String groupCode, String modelCode, List<Grade> list) {
        if (list.isEmpty()) return;
        String sql = """
            INSERT INTO cz_grade (maker_code, model_group_code, model_code, trim_code, grade_code, grade_name)
            VALUES (?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE grade_name=VALUES(grade_name)
        """;
        batch(list.size(), sql, (ps, i) -> {
            Grade g = list.get(i);
            ps.setString(1, makerCode);
            ps.setString(2, groupCode);
            ps.setString(3, modelCode);
            ps.setString(4, g.modelCode); // grade는 model(=trim) 하위
            ps.setString(5, g.gradeCode);
            ps.setString(6, g.gradeName);
        });
        log.info("[CHACHA] grades upsert mk={},mg={},model={} n={}", makerCode, groupCode, modelCode, list.size());
    }

    /* ======================= UTIL ======================= */

    private interface PSS { void set(PreparedStatement ps, int i) throws SQLException; }
    private void batch(int size, String sql, PSS setter) {
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException { setter.set(ps, i); }
            @Override public int getBatchSize() { return size; }
        });
    }

    /* ======================= DTO ======================= */

    private record Maker(String code, String name, String country, Integer order){}
    private record ModelGroup(String code, String name){}
    private record CarModel(String code, String name){}
    private record Trim(String code, String name){}
    private record Grade(String modelCode, String gradeCode, String gradeName){}
    private record CodeModelGrade(List<Trim> trims, List<Grade> grades){}
}
