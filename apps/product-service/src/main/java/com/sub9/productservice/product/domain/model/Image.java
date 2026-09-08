package com.sub9.productservice.product.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "p_images",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_images_product_id_image_order",
          columnNames = {"product_id", "sort_id"})
    })
public class Image {
  @Id private UUID id;

  @Column(nullable = false)
  private UUID productId;

  @Column(nullable = false, length = 500)
  private String originalKey;

  @Column(nullable = false, length = 500)
  private String processedKey;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private ImageProcessingStatus status;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(nullable = false, updatable = false)
  private UUID createdBy;

  @Column(nullable = false)
  private Instant processedAt;

  public static Image create(UUID productId, String originalKey, String processedKey) {
    Image image = new Image();
    image.productId = productId;
    image.originalKey = originalKey;
    image.processedKey = processedKey;
    image.status = ImageProcessingStatus.PENDING;

    return image;
  }

  public void completeProcessing(String processedKey) {
    this.processedKey = processedKey;
    this.status = ImageProcessingStatus.COMPLETED;
    this.processedAt = Instant.now();
  }

  public void failProcessing() {
    this.status = ImageProcessingStatus.FAILED;
    this.processedAt = Instant.now();
  }
}
