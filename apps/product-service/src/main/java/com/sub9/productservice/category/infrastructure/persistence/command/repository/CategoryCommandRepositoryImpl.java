package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.sub9.productservice.category.application.command.repository.CategoryCommandRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CategoryCommandRepositoryImpl implements CategoryCommandRepository {

    private final CategoryCommandJpaRepository categoryCommandJpaRepository;
    private final CategoryHashtagCommandJpaRepository categoryHashtagCommandJpaRepository;

    @Override
    public Category save(Category category) {
        return categoryCommandJpaRepository.save(category);
    }

    @Override
    public Optional<Category> findById(UUID categoryId) {
        return categoryCommandJpaRepository.findByIdAndDeletedAtIsNull(categoryId);
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
