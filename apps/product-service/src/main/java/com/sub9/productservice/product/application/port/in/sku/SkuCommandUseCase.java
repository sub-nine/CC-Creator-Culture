package com.sub9.productservice.product.application.port.in.sku;

import com.sub9.productservice.product.application.command.dto.sku.AddSkuCommand;
import com.sub9.productservice.product.application.command.dto.sku.DeleteSkuCommand;
import com.sub9.productservice.product.application.command.dto.sku.UpdateSkuCommand;
import java.util.UUID;

public interface SkuCommandUseCase {
  /**
   * 상품에 SKU와 초기 재고를 추가한다.
   * 대표 SKU로 등록 시 기존 대표 SKU를 해제한다.
   *
   * @return 생성된 SKU ID
   */
  UUID addSku(AddSkuCommand command);

  /**
   * SKU 정보를 수정한다.
   * 대표 SKU 변경 시 기존 대표 SKU를 해제한다.
   *
   */
  void updateSku(UpdateSkuCommand command);

  /**
   * SKU를 삭제한다.
   * 대표 SKU 여부와 최소 SKU 개수를 검증한다.
   *
   */
  void deleteSku(DeleteSkuCommand command);
}
