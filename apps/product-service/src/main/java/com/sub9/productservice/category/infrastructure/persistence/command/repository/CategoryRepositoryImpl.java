package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryCommandRepository {

    private final CategoryJpaRepository categoryJpaRepository;
    private final CategoryHashtagCommandJpaRepository categoryHashtagCommandJpaRepository;

    @Override
    public Category save(Category category) {
        return categoryJpaRepository.save(category);
    }

    @Override
    public Optional<Category> findById(UUID categoryId) {
        return categoryJpaRepository.findByIdAndDeletedAtIsNull(categoryId);
    }

    @Override
    public Optional<CategoryHashtag> findCategoryHashtagById(UUID categoryHashtagId) {
        return categoryHashtagCommandJpaRepository.findByIdAndDeletedAtIsNull(categoryHashtagId);
    }

    @Override
    public void linkCategoryHashtag(CategoryHashtag categoryHashtag) {
        categoryHashtagCommandJpaRepository.save(categoryHashtag);
    }

    @Override
    public Optional<CategoryHashtag> findCategoryHashtagByCategoryIdAndHashtagId(UUID categoryId, UUID hashtagId) {
        return categoryHashtagCommandJpaRepository.findByCategory_IdAndHashtag_IdAndDeletedAtIsNull(categoryId, hashtagId);
    }
}
