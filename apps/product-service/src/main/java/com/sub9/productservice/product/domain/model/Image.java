package com.sub9.productservice.product.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE p_images SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@Table(
    name = "p_images",
    indexes = {
      @Index(name = "idx_images_product_id_deleted_at", columnList = "product_id, deleted_at"),
      @Index(name = "idx_images_deleted_at", columnList = "deleted_at")
    })
public class Image {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID productId;

  @Column(length = 500)
  private String originalKey;

  @Column(length = 500)
  private String processedKey;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private ImageProcessingStatus status;

  @Column(nullable = false)
  private int sortOrder;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(nullable = false, updatable = false)
  private UUID createdBy;

  @Column private Instant processedAt;

  private Instant deletedAt;

  public static Image create(
      UUID productId, String originalKey, String processedKey, int sortOrder) {
    Image image = new Image();
    image.id = UuidCreator.getTimeOrderedEpoch();
    image.productId = productId;
    image.originalKey = originalKey;
    image.processedKey = processedKey;
    image.status = ImageProcessingStatus.PENDING;
    image.sortOrder = sortOrder;

    return image;
  }

  public void updateSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
