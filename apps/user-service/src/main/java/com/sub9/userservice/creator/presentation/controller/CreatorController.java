package com.sub9.userservice.creator.presentation.controller;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.userservice.creator.application.service.CreatorQueryService;
import com.sub9.userservice.creator.presentation.response.CreatorPageResponse;
import com.sub9.userservice.creator.presentation.response.CreatorSummaryResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/creators")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorQueryService creatorQueryService;

    @GetMapping
    public ApiResponse<CreatorPageResponse> getCreators(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "creatorName,asc")
            @Pattern(regexp = "creatorName,(?i:asc|desc)") String sort) {
        return ApiResponse.success(
                "창작자 목록을 조회했습니다.",
                creatorQueryService.getCreators(page, size, keyword, sort));
    }

    @GetMapping("/{creatorId}")
    public ApiResponse<CreatorSummaryResponse> getCreator(@PathVariable UUID creatorId) {
        return ApiResponse.success(
                "창작자 정보를 조회했습니다.",
                creatorQueryService.getCreator(creatorId));
    }
}
