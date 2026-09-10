package com.sub9.userservice.creator.presentation.request;

import com.sub9.userservice.creator.domain.model.ApprovalStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record CreatorApprovalRequest(
        @NotNull(message = "승인 상태는 필수입니다.")
        ApprovalStatus approvalStatus
) {

    @AssertTrue(message = "승인 상태는 APPROVED 또는 REJECTED만 가능합니다.")
    public boolean isValidReviewDecision() {
        return approvalStatus == ApprovalStatus.APPROVED
                || approvalStatus == ApprovalStatus.REJECTED;
    }
}
