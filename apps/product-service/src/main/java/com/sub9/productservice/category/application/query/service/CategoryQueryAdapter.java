package com.sub9.productservice.category.application.query.service;

import com.sub9.productservice.category.application.query.repository.CategoryQueryRepository;
import com.sub9.productservice.category.presentation.query.dto.CategoryResponse;
import com.sub9.productservice.leaderboard.application.port.CategoryQueryPort;
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
}
