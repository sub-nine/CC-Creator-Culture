package com.sub9.productservice.category.application.query.repository;

import com.sub9.productservice.category.presentation.query.dto.HashtagResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface HashtagQueryRepository {

    Page<HashtagResponse> searchHashtags(String keyword, Pageable pageable);

    List<HashtagResponse> searchHashtagsByIds(List<UUID> ids);
}
