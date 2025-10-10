package com.carizon.mapping;

public final class Similarity {
    private Similarity() {}

    /** 간단 Jaro-Winkler + 토큰 교집합 가중 */
    public static double score(String a, String b) {
        if (a == null || b == null) return 0.0;
        String a2 = a.trim(), b2 = b.trim();
        if (a2.isEmpty() || b2.isEmpty()) return 0.0;

        double jw = jaroWinkler(a2, b2);
        double tok = tokenDice(a2, b2);
        return jw * 0.6 + tok * 0.4;
    }

    private static double tokenDice(String a, String b) {
        java.util.Set<String> A = new java.util.HashSet<>(java.util.Arrays.asList(a.split("\\s+")));
        java.util.Set<String> B = new java.util.HashSet<>(java.util.Arrays.asList(b.split("\\s+")));
        if (A.isEmpty() || B.isEmpty()) return 0.0;
        java.util.Set<String> inter = new java.util.HashSet<>(A); inter.retainAll(B);
        return (2.0 * inter.size()) / (A.size() + B.size());
    }

    // Jaro-Winkler 간단구현
    private static double jaroWinkler(String s1, String s2) {
        int[] mtp = matches(s1, s2);
        double m = mtp[0];
        if (m == 0) return 0.0;
        double j = (m / s1.length() + m / s2.length() + (m - mtp[1]) / m) / 3.0;
        double p = 0.1; // prefix scale
        return j < 0.7 ? j : j + Math.min(p, 1.0 / Math.max(s1.length(), s2.length())) * mtp[2] * (1 - j);
    }

    private static int[] matches(String s1, String s2) {
        String max = s1.length() > s2.length() ? s1 : s2;
        String min = s1.length() > s2.length() ? s2 : s1;
        int range = Math.max(max.length() / 2 - 1, 0);
        boolean[] matchFlags = new boolean[max.length()];
        int matches = 0;

        for (int i = 0; i < min.length(); i++) {
            char c1 = min.charAt(i);
            for (int j = Math.max(i - range, 0),
                 end = Math.min(i + range + 1, max.length()); j < end; j++) {
                if (!matchFlags[j] && c1 == max.charAt(j)) {
                    matchFlags[j] = true;
                    matches++;
                    break;
                }
            }
        }

        char[] ms1 = new char[matches];
        char[] ms2 = new char[matches];

        for (int i = 0, si = 0; i < min.length(); i++) {
            char c1 = min.charAt(i);
            for (int j = Math.max(i - range, 0),
                 end = Math.min(i + range + 1, max.length()); j < end; j++) {
                if (max.charAt(j) == c1) {
                    ms1[si] = c1;
                    break;
                }
            }
        }

        for (int i = 0, si = 0; i < max.length(); i++) {
            if (matchFlags[i]) {
                ms2[si] = max.charAt(i);
                si++;
            }
        }

        int transpositions = 0;
        for (int i = 0; i < matches; i++) {
            if (ms1[i] != ms2[i]) transpositions++;
        }

        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1.length(), s2.length())); i++) {
            if (s1.charAt(i) == s2.charAt(i)) prefix++; else break;
        }
        return new int[]{matches, transpositions / 2, prefix};
    }
}
