package com.sub9.productservice.leaderboard.presentation.controller;

import com.sub9.productservice.category.domain.entity.Category;
import com.sub9.productservice.category.domain.entity.Hashtag;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.CategoryJpaRepository;
import com.sub9.productservice.category.infrastructure.persistence.command.repository.jpa.HashtagJpaRepository;
import com.sub9.productservice.leaderboard.domain.model.LeaderboardType;
import com.sub9.productservice.leaderboard.infrastructure.redis.LeaderboardRedisKey;
import com.sub9.productservice.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("LeaderboardController - 통합 테스트")
class LeaderboardControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private HashtagJpaRepository hashtagJpaRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    @AfterEach
    void cleanUpRedis() {
        stringRedisTemplate.delete(LeaderboardRedisKey.current(LeaderboardType.CATEGORY));
        stringRedisTemplate.delete(LeaderboardRedisKey.current(LeaderboardType.HASHTAG));
    }

    @Test
    @DisplayName("실시간 카테고리 랭킹을 점수 내림차순으로 조회한다")
    void getCategoryLeaderboard_success() throws Exception {
        Category category1 = categoryJpaRepository.save(Category.create("패션", null));
        Category category2 = categoryJpaRepository.save(Category.create("전자기기", null));

        stringRedisTemplate.opsForZSet().add(
                LeaderboardRedisKey.current(LeaderboardType.CATEGORY), category1.getId().toString(), 15.0);
        stringRedisTemplate.opsForZSet().add(
                LeaderboardRedisKey.current(LeaderboardType.CATEGORY), category2.getId().toString(), 20.0);

        mockMvc.perform(get("/api/v1/leaderboards/categories")
                        .param("period", "DAILY")
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period").value("DAILY"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].targetId").value(category2.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].name").value("전자기기"))
                .andExpect(jsonPath("$.data.items[0].ranking").value(1))
                .andExpect(jsonPath("$.data.items[0].score").value(20.0))
                .andExpect(jsonPath("$.data.items[1].targetId").value(category1.getId().toString()));
    }

    @Test
    @DisplayName("실시간 해시태그 랭킹을 점수 내림차순으로 조회한다")
    void getHashtagLeaderboard_success() throws Exception {
        Hashtag hashtag1 = hashtagJpaRepository.save(Hashtag.create("스트릿"));
        Hashtag hashtag2 = hashtagJpaRepository.save(Hashtag.create("빈티지"));

        stringRedisTemplate.opsForZSet().add(
                LeaderboardRedisKey.current(LeaderboardType.HASHTAG), hashtag1.getId().toString(), 8.0);
        stringRedisTemplate.opsForZSet().add(
                LeaderboardRedisKey.current(LeaderboardType.HASHTAG), hashtag2.getId().toString(), 3.0);

        mockMvc.perform(get("/api/v1/leaderboards/hashtags")
                        .param("period", "DAILY")
                        .param("limit", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].name").value("스트릿"))
                .andExpect(jsonPath("$.data.items[1].name").value("빈티지"));
    }

    @Test
    @DisplayName("limit을 초과하는 랭킹은 잘라서 반환한다")
    void getCategoryLeaderboard_appliesLimit() throws Exception {
        Category category1 = categoryJpaRepository.save(Category.create("카테고리1", null));
        Category category2 = categoryJpaRepository.save(Category.create("카테고리2", null));

        stringRedisTemplate.opsForZSet().add(
                LeaderboardRedisKey.current(LeaderboardType.CATEGORY), category1.getId().toString(), 5.0);
        stringRedisTemplate.opsForZSet().add(
                LeaderboardRedisKey.current(LeaderboardType.CATEGORY), category2.getId().toString(), 1.0);

        mockMvc.perform(get("/api/v1/leaderboards/categories")
                        .param("period", "DAILY")
                        .param("limit", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].targetId").value(category1.getId().toString()));
    }
}
