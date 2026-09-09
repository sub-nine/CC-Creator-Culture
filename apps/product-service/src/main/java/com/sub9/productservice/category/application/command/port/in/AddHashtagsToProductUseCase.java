package com.sub9.productservice.category.application.command.port.in;

import java.util.List;
import java.util.UUID;

public interface AddHashtagsToProductUseCase {
    void addHashtagsToProduct(UUID productId, List<String> hashtagStrings);
}
