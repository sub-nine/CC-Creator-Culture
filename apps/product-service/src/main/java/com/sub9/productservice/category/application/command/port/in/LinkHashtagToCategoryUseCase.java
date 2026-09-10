package com.sub9.productservice.category.application.command.port.in;

import java.util.UUID;

public interface LinkHashtagToCategoryUseCase {
    void tryLink(UUID hashtagId);
}
