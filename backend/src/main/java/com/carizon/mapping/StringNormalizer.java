package com.carizon.mapping;

import java.text.Normalizer;

public final class StringNormalizer {
    private StringNormalizer() {}

    public enum Level { MAKER, MODEL_GROUP, MODEL, TRIM, GRADE }

    /** 한/영 혼용 정규화: 괄호/특수문자/여분공백 제거, 세대 토큰 통일, 대문자화 */
    public static String normalize(String s, Level level) {
        if (s == null) return "";
        String x = s;
        x = Normalizer.normalize(x, Normalizer.Form.NFKC);

        // 괄호류 제거
        x = x.replaceAll("[\\(\\)\\[\\]{}]", " ");

        // 상위 레벨은 과도한 스펙 신호 제거(배기량/연료/구동/변속 등)
        if (level != Level.GRADE) {
            x = x.replaceAll("(?i)\\b(\\d\\.\\d|HEV|LP\\s?I|LPG|디젤|가솔린|하이브리드|전기|EV|오토|수동|AT|MT|DCT|CVT|4WD|2WD|AWD|터보|T)\\b", " ");
        }

        // 세대/프로젝트 토큰 통일(예시)
        x = x.replaceAll("(?i)NEW\\s*", "")
                .replaceAll("(?i)DN8", " DN8 ")
                .replaceAll("(?i)LF", " LF ")
                .replaceAll("(?i)NF", " NF ");

        // 기호 제거, 공백 정리
        x = x.replaceAll("[^0-9A-Z가-힣]+", " ");
        x = x.trim().replaceAll("\\s{2,}", " ");
        return x.toUpperCase();
    }
}
