package com.sub9.productservice.category.application.command.port.out;

import com.sub9.productservice.category.application.command.model.CategoryUpsertResult;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryCommandRepository {

    Category save(Category category);

    Optional<Category> findById(UUID categoryId);

    List<Category> findAllActive();

    Optional<CategoryHashtag> findCategoryHashtagById(UUID requestId);

    void linkCategoryHashtag(CategoryHashtag categoryHashtag);

    Optional<CategoryHashtag> findCategoryHashtagByCategoryIdAndHashtagId(UUID categoryId, UUID hashtagId);

    // 이름으로 조회하고 없으면 원자적으로 생성 - 동시에 같은 이름으로 호출돼도 하나만 생성됨
    CategoryUpsertResult findOrCreateByName(String name);

    // 이미 연결돼 있으면 원자적으로 아무것도 안 함(예외 없이) - 동시에 같은 조합으로 호출돼도 하나만 연결됨
    void linkCategoryHashtagIfAbsent(CategoryHashtag categoryHashtag);
}
