package com.sub9.productservice.product.application.support;

import com.sub9.productservice.product.application.port.out.image.ImageStoragePort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImageStorageRollbackCleaner {
  private final ImageStoragePort imageStoragePort;

  // 이미지 업로드 실패 시 보상 작업
  public void registerRollbackCleanup(List<String> objectKeys) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_ROLLED_BACK) {
              return;
            }
            objectKeys.forEach(
                (objectKey) -> {
                  try {
                    imageStoragePort.delete(objectKey);
                  } catch (Exception e) {
                    log.error(
                        "[ERROR] 트랜잭션 롤백 보상 이미지 삭제 실패, objectKey = {}", objectKey, e);
                  }
                });
          }
        });
  }
}
