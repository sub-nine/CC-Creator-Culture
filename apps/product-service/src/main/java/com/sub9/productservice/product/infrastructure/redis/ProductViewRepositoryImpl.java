package com.sub9.productservice.product.infrastructure.redis;

import com.sub9.productservice.product.application.query.dto.ProductViewCount;
import com.sub9.productservice.product.domain.repository.ProductViewRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductViewRepositoryImpl implements ProductViewRepository {
  private static final String VIEWER_KEY_PREFIX = "product:viewer:";
  private static final String VIEW_COUNT_KEY = "product:view:counts";

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Long> viewCountScript;

  @Override
  public boolean recordView(UUID productId, UUID viewerId, Duration ttl) {
    String viewerKey = VIEWER_KEY_PREFIX + productId + ":" + viewerId;
    Long result =
        redisTemplate.execute(
            viewCountScript,
            List.of(viewerKey, VIEW_COUNT_KEY),
            String.valueOf(ttl.toSeconds()),
            productId.toString());

    return Long.valueOf(1L).equals(result);
  }

  @Override
  public List<ProductViewCount> findAllViewCounts() {
    List<ProductViewCount> result = new ArrayList<>();

    // TODO : 스캔 성능 조정 필요 시 수정
    // ScanOptions scanOptions = ScanOptions.scanOptions().count(탐색 값).build();
    ScanOptions scanOptions = ScanOptions.NONE;

    try (Cursor<Map.Entry<Object, Object>> cursor =
        redisTemplate.opsForHash().scan(VIEW_COUNT_KEY, scanOptions)) {

      while (cursor.hasNext()) {
        Map.Entry<Object, Object> entry = cursor.next();

        result.add(
            new ProductViewCount(
                UUID.fromString(entry.getKey().toString()),
                Long.parseLong(entry.getValue().toString())));
      }
    }

    return result;
  }

  @Override
  public void deleteAllViewCount() {
    redisTemplate.delete(VIEW_COUNT_KEY);
  }
}
