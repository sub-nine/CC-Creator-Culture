package com.sub9.userservice.creator.presentation.controller;

import com.sub9.userservice.creator.application.service.InternalCreatorQueryService;
import com.sub9.userservice.creator.presentation.response.CreatorNameResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/creators")
@RequiredArgsConstructor
public class InternalCreatorQueryController {

    private final InternalCreatorQueryService internalCreatorQueryService;

    @PostMapping("/names")
    public List<CreatorNameResponse> getCreatorNames(
            @RequestBody @NotNull @Size(max = 50) List<@NotNull UUID> creatorIds) {
        return internalCreatorQueryService.getCreatorNames(creatorIds);
    }
}
