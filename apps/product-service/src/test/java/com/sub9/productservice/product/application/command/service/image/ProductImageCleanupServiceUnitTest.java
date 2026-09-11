package com.sub9.productservice.product.application.command.service.image;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.command.dto.product.*;
import com.sub9.productservice.product.application.port.out.image.*;
import com.sub9.productservice.product.domain.model.*;
import com.sub9.productservice.product.domain.repository.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageCleanupService - 단위 테스트")
class ProductImageCleanupServiceUnitTest {
  @Mock ImageCommandRepository imageCommandRepository;
  @Mock ImageStoragePort imageStoragePort;
  @InjectMocks ProductImageCleanupService imageService;

  @Test
  @DisplayName("앞쪽 파일 삭제 실패와 상관없이 다음 이미지를 정리한다.")
  void deleteExpiredImages_fails_without_removing_failed_image() {
    // given
    Instant now = Instant.parse("2026-09-11T00:00:00Z");
    Image failed = Image.create(UUID.randomUUID(), "original/failed", null, 0);
    Image next = Image.create(UUID.randomUUID(), "original/next", null, 0);

    given(imageCommandRepository.findExpiredImages(now.minusSeconds(7 * 86400)))
        .willReturn(List.of(failed, next));

    willThrow(new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR))
        .given(imageStoragePort)
        .delete("original/failed");

    // when
    imageService.deleteExpiredImages(now);

    // then
    verify(imageCommandRepository, never()).hardDeleteById(failed.getId());
    verify(imageCommandRepository).hardDeleteById(next.getId());
  }
}
