package com.sub9.productservice.category.application.query.adapter;

import com.sub9.productservice.category.application.query.port.out.CategoryQueryRepository;
import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.category.presentation.query.dto.ProductCategoryIdsResponse;
import com.sub9.productservice.leaderboard.application.port.out.CategoryQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryQueryAdapter implements CategoryQueryPort {
    private final CategoryQueryRepository categoryQueryRepository;

    @Override
    public List<CategoryResponse> getCategoriesByIds(List<UUID> ids) {
        return categoryQueryRepository.searchCategoriesByIds(ids);
    }

    @Override
    public List<ProductCategoryIdsResponse> getCategoryIdsByProductIds(List<UUID> productIds) {
        // TODO: CategoryProduct 리포지토리 연결 후 targetId -> categoryId 매핑 구현

        return List.of();
    }
}
