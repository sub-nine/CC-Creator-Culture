package com.sub9.productservice.category.application.query.port.out;

import com.sub9.productservice.category.presentation.query.dto.MergeRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MergeRequestQueryRepository {

    Page<MergeRequestResponse> findPendingMergeRequests(Pageable pageable);
}
