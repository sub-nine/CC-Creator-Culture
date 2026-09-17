package com.sub9.productservice.category.application.command.port.in;

import java.util.UUID;

public interface CalculateCategoryVectorUseCase {
    void calculate(UUID categoryId);
}
