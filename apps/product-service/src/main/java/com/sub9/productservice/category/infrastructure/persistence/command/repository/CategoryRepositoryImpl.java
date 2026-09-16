package com.sub9.productservice.category.infrastructure.persistence.command.repository;

import com.github.f4b6a3.uuid.UuidCreator;
import com.sub9.productservice.category.application.command.model.CategoryUpsertResult;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.CategoryHashtag;
import com.sub9.productservice.category.domain.model.CategoryStatus;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryHashtagJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryCommandRepository {

    private final CategoryJpaRepository categoryJpaRepository;
    private final CategoryHashtagJpaRepository categoryHashtagJpaRepository;

    @Override
    public Category save(Category category) {
        return categoryJpaRepository.save(category);
    }

    @Override
    public Optional<Category> findById(UUID categoryId) {
        return categoryJpaRepository.findByIdAndDeletedAtIsNull(categoryId);
    }

    @Override
    public List<Category> findAllActive() {
        return categoryJpaRepository.findAllByStatusAndDeletedAtIsNull(CategoryStatus.ACTIVE);
    }

    @Override
    public Optional<CategoryHashtag> findCategoryHashtagById(UUID categoryHashtagId) {
        return categoryHashtagJpaRepository.findByIdAndDeletedAtIsNull(categoryHashtagId);
    }

    @Override
    public void linkCategoryHashtag(CategoryHashtag categoryHashtag) {
        categoryHashtagJpaRepository.save(categoryHashtag);
    }

    @Override
    public Optional<CategoryHashtag> findCategoryHashtagByCategoryIdAndHashtagId(UUID categoryId, UUID hashtagId) {
        return categoryHashtagJpaRepository.findByCategory_IdAndHashtag_IdAndDeletedAtIsNull(categoryId, hashtagId);
    }

    @Override
    public CategoryUpsertResult findOrCreateByName(String name) {
        boolean created = categoryJpaRepository.insertIfAbsent(
                UuidCreator.getTimeOrderedEpoch(), name, null, CategoryStatus.ACTIVE.name(), Category.ACTIVE_UNIQUE_VERSION
        ).isPresent();

        Category category = categoryJpaRepository.findByNameAndDeletedAtIsNull(name)
                .orElseThrow(() -> new IllegalStateException("Category upsert 직후 조회 실패 - name: " + name));

        return new CategoryUpsertResult(category, created);
    }

    @Override
    public void linkCategoryHashtagIfAbsent(CategoryHashtag categoryHashtag) {
        categoryHashtagJpaRepository.insertIfAbsent(
                UuidCreator.getTimeOrderedEpoch(),
                categoryHashtag.getCategory().getId(),
                categoryHashtag.getHashtag().getId(),
                categoryHashtag.getMatchType().name(),
                categoryHashtag.getStatus().name(),
                categoryHashtag.getSimilarityScore(),
                CategoryHashtag.ACTIVE_UNIQUE_VERSION
        );
    }
}
