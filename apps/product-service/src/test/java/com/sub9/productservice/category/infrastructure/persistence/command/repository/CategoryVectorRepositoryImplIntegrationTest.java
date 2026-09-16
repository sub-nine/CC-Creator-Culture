package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.HashtagVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagVectorJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@DisplayName("CategoryVectorRepositoryImpl 통합 테스트")
class CategoryVectorRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    private static final int DIMENSION = 768;

    @Autowired
    private CategoryVectorRepository categoryVectorRepository;

    @Autowired
    private HashtagVectorJpaRepository hashtagVectorJpaRepository;

    @Test
    @DisplayName("해시태그 벡터와 방향이 같은 카테고리 벡터는 유사도 1에 가깝게 나온다")
    void findSimilarities_sameDirectionVectors_returnsSimilarityCloseToOne() {
        UUID hashtagId = saveHashtagVector(oneHot(0));
        UUID categoryId = UUID.randomUUID();
        categoryVectorRepository.save(categoryId, oneHot(0));

        Map<UUID, Double> similarities = categoryVectorRepository.findSimilarities(hashtagId, List.of(categoryId));

        assertThat(similarities.get(categoryId)).isCloseTo(1.0, within(0.0001));
    }

    @Test
    @DisplayName("해시태그 벡터와 직교하는 카테고리 벡터는 유사도 0에 가깝게 나온다")
    void findSimilarities_orthogonalVectors_returnsSimilarityCloseToZero() {
        UUID hashtagId = saveHashtagVector(oneHot(0));
        UUID categoryId = UUID.randomUUID();
        categoryVectorRepository.save(categoryId, oneHot(1));

        Map<UUID, Double> similarities = categoryVectorRepository.findSimilarities(hashtagId, List.of(categoryId));

        assertThat(similarities.get(categoryId)).isCloseTo(0.0, within(0.0001));
    }

    @Test
    @DisplayName("벡터가 없는 카테고리는 결과 맵에서 빠진다")
    void findSimilarities_categoryVectorMissing_excludedFromResult() {
        UUID hashtagId = saveHashtagVector(oneHot(0));
        UUID categoryWithVector = UUID.randomUUID();
        UUID categoryWithoutVector = UUID.randomUUID();
        categoryVectorRepository.save(categoryWithVector, oneHot(0));

        Map<UUID, Double> similarities = categoryVectorRepository.findSimilarities(
                hashtagId, List.of(categoryWithVector, categoryWithoutVector));

        assertThat(similarities).containsOnlyKeys(categoryWithVector);
    }

    @Test
    @DisplayName("해시태그 벡터 자체가 없으면 결과가 빈 맵이다")
    void findSimilarities_hashtagVectorMissing_returnsEmptyMap() {
        UUID hashtagId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        categoryVectorRepository.save(categoryId, oneHot(0));

        Map<UUID, Double> similarities = categoryVectorRepository.findSimilarities(hashtagId, List.of(categoryId));

        assertThat(similarities).isEmpty();
    }

    private UUID saveHashtagVector(float[] vector) {
        UUID hashtagId = UUID.randomUUID();
        hashtagVectorJpaRepository.save(HashtagVector.of(hashtagId, vector));
        return hashtagId;
    }

    private float[] oneHot(int index) {
        float[] vector = new float[DIMENSION];
        vector[index] = 1.0f;
        return vector;
    }
}
