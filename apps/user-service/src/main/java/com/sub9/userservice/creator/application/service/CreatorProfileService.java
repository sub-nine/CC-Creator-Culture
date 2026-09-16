package com.sub9.userservice.creator.application.service;

import com.sub9.common.exception.BusinessException;
import com.sub9.userservice.auth.domain.exception.UserErrorCode;
import com.sub9.userservice.creator.domain.exception.CreatorErrorCode;
import com.sub9.userservice.creator.domain.model.Creator;
import com.sub9.userservice.creator.domain.repository.CreatorRepository;
import com.sub9.userservice.creator.presentation.request.UpdateCreatorRequest;
import com.sub9.userservice.creator.presentation.response.MyCreatorResponse;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreatorProfileService {

    private final CreatorRepository creatorRepository;
    private final Clock clock;

    @Transactional
    public MyCreatorResponse updateMyCreator(UUID userId, UpdateCreatorRequest request) {
        Creator creator = creatorRepository.findApprovedActiveByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(CreatorErrorCode.CREATOR_NOT_FOUND));

        String creatorName = request.creatorNameProvided()
                ? request.creatorName()
                : creator.getCreatorName();
        String businessNumber = request.businessRegistrationNumberProvided()
                ? request.businessRegistrationNumber()
                : creator.getBusinessRegistrationNumber();

        validateCreatorName(creator, creatorName);
        validateBusinessNumber(creator, businessNumber);
        boolean changed = creator.updateProfile(
                creatorName, businessNumber, userId, clock.instant());

        if (changed) {
            try {
                creatorRepository.flush();
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(
                        CreatorErrorCode.CREATOR_PROFILE_VALUE_ALREADY_EXISTS);
            }
        }
        return MyCreatorResponse.from(creator);
    }

    private void validateCreatorName(Creator creator, String creatorName) {
        if (!creator.getCreatorName().equals(creatorName)
                && creatorRepository.existsByCreatorNameIncludingDeleted(creatorName)) {
            throw new BusinessException(UserErrorCode.CREATOR_NAME_ALREADY_EXISTS);
        }
    }

    private void validateBusinessNumber(Creator creator, String businessNumber) {
        if (!creator.getBusinessRegistrationNumber().equals(businessNumber)
                && creatorRepository.existsByBusinessRegistrationNumberIncludingDeleted(
                        businessNumber)) {
            throw new BusinessException(
                    UserErrorCode.BUSINESS_REGISTRATION_NUMBER_ALREADY_EXISTS);
        }
    }
}
