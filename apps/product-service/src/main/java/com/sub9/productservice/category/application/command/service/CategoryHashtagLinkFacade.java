package com.sub9.productservice.category.application.command.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.productservice.category.application.command.port.in.LinkHashtagToCategoryUseCase;
import com.sub9.productservice.category.application.command.port.out.CategoryCommandRepository;
import com.sub9.productservice.category.application.command.port.out.HashtagCommandRepository;
import com.sub9.productservice.category.application.command.similarity.CategorySimilarityPipeline;
import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.domain.exception.CategoryErrorCode;
import com.sub9.productservice.category.domain.model.CategoryCandidateResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryHashtagLinkFacade implements LinkHashtagToCategoryUseCase {

    private final CategoryCommandRepository categoryCommandRepository;
    private final HashtagCommandRepository hashtagCommandRepository;
    private final CategorySimilarityPipeline categorySimilarityPipeline;
    private final CategoryHashtagLinkWriter categoryHashtagLinkWriter;

    @Override
    public void tryLink(UUID hashtagId) {
        Hashtag hashtag = hashtagCommandRepository.findById(hashtagId)
                .orElseThrow(() -> new BusinessException(CategoryErrorCode.HASHTAG_NOT_FOUND));
        List<Category> categories = categoryCommandRepository.findAllActive();

        List<CategoryCandidateResult> candidateResults = categorySimilarityPipeline.resolve(hashtag, categories);

        categoryHashtagLinkWriter.applyResults(hashtagId, hashtag, candidateResults);
    }
}
