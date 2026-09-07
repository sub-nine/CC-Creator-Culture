package com.sub9.productservice.category.domain.entity;

import com.sub9.productservice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "p_categories_products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_categories_products_category_product",
                        columnNames = {"category_id", "product_id", "unique_version"}
                )
        }
)
public class CategoryProduct extends BaseEntity {

    @JoinColumn(name = "category_id", nullable = false)
    @ManyToOne
    private Category category;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "unique_version", nullable = false)
    private UUID uniqueVersion;

    @Override
    public void delete(UUID deletedBy) {
        super.delete(deletedBy);

        this.uniqueVersion = this.getId();
    }

    public static CategoryProduct create(Category category, UUID productId) {
        return CategoryProduct.builder()
                .category(category)
                .productId(productId)
                .uniqueVersion(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .build();
    }

    @Builder
    private CategoryProduct(Category category, UUID productId, UUID uniqueVersion) {
        this.category = category;
        this.productId = productId;
        this.uniqueVersion = uniqueVersion;
    }
}
/*
id	식별자	uuid		PK	NOT NULL	앱 생성(UUID)	식별자		주요 엔티티 ID
category_id	카테고리 ID	uuid			NOT NULL		연결된 카테고리 식별자	p_categories
product_id	상품 ID	uuid			NOT NULL		연결된 상품 식별자	p_products (product 모듈, ID 참조만)
 */
