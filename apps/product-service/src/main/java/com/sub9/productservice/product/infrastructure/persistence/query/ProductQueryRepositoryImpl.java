package com.sub9.productservice.product.infrastructure.persistence.query;

import static com.sub9.productservice.product.domain.model.QImage.image;
import static com.sub9.productservice.product.domain.model.QProduct.product;
import static com.sub9.productservice.product.domain.model.QProductDailyView.productDailyView;
import static com.sub9.productservice.product.domain.model.QSku.sku;
import static com.sub9.productservice.product.domain.model.QStock.stock;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sub9.productservice.product.application.port.out.product.ProductQueryRepository;
import com.sub9.productservice.product.application.query.dto.ProductDetailInfo;
import com.sub9.productservice.product.application.query.dto.ProductInfo;
import com.sub9.productservice.product.application.query.dto.SkuInfo;
import com.sub9.productservice.product.domain.model.Product;
import com.sub9.productservice.product.domain.model.ProductStatus;
import com.sub9.productservice.product.domain.model.QImage;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class ProductQueryRepositoryImpl implements ProductQueryRepository {
  private final JPAQueryFactory queryFactory;

  @Override
  public Page<ProductInfo> searchProducts(
      String keyword, Set<UUID> metadataProductIds, Pageable pageable) {
    BooleanBuilder searchCondition = searchCondition(keyword, metadataProductIds);

    List<UUID> productIds =
        queryFactory
            .select(product.id)
            .from(product)
            .where(product.deletedAt.isNull(), searchCondition)
            .orderBy(productStatusOrder(), product.createdAt.desc(), product.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    List<ProductInfo> content = productIds.isEmpty() ? List.of() : findProductsByIds(productIds);

    JPAQuery<Long> countQuery =
        queryFactory
            .select(product.count())
            .from(product)
            .where(product.deletedAt.isNull(), searchCondition);

    return PageableExecutionUtils.getPage(
        content,
        pageable,
        () -> {
          Long total = countQuery.fetchOne();
          return total != null ? total : 0L;
        });
  }

  @Override
  public Optional<ProductDetailInfo> findProductDetailById(UUID productId) {
    Product product = findProductById(productId);

    if (product == null) {
      return Optional.empty();
    }

    List<ProductDetailInfo.SkuInfo> skus = findSkusByProductId(productId);
    List<ProductDetailInfo.ImageInfo> images = findImagesByProductId(productId);

    return Optional.of(
        new ProductDetailInfo(
            product.getId(),
            product.getCreatorId(),
            null,
            product.getName(),
            product.getContent(),
            product.getStatus(),
            getTotalViewCount(product),
            product.getAverageRating(),
            product.getReviewCount(),
            List.of(),
            List.of(),
            skus,
            images));
  }

  @Override
  public List<SkuInfo> getCartItemProducts(List<UUID> skuIds) {
    return queryFactory
        .select(
            Projections.constructor(
                SkuInfo.class,
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
  public boolean existsSkuOwnedByCreatorId(UUID creatorId, UUID skuId) {
    return queryFactory
            .selectOne()
            .from(sku)
            .join(product)
            .on(sku.productId.eq(product.id))
            .where(
                sku.id.eq(skuId),
                product.creatorId.eq(creatorId),
                sku.deletedAt.isNull(),
                product.deletedAt.isNull())
            .fetchFirst()
        != null;
  }

  @Override
  public boolean existsById(UUID productId) {
    return queryFactory
            .selectOne()
            .from(product)
            .where(
                product.id.eq(productId),
                product.deletedAt.isNull(),
                product.status.ne(ProductStatus.SUSPENDED))
            .fetchFirst()
        != null;
  }

  @Override
  public List<ProductInfo> findProductsByIds(List<UUID> productIds) {
    return queryFactory
        .select(
            Projections.constructor(
                ProductInfo.class,
                product.id,
                product.creatorId,
                Expressions.nullExpression(String.class),
                product.name,
                product.status,
                product.averageRating,
                product.reviewCount,
                sku.price,
                stock.quantity,
                image.processedKey.coalesce(image.originalKey)))
        .from(product)
        .join(sku)
        .on(sku.productId.eq(product.id), sku.isDefault.isTrue(), sku.deletedAt.isNull())
        .join(stock)
        .on(stock.skuId.eq(sku.id))
        .leftJoin(image)
        .on(image.productId.eq(product.id), image.deletedAt.isNull(), isPrimaryImage())
        .where(product.id.in(productIds), product.deletedAt.isNull())
        .orderBy(productStatusOrder(), product.createdAt.desc(), product.id.desc())
        .fetch();
  }

  // ============================== Helper Method ====================================
  private Product findProductById(UUID productId) {
    return queryFactory
        .selectFrom(product)
        .where(product.id.eq(productId), product.deletedAt.isNull())
        .fetchOne();
  }

  private BooleanBuilder searchCondition (
      String keword, Set<UUID> metadataProductIds
  ) {
    BooleanBuilder searchCondition = new BooleanBuilder();

    if (!StringUtils.hasText(keword)) {
      return searchCondition;
    }

    searchCondition.or(QuerydslUtils.containsIgnoreCase(product.name, keword));

    if (!metadataProductIds.isEmpty()) {
      searchCondition.or(product.id.in(metadataProductIds));
    }

    return searchCondition;
  }

  private List<ProductDetailInfo.SkuInfo> findSkusByProductId(UUID productId) {
    return queryFactory
        .select(
            Projections.constructor(
                ProductDetailInfo.SkuInfo.class,
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

  private List<ProductDetailInfo.ImageInfo> findImagesByProductId(UUID productId) {
    return queryFactory
        .select(
            Projections.constructor(
                ProductDetailInfo.ImageInfo.class,
                image.id,
                image.processedKey.coalesce(image.originalKey),
                image.sortOrder))
        .from(image)
        .where(image.productId.eq(productId), image.deletedAt.isNull())
        .orderBy(image.sortOrder.asc())
        .fetch();
  }

  private BooleanExpression isPrimaryImage() {
    QImage subImage = new QImage("subImage");

    return image.sortOrder.eq(
        JPAExpressions.select(subImage.sortOrder.min())
            .from(subImage)
            .where(subImage.productId.eq(product.id), subImage.deletedAt.isNull()));
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

  private long getTotalViewCount(Product product) {
    return product.getViewCount() + findTodayViewCount(product.getId());
  }

  private long findTodayViewCount(UUID productId) {
    Long viewCount =
        queryFactory
            .select(productDailyView.viewCount)
            .from(productDailyView)
            .where(
                productDailyView.productId.eq(productId),
                productDailyView.viewDate.eq(LocalDate.now(Clock.systemUTC())))
            .fetchOne();
    return viewCount != null ? viewCount : 0;
  }
}
