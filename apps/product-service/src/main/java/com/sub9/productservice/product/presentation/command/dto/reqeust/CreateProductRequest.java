package com.sub9.productservice.product.presentation.command.dto.reqeust;

import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.CreateSkuCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
    @Size(max = 5, message = "해시태그는 최대 5개까지 등록할 수 있습니다.")
        List<
                @NotBlank(message = "해시태그는 빈 값일 수  없습니다.")
                @Size(max = 10, message = "해시태그는 10자를 초과할 수 없습니다.") String>
            hashTags,
    @NotBlank(message = "상품명은 필수입니다.") String name,
    @NotBlank(message = "상품 설명은 필수입니다.") String content,
    @NotEmpty(message = "상품 옵션은 최소 1개 이상 등록해야합니다.") List<CreateSkuCommand> skus) {
  public CreateProductCommand toCommand(
      UUID creatorId) {
    return new CreateProductCommand(
        hashTags, creatorId, name, content, skus);
  }
}
