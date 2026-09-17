package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.model.CategoryHashtagMatchType;
import com.sub9.productservice.category.domain.model.CategoryHashtagStatus;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("HashtagCommandRepositoryImpl 통합 테스트")
class HashtagCommandRepositoryImplIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private HashtagCommandRepository hashtagCommandRepository;

    @Autowired
    private CategoryCommandRepository categoryCommandRepository;

    @Test
    @DisplayName("어떤 카테고리와도 연결되지 않은 해시태그만 조회되고, 연결된 해시태그는 빠진다")
    void findIdsWithoutCategoryLink_returnsOnlyUnlinkedHashtags() {
        Hashtag linked = hashtagCommandRepository.save(Hashtag.create("연결됨" + UUID.randomUUID()));
        Hashtag unlinked = hashtagCommandRepository.save(Hashtag.create("연결안됨" + UUID.randomUUID()));
        Category category = categoryCommandRepository.save(Category.create("카테고리" + UUID.randomUUID(), null));
        categoryCommandRepository.linkCategoryHashtagIfAbsent(
                CategoryHashtag.create(category, linked, CategoryHashtagMatchType.MANUAL, CategoryHashtagStatus.MERGED, 1.0));

        List<UUID> ids = hashtagCommandRepository.findIdsWithoutCategoryLink(100);

        assertThat(ids).contains(unlinked.getId());
        assertThat(ids).doesNotContain(linked.getId());
    }

    @Test
    @DisplayName("limit을 넘는 결과는 잘려서 반환된다")
    void findIdsWithoutCategoryLink_respectsLimit() {
        for (int i = 0; i < 3; i++) {
            hashtagCommandRepository.save(Hashtag.create("한도테스트" + UUID.randomUUID()));
        }

        List<UUID> ids = hashtagCommandRepository.findIdsWithoutCategoryLink(2);

        assertThat(ids).hasSizeLessThanOrEqualTo(2);
    }
}
