package com.sub9.userservice.creator.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.presentation.response.CreatorPageResponse;
import com.sub9.userservice.creator.presentation.response.CreatorSummaryResponse;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorQueryService {

    private final CreatorRepository creatorRepository;

    @Transactional(readOnly = true)
    public CreatorPageResponse getCreators(
            int page, int size, String keyword, String sortCondition) {
        String normalizedKeyword = normalizeKeyword(keyword);
        PageRequest pageable = PageRequest.of(page, size, createSort(sortCondition));
        return CreatorPageResponse.from(
                creatorRepository.findApprovedActiveByCreatorName(
                        normalizedKeyword, pageable));
    }

    @Transactional(readOnly = true)
    public CreatorSummaryResponse getCreator(UUID creatorId) {
        Creator creator = creatorRepository.findApprovedActiveById(creatorId)
                .orElseThrow(() -> new BusinessException(CreatorErrorCode.CREATOR_NOT_FOUND));
        return CreatorSummaryResponse.from(creator);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private Sort createSort(String sortCondition) {
        String[] parts = sortCondition.toLowerCase(Locale.ROOT).split(",");
        Sort.Direction direction = Sort.Direction.fromString(parts[1]);
        return Sort.by(
                new Sort.Order(direction, "creatorName"),
                new Sort.Order(direction, "id"));
    }
}
