package com.sub9.productservice.category.domain.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class HashtagCategorySimilarityDomainService {

    // Levenshtein Distance 기반 문자열 유사도(대소문자 무시)를 0.0 ~ 1.0 사이로 계산
    public double calculateSimilarity(String a, String b) {
        String normalizedA = normalize(a);
        String normalizedB = normalize(b);

        int maxLength = Math.max(normalizedA.length(), normalizedB.length());
        if (maxLength == 0) {
            return 1.0;
        }
        return 1.0 - ((double) levenshteinDistance(normalizedA, normalizedB) / maxLength);
    }

    // 분해형(NFD)으로 입력된 한글(자모 분리)을 완성형(NFC)으로 통일한 뒤 대소문자 무시
    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
    }

    private int levenshteinDistance(String a, String b) {
        int[][] distances = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            distances[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            distances[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int substitutionCost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                distances[i][j] = Math.min(
                        Math.min(distances[i - 1][j] + 1, distances[i][j - 1] + 1),
                        distances[i - 1][j - 1] + substitutionCost
                );
            }
        }

        return distances[a.length()][b.length()];
    }
}
