package com.sub9.orderservice.cart.application.port.out;

import com.sub9.orderservice.cart.application.dto.CreatorNameInfo;

import java.util.List;
import java.util.UUID;

public interface CartUserPort {
  /**
   * 장바구니에 담긴 창작자 ID들의 창작자의 상호명을 조회한다.
   *
   * @return 창작자 ID와 상호명
   */
  List<CreatorNameInfo> getCreatorNamesByIds(List<UUID> creatorIds);
}
