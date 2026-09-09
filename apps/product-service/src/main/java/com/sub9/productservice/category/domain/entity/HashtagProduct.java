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
        name = "p_hashtags_products",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_hashtags_products_hashtag_product",
                        columnNames = {"hashtag_id", "product_id", "unique_version"}
                )
        }
)
public class HashtagProduct extends BaseEntity {

    public static final UUID ACTIVE_UNIQUE_VERSION = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @JoinColumn(name = "hashtag_id", nullable = false)
    @ManyToOne
    private Hashtag hashtag;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "unique_version", nullable = false)
    private UUID uniqueVersion;

    @Override
    public void delete(UUID deletedBy) {
        super.delete(deletedBy);

        this.uniqueVersion = this.getId();
    }

    public static HashtagProduct create(Hashtag hashtag, UUID productId) {
        return HashtagProduct.builder()
                .hashtag(hashtag)
                .productId(productId)
                .uniqueVersion(ACTIVE_UNIQUE_VERSION)
                .build();
    }

    @Builder
    private HashtagProduct(Hashtag hashtag, UUID productId, UUID uniqueVersion) {
        this.hashtag = hashtag;
        this.productId = productId;
        this.uniqueVersion = uniqueVersion;
    }
}
/*
id	식별자	uuid		PK	NOT NULL	앱 생성(UUID)	식별자		주요 엔티티 ID
hashtag_id	해시태그 ID	uuid			NOT NULL		연결된 해시태그 식별자	p_hashtags
product_id	상품 ID	uuid			NOT NULL		연결된 상품 식별자	p_products (product 모듈, ID 참조만)
 */
