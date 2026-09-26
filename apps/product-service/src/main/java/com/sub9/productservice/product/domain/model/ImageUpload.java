package com.sub9.productservice.product.domain.model;

import com.github.f4b6a3.uuid.UuidCreator;
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
    name = "p_image_uploads",
    indexes = {@Index(name = "idx_image_uploads_created_at", columnList = "created_at")},
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_image_uploads_object_key", columnNames = "object_key")
    })
public class ImageUpload {
  @Id private UUID id;

  @Column(nullable = false, length = 500)
  private String objectKey;

  @Column(nullable = false, length = 50)
  private String contentType;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(updatable = false)
  private UUID createdBy;

  public static ImageUpload create(String objectKey, String contentType) {
    ImageUpload image = new ImageUpload();
    image.id = UuidCreator.getTimeOrderedEpoch();
    image.objectKey = objectKey;
    image.contentType = contentType;

    return image;
  }
}
