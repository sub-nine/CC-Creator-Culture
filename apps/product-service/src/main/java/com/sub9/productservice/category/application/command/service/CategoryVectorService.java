package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.application.command.port.in.CalculateCategoryVectorUseCase;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.application.command.port.out.EmbeddingClient;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryVectorService implements CalculateCategoryVectorUseCase {

    private final CategoryCommandRepository categoryCommandRepository;
    private final EmbeddingClient embeddingClient;
    private final CategoryVectorRepository categoryVectorRepository;

    @Override
    @Transactional
    public void calculate(UUID categoryId) {
        Category category = categoryCommandRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        float[] vector = embeddingClient.embed(category.getName());
        categoryVectorRepository.save(categoryId, vector);
    }
}
