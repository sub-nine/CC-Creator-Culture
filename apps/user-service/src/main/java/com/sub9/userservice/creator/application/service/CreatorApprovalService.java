package com.sub9.userservice.creator.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.presentation.request.CreatorApprovalRequest;
import com.sub9.userservice.creator.presentation.response.CreatorApprovalResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorApprovalService {

    private final CreatorRepository creatorRepository;
    private final Clock clock;

    @Transactional
    public CreatorApprovalResponse review(
            UUID creatorId, UUID adminId, CreatorApprovalRequest request) {
        Creator creator = creatorRepository.findActiveByIdForUpdate(creatorId)
                .orElseThrow(() -> new BusinessException(CreatorErrorCode.CREATOR_NOT_FOUND));
        if (creator.getApprovalStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException(CreatorErrorCode.CREATOR_ALREADY_REVIEWED);
        }

        Instant now = clock.instant();
        if (request.approvalStatus() == ApprovalStatus.APPROVED) {
            creator.approve(adminId, now);
        } else {
            creator.reject(adminId, now);
        }
        return CreatorApprovalResponse.from(creator);
    }
}
