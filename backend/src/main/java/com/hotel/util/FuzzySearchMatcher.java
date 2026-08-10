package com.hotel.util;

import java.util.HashSet;
import java.util.Set;

/** Scores short user queries against normalized searchable text. */
public final class FuzzySearchMatcher {

    private FuzzySearchMatcher() {
    }

    public static double score(String query, String candidate) {
        String q = VietnameseTextNormalizer.normalize(query);
        String c = VietnameseTextNormalizer.normalize(candidate);
        if (q == null || c == null) {
            return 0d;
        }
        if (q.length() < 3) {
            return 0d;
        }
        if (q.equals(c)) {
            return 1d;
        }
        if (c.startsWith(q)) {
            return 0.96d;
        }
        if (c.contains(q)) {
            return 0.90d;
        }

        double edit = normalizedEditSimilarity(q, c);
        double grams = trigramDice(q, c);
        return Math.max(edit, grams * 0.85d);
    }

    public static boolean matches(String query, String candidate, double minimumScore) {
        return score(query, candidate) >= minimumScore;
    }

    static double normalizedEditSimilarity(String left, String right) {
        int distance = levenshtein(left, right);
        return 1d - ((double) distance / Math.max(left.length(), right.length()));
    }

    static double trigramDice(String left, String right) {
        if (left.length() < 3 || right.length() < 3) {
            return 0d;
        }
        Set<String> leftGrams = grams(left);
        Set<String> rightGrams = grams(right);
        long overlap = leftGrams.stream().filter(rightGrams::contains).count();
        return (2d * overlap) / (leftGrams.size() + rightGrams.size());
    }

    private static Set<String> grams(String value) {
        Set<String> result = new HashSet<>();
        for (int i = 0; i <= value.length() - 3; i++) {
            result.add(value.substring(i, i + 3));
        }
        return result;
    }

    private static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            }
            previous = current;
        }
        return previous[right.length()];
    }
}
