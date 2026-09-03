package com.sub9.productservice.category.application.query.repository;

import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import com.sub9.productservice.category.presentation.query.dto.MergeRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface CategoryHashtagQueryRepository {

    List<HashtagResponse> findHashtagsByCategoryId(UUID categoryId);

    Page<HashtagResponse> findHashtagsByCategoryId(UUID categoryId, Pageable pageable);

    Page<MergeRequestResponse> findPendingMergeRequests(Pageable pageable);
}
