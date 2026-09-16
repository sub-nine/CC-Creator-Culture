package com.sub9.userservice.creator.presentation.response;

import com.sub9.userservice.creator.domain.model.Creator;
import java.util.UUID;

public record CreatorSummaryResponse(
        UUID creatorId,
        String creatorName
) {

    public static CreatorSummaryResponse from(Creator creator) {
        return new CreatorSummaryResponse(creator.getId(), creator.getCreatorName());
    }
}
