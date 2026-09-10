package com.sub9.productservice.category.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@DisplayName("HashtagCategorySimilarityDomainService 단위 테스트")
class HashtagCategorySimilarityDomainServiceTest {

    private final HashtagCategorySimilarityDomainService similarityDomainService = new HashtagCategorySimilarityDomainService();

    @Test
    @DisplayName("완전히 동일한 문자열이면 유사도는 1.0이다")
    void identicalStrings_returnsOne() {
        double similarity = similarityDomainService.calculateSimilarity("고양이", "고양이");

        assertThat(similarity).isEqualTo(1.0);
    }

    @Test
    @DisplayName("대소문자만 다르면 유사도는 1.0이다")
    void caseInsensitive_returnsOne() {
        double similarity = similarityDomainService.calculateSimilarity("Cat", "CAT");

        assertThat(similarity).isEqualTo(1.0);
    }

    @Test
    @DisplayName("공통된 문자가 하나도 없으면 유사도는 0.0이다")
    void completelyDifferentStrings_returnsZero() {
        double similarity = similarityDomainService.calculateSimilarity("ABC", "XYZ");

        assertThat(similarity).isEqualTo(0.0);
    }

    @Test
    @DisplayName("한글 음절이 하나 추가된 경우 편집거리 기반 유사도를 계산한다")
    void partiallyDifferentKoreanStrings_returnsExpectedRatio() {
        // "카테고리"(4음절) -> "카테고리즘"(5음절), 편집거리 1 / 최대길이 5 = 0.8
        double similarity = similarityDomainService.calculateSimilarity("카테고리", "카테고리즘");

        assertThat(similarity).isCloseTo(0.8, offset(0.0001));
    }

    @Test
    @DisplayName("둘 다 빈 문자열이면 유사도는 1.0이다")
    void bothEmpty_returnsOne() {
        double similarity = similarityDomainService.calculateSimilarity("", "");

        assertThat(similarity).isEqualTo(1.0);
    }

    @Test
    @DisplayName("한쪽만 빈 문자열이면 유사도는 0.0이다")
    void oneEmpty_returnsZero() {
        double similarity = similarityDomainService.calculateSimilarity("", "고양이");

        assertThat(similarity).isEqualTo(0.0);
    }

    @Test
    @DisplayName("분해형(NFD)과 완성형(NFC)으로 표기된 동일한 한글은 유사도 1.0으로 취급한다")
    void nfdAndNfcKorean_areTreatedAsEqual() {
        String composed = "가나다";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);

        double similarity = similarityDomainService.calculateSimilarity(composed, decomposed);

        assertThat(similarity).isEqualTo(1.0);
    }
}
