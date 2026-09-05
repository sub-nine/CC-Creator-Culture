package com.sub9.productservice.category.application.command.repository;

import com.sub9.productservice.category.domain.entity.Hashtag;

import java.util.Optional;
import java.util.UUID;

public interface HashtagCommandRepository {

    Hashtag save(Hashtag hashtag);

    Optional<Hashtag> findById(UUID hashtagId);
}
