package com.sub9.productservice.product.application.port.out.product;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProductUserPort {
  /**
   * 상품 조회 시 창작자의 상호명을 조회한다.
   *
   * @return 창작자 ID와 상호명
   */
  Map<UUID, String> getCreatorNamesByIds(List<UUID> creatorIds);
}
