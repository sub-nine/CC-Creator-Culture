package com.sub9.common.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

public record PageResponse<T>(
    List<T> data,
    Integer totalPages,
    int pageSize,
    Long totalElements,
    int pageNumber,
    boolean hasNext) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getTotalPages(),
        page.getSize(),
        page.getTotalElements(),
        page.getNumber(),
        page.hasNext());
  }

  public static <T> PageResponse<T> of(Slice<T> slice) {
    return new PageResponse<>(
        slice.getContent(), null, slice.getSize(), null, slice.getNumber(), slice.hasNext());
  }
}
