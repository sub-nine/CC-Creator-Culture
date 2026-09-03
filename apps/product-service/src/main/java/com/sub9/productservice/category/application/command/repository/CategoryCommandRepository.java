package com.sub9.productservice.category.application.command.repository;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;

import java.util.Optional;
import java.util.UUID;

public interface CategoryCommandRepository {

    Category save(Category category);

    Optional<Category> findById(UUID categoryId);

    Optional<CategoryHashtag> findCategoryHashtagById(UUID requestId);

    void linkCategoryHashtag(CategoryHashtag categoryHashtag);

    Optional<CategoryHashtag> findCategoryHashtagByCategoryIdAndHashtagId(UUID categoryId, UUID hashtagId);
}
