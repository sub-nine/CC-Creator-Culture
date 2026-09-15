package com.sub9.userservice.creator.application.service;

import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.presentation.response.CreatorNameResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalCreatorQueryService {

    private final CreatorRepository creatorRepository;

    @Transactional(readOnly = true)
    public List<CreatorNameResponse> getCreatorNames(List<UUID> creatorIds) {
        if (creatorIds.isEmpty()) {
            return List.of();
        }
        return creatorRepository.findApprovedActiveByUserIds(creatorIds).stream()
                .map(CreatorNameResponse::from)
                .toList();
    }
}
