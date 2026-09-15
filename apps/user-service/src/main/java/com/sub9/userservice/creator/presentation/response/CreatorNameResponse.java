package com.sub9.userservice.creator.presentation.response;

import com.sub9.userservice.creator.domain.model.Creator;
import java.util.UUID;

public record CreatorNameResponse(
        UUID creatorId,
        String creatorName
) {
    public static CreatorNameResponse from(Creator creator) {
        return new CreatorNameResponse(creator.getUserId(), creator.getCreatorName());
    }
}
