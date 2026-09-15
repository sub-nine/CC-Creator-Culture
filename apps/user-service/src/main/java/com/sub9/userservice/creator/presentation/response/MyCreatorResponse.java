package com.sub9.userservice.creator.presentation.response;

import com.sub9.userservice.creator.domain.model.Creator;
import java.util.UUID;

public record MyCreatorResponse(
        UUID creatorId,
        String creatorName,
        String businessRegistrationNumber
) {

    public static MyCreatorResponse from(Creator creator) {
        return new MyCreatorResponse(
                creator.getId(),
                creator.getCreatorName(),
                creator.getBusinessRegistrationNumber());
    }
}
