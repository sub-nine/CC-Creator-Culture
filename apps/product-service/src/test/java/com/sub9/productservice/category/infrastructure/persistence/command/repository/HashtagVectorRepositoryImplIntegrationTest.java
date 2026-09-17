package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagVectorRepository;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.infrastructure.persistence.command.entity.HashtagVector;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagVectorJpaRepository;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("HashtagVectorRepositoryImpl 통합 테스트")
class HashtagVectorRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    private static final int DIMENSION = 768;

    @Autowired
    private HashtagVectorRepository hashtagVectorRepository;

    @Autowired
    private HashtagVectorJpaRepository hashtagVectorJpaRepository;

    @Autowired
    private HashtagCommandRepository hashtagCommandRepository;

    @Test
    @DisplayName("저장된 적 없는 해시태그는 존재하지 않는다고 판단한다")
    void existsByHashtagId_notSaved_returnsFalse() {
        assertThat(hashtagVectorRepository.existsByHashtagId(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("벡터를 저장하면 이후 존재한다고 판단하고, pgvector 컬럼에 원래 값 그대로 저장된다")
    void saveIfAbsent_savesVector_thenExists() {
        UUID hashtagId = saveHashtag().getId();
        float[] vector = zeroVector();
        vector[0] = 1.0f;

        hashtagVectorRepository.saveIfAbsent(hashtagId, vector);

        assertThat(hashtagVectorRepository.existsByHashtagId(hashtagId)).isTrue();
        HashtagVector saved = hashtagVectorJpaRepository.findById(hashtagId).orElseThrow();
        assertThat(saved.getEmbedding()).containsExactly(vector);
    }

    @Test
    @DisplayName("이미 저장된 해시태그에 다시 saveIfAbsent를 호출해도 기존 값을 덮어쓰지 않는다")
    void saveIfAbsent_alreadyExists_doesNotOverwrite() {
        UUID hashtagId = saveHashtag().getId();
        float[] firstVector = zeroVector();
        firstVector[0] = 1.0f;
        float[] secondVector = zeroVector();
        secondVector[1] = 1.0f;

        hashtagVectorRepository.saveIfAbsent(hashtagId, firstVector);
        hashtagVectorRepository.saveIfAbsent(hashtagId, secondVector);

        HashtagVector saved = hashtagVectorJpaRepository.findById(hashtagId).orElseThrow();
        assertThat(saved.getEmbedding()).containsExactly(firstVector);
    }

    private float[] zeroVector() {
        return new float[DIMENSION];
    }

    private Hashtag saveHashtag() {
        return hashtagCommandRepository.save(Hashtag.create("벡터테스트" + UUID.randomUUID()));
    }
}
