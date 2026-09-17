package com.sub9.userservice.creator.presentation.response;

import com.sub9.userservice.creator.domain.model.Creator;
import java.util.List;
import org.springframework.data.domain.Page;

public record CreatorPageResponse(
        List<CreatorSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static CreatorPageResponse from(Page<Creator> creators) {
        return new CreatorPageResponse(
                creators.getContent().stream()
                        .map(CreatorSummaryResponse::from)
                        .toList(),
                creators.getNumber(),
                creators.getSize(),
                creators.getTotalElements(),
                creators.getTotalPages(),
                creators.isFirst(),
                creators.isLast());
    }
}
