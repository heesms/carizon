package com.carizon.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.carizon.mapping.StringNormalizer.Level;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeMappingService {

    private final JdbcTemplate jdbc;

    public enum Scope { TODAY, FULL }

    /* ---------- 튜닝 파라미터 ---------- */
    private static final int BATCH_SIZE = 1000;
    private static final double THRESH_MAKER = 0.85;
    private static final double THRESH_GROUP = 0.88;
    private static final double THRESH_MODEL = 0.90;
    private static final double THRESH_TRIM  = 0.90;
    private static final double THRESH_GRADE = 0.90;
    private static final double THRESH_FINAL = 0.93;

    /* =======================================================================
       메인 엔트리
       우선순위 0) 강제매핑 → 1) 차량번호 동일(차차차) → 2) 부모제약 텍스트 매칭
       부분매칭 허용: 결정된 부모는 그대로 존중하고, 자식만 매칭/보류
       ======================================================================= */
    public int runAutoMapping(String platformName, Scope scope) {
        final String platform = platformName.toUpperCase();

        // 1) 입력로우 수집 (필요컬럼만)
        var rows = fetchPlatformRows(platform, scope);
        if (rows.isEmpty()) return 0;

        // 2) 캐시/사전 한 번만 로드
        var plateStd = preloadPlateStd(rows);
        log.debug("preloadPlateStd : {}", plateStd) ;
        var forced = preloadForced(platform);
        var dict = preloadStandardDictionaries(); // maker→groups→models→trims→grades 이름 캐시

        // 3) 배치 업서트 버퍼
        List<Param> buffer = new ArrayList<>(BATCH_SIZE);
        int total = 0;

        for (Row r : rows) {
            // 플랫폼 정규화 이름
            String pmkN = normalize(r.p_maker_name, Level.MAKER);
            String pmgN = normalize(r.p_model_group_name, Level.MODEL_GROUP);
            String pmdN = normalize(r.p_model_name, Level.MODEL);
            String ptrN = normalize(r.p_trim_name, Level.TRIM);
            String pgrN = normalize(r.p_grade_name, Level.GRADE);

            // ---------- 우선순위 0: 강제 매핑 ----------
            Std std = findForced(forced, r);
            String reason = null;
            double score = 0.0;

            // ---------- 우선순위 1: 차량번호 동일(CHACHACHA) ----------
            if (std == null && r.plate != null) {
                Std s = plateStd.get(r.plate);
                if (s != null) {
                    std = s;
                    reason = "PLATE_EQUAL";
                    score = 1.0;
                }
            }

            // ---------- 우선순위 2: 부모제약 텍스트 매칭 ----------
            if (std == null) {
                std = new Std(); // 부분 채움용 빈 표준
                reason = "HIER_TEXT";
                // 2-1) maker
                DictMaker mkCand = dict.bestMaker(pmkN, THRESH_MAKER);
                if (mkCand != null) std.maker = mkCand.code;

                // 2-2) group : 부모(maker) 정해졌을 때만 해당 maker 하위에서 비교
                DictGroup mgCand = (std.maker != null)
                        ? dict.bestGroup(std.maker, pmgN, THRESH_GROUP) : null;
                if (mgCand != null) std.group = mgCand.code;

                // 2-3) model : 부모(maker,group) 둘 다 정해졌을 때만
                DictModel mdCand = (std.maker != null && std.group != null)
                        ? dict.bestModel(std.maker, std.group, pmdN, THRESH_MODEL) : null;
                if (mdCand != null) std.model = mdCand.code;

                // 2-4) trim : 부모(maker,group,model)
                DictTrim trCand = (std.maker != null && std.group != null && std.model != null && notBlank(ptrN))
                        ? dict.bestTrim(std.maker, std.group, std.model, ptrN, THRESH_TRIM) : null;
                if (trCand != null) std.trim = trCand.code;

                // 2-5) grade : 부모 + trim
                DictGrade grCand = (std.maker != null && std.group != null && std.model != null
                        && std.trim != null && notBlank(pgrN))
                        ? dict.bestGrade(std.maker, std.group, std.model, std.trim, pgrN, THRESH_GRADE) : null;
                if (grCand != null) std.grade = grCand.code;

                // 최종 스코어(존재하는 단계만 가중 평균)
                score = dict.lastScore;
            }

            String status = ( "PLATE_EQUAL".equals(reason) || score >= THRESH_FINAL ) ? "AUTO" : "REVIEW";

            // 부분 매칭이라도 **결정된 부모는 그대로 채워 저장**(비워두지 않음)
            buffer.add(new Param(platform, r, pmkN, pmgN, pmdN, ptrN, pgrN, std, score, reason, status));

            if (buffer.size() >= BATCH_SIZE) {
                total += upsertBatch(buffer);
                buffer.clear();
            }
        }
        if (!buffer.isEmpty()) total += upsertBatch(buffer);

        log.info("auto-mapping v2 platform={} scope={} affected={}", platform, scope, total);
        return total;
    }

    /* ======================= I/O & 캐시 ======================= */

    private List<Row> fetchPlatformRows(String platform, Scope scope) {
        StringBuilder sb = new StringBuilder("""
            SELECT DISTINCT
              CAR_NO,
              MAKER_CODE, MAKER_NAME,
              MODEL_GROUP_CODE, MODEL_GROUP_NAME,
              MODEL_CODE, MODEL_NAME,
              TRIM_CODE, TRIM_NAME,
              GRADE_CODE, GRADE_NAME
            FROM platform_car
            WHERE PLATFORM_NAME=?
        """);
        List<Object> args = new ArrayList<>(); args.add(platform);
        if (scope == Scope.TODAY) {
            var d = java.sql.Date.valueOf(LocalDate.now());
            sb.append(" AND last_seen_date >= ? AND last_seen_date < DATE_ADD(?, INTERVAL 1 DAY)");
            args.add(d); args.add(d);
        }
        return jdbc.query(sb.toString(), args.toArray(), (rs, i) -> new Row(
                n(rs.getString("CAR_NO")),
                n(rs.getString("MAKER_CODE")), n(rs.getString("MAKER_NAME")),
                n(rs.getString("MODEL_GROUP_CODE")), n(rs.getString("MODEL_GROUP_NAME")),
                n(rs.getString("MODEL_CODE")), n(rs.getString("MODEL_NAME")),
                n(rs.getString("TRIM_CODE")), n(rs.getString("TRIM_NAME")),
                n(rs.getString("GRADE_CODE")), n(rs.getString("GRADE_NAME"))
        ));
    }

    // plate → 표준 하이라키 (CHACHACHA 최신 1건)
    private Map<String, Std> preloadPlateStd(List<Row> rows) {
        var plates = rows.stream().map(r -> r.plate).filter(Objects::nonNull).collect(Collectors.toSet());
        if (plates.isEmpty()) return Map.of();

        String in = plates.stream().map(p -> "?").collect(Collectors.joining(","));
        String sql = """
            SELECT CAR_NO,
                   MAKER_CODE, MODEL_GROUP_CODE, MODEL_CODE, TRIM_CODE, GRADE_CODE
              FROM platform_car
             WHERE PLATFORM_NAME='CHACHACHA' AND CAR_NO IN (%s)
        """.formatted(in);

        Map<String, Std> map = new HashMap<>(plates.size()*2);
        jdbc.query(sql, plates.toArray(), rs -> {
            String plate = rs.getString("CAR_NO");
            // 최신성 보장은 큰 의미 없으니 마지막 값으로 덮음(동일 plate 다수여도 문제 없음)
            map.put(plate, new Std(
                    rs.getString("MAKER_CODE"),
                    rs.getString("MODEL_GROUP_CODE"),
                    rs.getString("MODEL_CODE"),
                    rs.getString("TRIM_CODE"),
                    rs.getString("GRADE_CODE")
            ));
        });
        return map;
    }

    // 강제 매핑: 플랫폼+depth별로 map
    private List<Forced> preloadForced(String platform) {
        return jdbc.query("""
            SELECT depth,
                   p_maker_code, p_model_group_code, p_model_code, p_trim_code, p_grade_code,
                   maker_code, model_group_code, model_code, trim_code, grade_code
              FROM cz_forced_map
             WHERE platform_name=?
        """, (rs, i) -> new Forced(
                rs.getInt("depth"),
                n(rs.getString("p_maker_code")), n(rs.getString("p_model_group_code")), n(rs.getString("p_model_code")),
                n(rs.getString("p_trim_code")), n(rs.getString("p_grade_code")),
                new Std(
                        n(rs.getString("maker_code")), n(rs.getString("model_group_code")), n(rs.getString("model_code")),
                        n(rs.getString("trim_code")), n(rs.getString("grade_code"))
                )
        ), platform);
    }

    // 표준 사전 캐싱(부모제약 기반 탐색 + 스코어 계산)
    private final class Dict {
        // maker
        final Map<String,String> makerNameByCode = new HashMap<>();
        final List<DictMaker> makers = new ArrayList<>();
        final Map<String,List<DictGroup>> groupsByMaker = new HashMap<>();
        final Map<String,List<DictModel>> modelsByMkMg = new HashMap<>();
        final Map<String,List<DictTrim>> trimsByMkMgMd = new HashMap<>();
        final Map<String,List<DictGrade>> gradesByMkMgMdTr = new HashMap<>();
        double lastScore;

        Dict() {
            jdbc.query("SELECT maker_code, maker_name FROM cz_maker", rs -> {
                var code = rs.getString(1); var name = normalize(rs.getString(2), Level.MAKER);
                makerNameByCode.put(code, name);
                makers.add(new DictMaker(code, name));
            });
            jdbc.query("SELECT maker_code, model_group_code, model_group_name FROM cz_model_group", rs -> {
                String mk = rs.getString(1); String mg = rs.getString(2);
                String name = normalize(rs.getString(3), Level.MODEL_GROUP);
                groupsByMaker.computeIfAbsent(mk, k -> new ArrayList<>()).add(new DictGroup(mk, mg, name));
            });
            jdbc.query("SELECT maker_code, model_group_code, model_code, model_name FROM cz_model", rs -> {
                String key = rs.getString(1) + "|" + rs.getString(2);
                modelsByMkMg.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new DictModel(rs.getString(1), rs.getString(2), rs.getString(3),
                                normalize(rs.getString(4), Level.MODEL)));
            });
            jdbc.query("SELECT maker_code, model_group_code, model_code, trim_code, trim_name FROM cz_trim", rs -> {
                String key = rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3);
                trimsByMkMgMd.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new DictTrim(rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), normalize(rs.getString(5), Level.TRIM)));
            });
            jdbc.query("SELECT maker_code, model_group_code, model_code, trim_code, grade_code, grade_name FROM cz_grade", rs -> {
                String key = rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3) + "|" + rs.getString(4);
                gradesByMkMgMdTr.computeIfAbsent(key, k -> new ArrayList<>())
                        .add(new DictGrade(rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getString(4), rs.getString(5), normalize(rs.getString(6), Level.GRADE)));
            });
        }

        DictMaker bestMaker(String nameN, double th) {
            if (blank(nameN)) return null;
            double best=0; DictMaker bestM=null;
            for (var m : makers) {
                double s = Similarity.score(m.nameN, nameN);
                if (s>best){best=s;bestM=m;}
            }
            if (best>=th){ lastScore = 0.15*best; return bestM; }
            lastScore = 0; return null;
        }
        DictGroup bestGroup(String mk, String nameN, double th) {
            if (blank(nameN)) return null;
            var list = groupsByMaker.getOrDefault(mk, List.of());
            double best=0; DictGroup bestG=null;
            for (var g : list){ double s=Similarity.score(g.nameN, nameN); if (s>best){best=s;bestG=g;}}
            if (best>=th){ lastScore += 0.25*best; return bestG; }
            return null;
        }
        DictModel bestModel(String mk,String mg,String nameN,double th){
            if (blank(nameN)) return null;
            var list = modelsByMkMg.getOrDefault(mk+"|"+mg, List.of());
            double best=0; DictModel bestD=null;
            for (var d : list){ double s=Similarity.score(d.nameN, nameN); if (s>best){best=s;bestD=d;}}
            if (best>=th){ lastScore += 0.30*best; return bestD; }
            return null;
        }
        DictTrim bestTrim(String mk,String mg,String md,String nameN,double th){
            if (blank(nameN)) return null;
            var list = trimsByMkMgMd.getOrDefault(mk+"|"+mg+"|"+md, List.of());
            double best=0; DictTrim bestT=null;
            for (var t : list){ double s=Similarity.score(t.nameN, nameN); if (s>best){best=s;bestT=t;}}
            if (best>=th){ lastScore += 0.15*best; return bestT; }
            return null;
        }
        DictGrade bestGrade(String mk,String mg,String md,String tr,String nameN,double th){
            if (blank(nameN)) return null;
            var list = gradesByMkMgMdTr.getOrDefault(mk+"|"+mg+"|"+md+"|"+tr, List.of());
            double best=0; DictGrade bestG=null;
            for (var g : list){ double s=Similarity.score(g.nameN, nameN); if (s>best){best=s;bestG=g;}}
            if (best>=th){ lastScore += 0.15*best; return bestG; }
            return null;
        }
    }
    private Dict preloadStandardDictionaries() { return new Dict(); }

    private Std findForced(List<Forced> forced, Row r) {
        for (Forced f : forced) {
            if (f.depth>=1 && neq(f.p_mk, r.p_maker_code)) continue;
            if (f.depth>=2 && neq(f.p_mg, r.p_model_group_code)) continue;
            if (f.depth>=3 && neq(f.p_md, r.p_model_code)) continue;
            if (f.depth>=4 && neq(f.p_tr, r.p_trim_code)) continue;
            if (f.depth>=5 && neq(f.p_gr, r.p_grade_code)) continue;
            return f.std; // 최초 일치 1건 적용
        }
        return null;
    }

    /* ======================= 배치 UPSERT ======================= */

    private int upsertBatch(List<Param> list) {
        final String sql = """
        INSERT INTO cz_code_map
          (platform_name,
           p_maker_code, p_model_group_code, p_model_code, p_trim_code, p_grade_code,
           p_maker_name_norm, p_model_group_name_norm, p_model_name_norm, p_trim_name_norm, p_grade_name_norm,
           ref_plate_no,
           maker_code, model_group_code, model_code, trim_code, grade_code,
           confidence_score, match_reason, status, first_seen, last_seen)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,  CURRENT_DATE, CURRENT_DATE)
        ON DUPLICATE KEY UPDATE
          p_maker_name_norm       = VALUES(p_maker_name_norm),
          p_model_group_name_norm = VALUES(p_model_group_name_norm),
          p_model_name_norm       = VALUES(p_model_name_norm),
          p_trim_name_norm        = VALUES(p_trim_name_norm),
          p_grade_name_norm       = VALUES(p_grade_name_norm),
          ref_plate_no            = IFNULL(VALUES(ref_plate_no), ref_plate_no),
          maker_code       = IF(status='LOCKED', maker_code,       VALUES(maker_code)),
          model_group_code = IF(status='LOCKED', model_group_code, VALUES(model_group_code)),
          model_code       = IF(status='LOCKED', model_code,       VALUES(model_code)),
          trim_code        = IF(status='LOCKED', trim_code,        VALUES(trim_code)),
          grade_code       = IF(status='LOCKED', grade_code,       VALUES(grade_code)),
          confidence_score = IF(status='LOCKED', confidence_score, VALUES(confidence_score)),
          match_reason     = IF(status='LOCKED', match_reason,     VALUES(match_reason)),
          status           = IF(status='LOCKED', status,           VALUES(status)),
          last_seen = CURRENT_DATE
    """;

        int[] res = jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                Param p = list.get(i);
                int x=1;
                // 1) platform_name
                ps.setString(x++, p.platform);
                // 2~6) p_*_code
                ps.setString(x++, p.row.p_maker_code);
                ps.setString(x++, p.row.p_model_group_code);
                ps.setString(x++, p.row.p_model_code);
                ps.setString(x++, p.row.p_trim_code);
                ps.setString(x++, p.row.p_grade_code);
                // 7~11) p_*_name_norm
                ps.setString(x++, p.pmkN);
                ps.setString(x++, p.pmgN);
                ps.setString(x++, p.pmdN);
                ps.setString(x++, p.ptrN);
                ps.setString(x++, p.pgrN);
                // 12) ref_plate_no
                ps.setString(x++, p.row.plate);
                // 13~17) 표준코드
                ps.setString(x++, p.std.maker);
                ps.setString(x++, p.std.group);
                ps.setString(x++, p.std.model);
                ps.setString(x++, p.std.trim);
                ps.setString(x++, p.std.grade);
                // 18~20) score / reason / status
                ps.setDouble(x++, p.score);
                ps.setString(x++, p.reason);
                ps.setString(x++, p.status);
            }
            @Override public int getBatchSize() { return list.size(); }
        });
        return java.util.Arrays.stream(res).sum();
    }


    /* ======================= helpers & DTO ======================= */

    private static boolean neq(String a, String b){ return !Objects.equals(n(a), n(b)); }
    private static boolean blank(String s){ return s==null || s.isBlank(); }
    private static boolean notBlank(String s){ return !blank(s); }
    private static String n(String s){ return s==null || s.isBlank() ? null : s; }
    private static String nz(String s){ return s==null ? null : s; }
    private static String normalize(String s, Level lv){ return StringNormalizer.normalize(s, lv); }

    private record Row(
            String plate,
            String p_maker_code, String p_maker_name,
            String p_model_group_code, String p_model_group_name,
            String p_model_code, String p_model_name,
            String p_trim_code, String p_trim_name,
            String p_grade_code, String p_grade_name
    ) {}

    private static final class Std {
        String maker, group, model, trim, grade;
        Std(){}
        Std(String mk, String mg, String md, String tr, String gr){
            this.maker=mk; this.group=mg; this.model=md; this.trim=tr; this.grade=gr;
        }
    }
    private record Forced(int depth, String p_mk, String p_mg, String p_md, String p_tr, String p_gr, Std std){}

    private record DictMaker(String code, String nameN){}
    private record DictGroup(String maker, String code, String nameN){}
    private record DictModel(String maker, String group, String code, String nameN){}
    private record DictTrim (String maker, String group, String model, String code, String nameN){}
    private record DictGrade(String maker, String group, String model, String trim, String code, String nameN){}

    private record Param(
            String platform, Row row,
            String pmkN, String pmgN, String pmdN, String ptrN, String pgrN,
            Std std, double score, String reason, String status
    ){}
}
