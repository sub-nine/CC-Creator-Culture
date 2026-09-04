package com.sub9.productservice.product.infrastructure.persistence.query;

import static com.sub9.productservice.product.domain.model.QProduct.product;
import static com.sub9.productservice.product.domain.model.QSku.sku;
import static com.sub9.productservice.product.domain.model.QStock.stock;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.product.application.query.repository.ProductQueryRepository;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.presentation.query.dto.ProductDetailResponse;
import com.sub9.productservice.product.presentation.query.dto.ProductResponse;
import com.sub9.productservice.product.presentation.query.dto.SkuResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
// TODO : 추후 해시태그, 카테고리 조건 추가
public class ProductQueryRepositoryImpl implements ProductQueryRepository {
  private final JPAQueryFactory queryFactory;

  @Override
  public Optional<ProductDetailResponse> findProductDetailById(UUID productId) {
    Product product = findProductById(productId);

    if (product == null) {
      return Optional.empty();
    }

    List<ProductDetailResponse.SkuInfo> skus = findSkusByProductId(productId);

    return Optional.of(
        new ProductDetailResponse(
            product.getId(),
            product.getCreatorId(),
            product.getName(),
            product.getContent(),
            product.getStatus(),
            product.getViewCount(),
            product.getAverageRating(),
            product.getReviewCount(),
            null,
            List.of(),
            skus));
  }

  @Override
  public List<SkuResponse> findAllSkuInfoByIds(List<UUID> skuIds) {
    return queryFactory
        .select(
            Projections.constructor(
                SkuResponse.class,
                sku.id,
                product.id,
                product.creatorId,
                product.name,
                sku.name,
                product.status,
                sku.price,
                stock.quantity))
        .from(sku)
        .join(product)
        .on(sku.productId.eq(product.id))
        .join(stock)
        .on(sku.id.eq(stock.skuId))
        .where(sku.id.in(skuIds), sku.deletedAt.isNull(), product.deletedAt.isNull())
        .fetch();
  }

  @Override
  public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
    List<UUID> productIds =
        queryFactory
            .select(product.id)
            .from(product)
            .where(
                product.deletedAt.isNull(), QuerydslUtils.containsIgnoreCase(product.name, keyword))
            .orderBy(productStatusOrder(), product.createdAt.desc(), product.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    List<ProductResponse> content =
        productIds.isEmpty() ? List.of() : findProductsByIds(productIds);

    JPAQuery<Long> countQuery =
        queryFactory
            .select(product.count())
            .from(product)
            .where(
                product.deletedAt.isNull(),
                QuerydslUtils.containsIgnoreCase(product.name, keyword));

    return PageableExecutionUtils.getPage(
        content,
        pageable,
        () -> {
          Long total = countQuery.fetchOne();
          return total != null ? total : 0L;
        });
  }

  private Product findProductById(UUID productId) {
    return queryFactory
        .selectFrom(product)
        .where(product.id.eq(productId), product.deletedAt.isNull())
        .fetchOne();
  }

  private List<ProductResponse> findProductsByIds(List<UUID> productIds) {
    return queryFactory
        .select(
            Projections.constructor(
                ProductResponse.class,
                product.id,
                product.name,
                product.status,
                product.averageRating,
                product.reviewCount,
                sku.price,
                stock.quantity))
        .from(product)
        .join(sku)
        .on(sku.productId.eq(product.id), sku.isDefault.isTrue(), sku.deletedAt.isNull())
        .join(stock)
        .on(stock.skuId.eq(sku.id))
        .where(product.id.in(productIds), product.deletedAt.isNull())
        .orderBy(productStatusOrder(), product.createdAt.desc(), product.id.desc())
        .fetch();
  }

  private List<ProductDetailResponse.SkuInfo> findSkusByProductId(UUID productId) {
    return queryFactory
        .select(
            Projections.constructor(
                ProductDetailResponse.SkuInfo.class,
                sku.id,
                sku.name,
                sku.price,
                sku.isDefault,
                stock.quantity))
        .from(sku)
        .join(stock)
        .on(stock.skuId.eq(sku.id))
        .where(sku.productId.eq(productId), sku.deletedAt.isNull())
        .orderBy(sku.isDefault.desc(), sku.createdAt.asc())
        .fetch();
  }

  private OrderSpecifier<Integer> productStatusOrder() {
    return new CaseBuilder()
        .when(product.status.eq(ProductStatus.ACTIVE))
        .then(0)
        .when(product.status.eq(ProductStatus.INACTIVE))
        .then(1)
        .when(product.status.eq(ProductStatus.SUSPENDED))
        .then(2)
        .otherwise(3)
        .asc();
  }
}
