package com.sub9.productservice.product.application.command.service.stock;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.product.application.command.dto.stock.AdjustStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.DeductStockCommand;
import com.sub9.productservice.product.application.command.dto.stock.RestoreStockCommand;
import com.sub9.productservice.product.application.port.in.stock.AdjusStockUseCase;
import com.sub9.productservice.product.application.port.in.stock.OrderStockUseCase;
import com.sub9.productservice.product.application.query.repository.ProductQueryRepository;
import com.sub9.productservice.product.domain.exception.ProductErrorCode;
import com.sub9.productservice.product.domain.model.StockHistory;
import com.sub9.productservice.product.domain.model.StockHistoryReason;
import com.sub9.productservice.product.domain.repository.StockCommandRepository;
import com.sub9.productservice.product.domain.repository.StockHistoryCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class StockCommandService implements AdjusStockUseCase, OrderStockUseCase {
  private final StockHistoryCommandRepository stockHistoryCommandRepository;
  private final StockCommandRepository stockCommandRepository;
  private final ProductQueryRepository productQueryRepository;

  @Override
  public void adjust(AdjustStockCommand command) {
    if (!productQueryRepository.existsSkuOwnedByCreatorId(command.creatorId(), command.skuId())) {
      throw new BusinessException(ProductErrorCode.PRODUCT_ACCESS_DENIED);
    }

    if (command.quantity() == 0) {
      throw new BusinessException(ProductErrorCode.INVALID_STOCK_ADJUSTMENT);
    }

    if (!stockCommandRepository.adjustStock(command.skuId(), command.quantity())) {
      throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK);
    }

    stockHistoryCommandRepository.save(
        StockHistory.create(
            null, command.skuId(), command.quantity(), StockHistoryReason.CREATOR_ADJUSTMENT));
  }

  @Override
  public void deduct(DeductStockCommand command) {
    for (var item : command.items()) {
      var history =
          StockHistory.create(
              command.orderId(), item.skuId(), item.quantity(), StockHistoryReason.ORDER);

      if (!stockHistoryCommandRepository.insertIfAbsent(history)) continue;

      if (!stockCommandRepository.decreaseStock(item.skuId(), item.quantity())) {
        throw new BusinessException(ProductErrorCode.INSUFFICIENT_STOCK);
      }
    }
  }

  @Override
  public void restore(RestoreStockCommand command) {
    for (var item : command.items()) {
      var history =
          StockHistory.create(command.orderId(), item.skuId(), item.quantity(), command.reason());

      if (!stockHistoryCommandRepository.insertIfAbsent(history)) continue;

      if (!stockCommandRepository.increaseStock(item.skuId(), item.quantity())) {
        throw new BusinessException(ProductErrorCode.SKU_NOT_FOUND);
      }
    }
  }
}
