package com.sub9.productservice.product.presentation.command.mapper;

import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.productservice.product.application.command.dto.product.UploadImageCommand;
import java.io.IOException;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

@UtilityClass
public class UploadImageMapper {
  public List<UploadImageCommand> from(List<MultipartFile> files) {
    if (files == null || files.isEmpty()) {
      return List.of();
    }

    return files.stream().map(UploadImageMapper::from).toList();
  }

  private static UploadImageCommand from(MultipartFile file) {
    try {
      return new UploadImageCommand(file.getContentType(), file.getBytes());
    } catch (IOException e) {
      throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
