package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("CategoryRepositoryImpl 통합 테스트")
class CategoryRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    private static final int DIMENSION = 768;

    @Autowired
    private CategoryCommandRepository categoryCommandRepository;

    @Autowired
    private CategoryVectorRepository categoryVectorRepository;

    @Test
    @DisplayName("벡터가 없는 활성 카테고리만 조회되고, 벡터가 있는 카테고리는 빠진다")
    void findActiveIdsWithoutVector_returnsOnlyCategoriesMissingVector() {
        Category withVector = categoryCommandRepository.save(Category.create("벡터있음" + UUID.randomUUID(), null));
        Category withoutVector = categoryCommandRepository.save(Category.create("벡터없음" + UUID.randomUUID(), null));
        categoryVectorRepository.save(withVector.getId(), new float[DIMENSION]);

        List<UUID> ids = categoryCommandRepository.findActiveIdsWithoutVector(100);

        assertThat(ids).contains(withoutVector.getId());
        assertThat(ids).doesNotContain(withVector.getId());
    }

    @Test
    @DisplayName("limit을 넘는 결과는 잘려서 반환된다")
    void findActiveIdsWithoutVector_respectsLimit() {
        for (int i = 0; i < 3; i++) {
            categoryCommandRepository.save(Category.create("한도테스트" + UUID.randomUUID(), null));
        }

        List<UUID> ids = categoryCommandRepository.findActiveIdsWithoutVector(2);

        assertThat(ids).hasSizeLessThanOrEqualTo(2);
    }
}
