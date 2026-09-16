package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.CategoryVectorRepository;
import com.sub9.productservice.category.application.command.port.out.EmbeddingClient;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryVectorService 단위 테스트")
class CategoryVectorServiceTest {

    @Mock
    private CategoryCommandRepository categoryCommandRepository;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private CategoryVectorRepository categoryVectorRepository;

    private CategoryVectorService categoryVectorService;

    @BeforeEach
    void setUp() {
        categoryVectorService = new CategoryVectorService(categoryCommandRepository, embeddingClient, categoryVectorRepository);
    }

    @Test
    @DisplayName("카테고리 이름으로 임베딩을 계산해 저장한다")
    void calculate_categoryExists_computesAndSavesVector() {
        Category category = Category.create("액체괴물", null);
        float[] vector = {0.1f, 0.2f};
        when(categoryCommandRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(embeddingClient.embed(category.getName())).thenReturn(vector);

        categoryVectorService.calculate(category.getId());

        verify(categoryVectorRepository).save(category.getId(), vector);
    }

    @Test
    @DisplayName("존재하지 않는 카테고리면 예외를 던지고 임베딩을 호출하지 않는다")
    void calculate_categoryNotFound_throwsException() {
        UUID categoryId = UUID.randomUUID();
        when(categoryCommandRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryVectorService.calculate(categoryId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CategoryErrorCode.CATEGORY_NOT_FOUND);

        verify(embeddingClient, never()).embed(anyString());
        verify(categoryVectorRepository, never()).save(any(), any());
    }
}
