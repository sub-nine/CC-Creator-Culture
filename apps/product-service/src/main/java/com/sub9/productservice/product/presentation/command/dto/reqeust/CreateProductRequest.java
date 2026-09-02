package com.sub9.productservice.product.presentation.command.dto.reqeust;

import com.sub9.productservice.product.application.command.dto.CreateProductCommand;
import com.sub9.productservice.product.application.command.dto.CreateSkuCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
    @NotNull(message = "해시태그는 필수입니다.")
        @Size(min = 1, max = 5, message = "해시태그는 최소 1개 이상 최대 5개 이하로 등록해야 합니다.")
        List<
                @NotBlank(message = "해시태그는 빈 값일 수  없습니다.")
                @Size(max = 10, message = "해시태그는 10자를 초과할 수 없습니다.")
                @Pattern(regexp = "^[\\p{L}\\p{N}]+$", message = "해시태그는 문자와 숫자만 사용할 수 있습니다.")
                String>
            hashTags,
    @NotBlank(message = "상품명은 필수입니다.") String name,
    @NotBlank(message = "상품 설명은 필수입니다.") String content,
    @Valid @NotEmpty(message = "상품 옵션은 최소 1개 이상 등록해야합니다.") List<CreateSkuRequest> skus) {
  public CreateProductCommand toCommand(UUID creatorId) {
    List<CreateSkuCommand> skuCommands = skus.stream().map(CreateSkuRequest::toCommand).toList();

    return new CreateProductCommand(hashTags, creatorId, name, content, skuCommands);
  }
}
