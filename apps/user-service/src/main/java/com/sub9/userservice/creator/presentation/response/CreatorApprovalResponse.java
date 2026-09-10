package com.sub9.userservice.creator.presentation.response;

import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import com.sub9.userservice.creator.domain.model.Creator;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreatorApprovalResponse(
        UUID creatorId,
        ApprovalStatus approvalStatus,
        UUID approvedBy,
        LocalDateTime approvedAt
) {

    public static CreatorApprovalResponse from(Creator creator) {
        return new CreatorApprovalResponse(
                creator.getId(),
                creator.getApprovalStatus(),
                creator.getApprovedBy(),
                creator.getApprovedAt());
    }
}
