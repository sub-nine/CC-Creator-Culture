package com.sub9.productservice.category.application.query.repository;

import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HashtagQueryRepository {

    Page<HashtagResponse> searchHashtags(String keyword, Pageable pageable);
}
