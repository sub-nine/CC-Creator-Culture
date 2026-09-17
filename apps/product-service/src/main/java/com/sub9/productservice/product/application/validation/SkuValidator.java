package com.sub9.productservice.product.application.validation;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.sku.CreateSkuCommand;
import com.sub9.productservice.product.domain.exception.SkuErrorCode;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.util.CollectionUtils;

@UtilityClass
public class SkuValidator {
  public void validateForCreate(List<CreateSkuCommand> skuCommands) {
    if (CollectionUtils.isEmpty(skuCommands)) {
      throw new BusinessException(SkuErrorCode.SKU_REQUIRED);
    }

    if (skuCommands.size() == 1) {
      return;
    }

    long defaultCount = skuCommands.stream().filter(CreateSkuCommand::isDefault).count();

    if (defaultCount != 1) {
      throw new BusinessException(SkuErrorCode.INVALID_DEFAULT_SKU_COUNT);
    }
  }
}
