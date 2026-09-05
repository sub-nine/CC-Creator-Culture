package com.sub9.orderservice.coupon.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.coupon.domain.model.Coupon;
import com.sub9.orderservice.coupon.domain.model.UserCoupon;
import com.sub9.orderservice.coupon.domain.repository.CouponRepository;
import com.sub9.orderservice.coupon.domain.repository.UserCouponRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'UTC'",
        "management.tracing.export.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("쿠폰 도메인 PostgreSQL 영속성")
class CouponPersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-09-01T01:00:00Z");
    private static final Instant EXPIRED_AT = Instant.parse("2026-09-03T14:59:59Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("order_service_test")
            .withUsername("test")
            .withPassword("test");

    private final UuidV7Generator uuidGenerator = new UuidV7Generator();

    @Autowired private CouponRepository couponRepository;
    @Autowired private UserCouponRepository userCouponRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from p_user_coupons");
        jdbcTemplate.update("delete from p_coupons");
    }

    @Test
    @DisplayName("쿠폰과 사용자 쿠폰을 저장하고 식별자와 소유 관계로 조회한다")
    void when_coupon_and_user_coupon_are_saved_relationship_is_restored() {
        Coupon coupon = coupon(10);
        UUID userId = uuidGenerator.generate();
        UserCoupon userCoupon = UserCoupon.issue(uuidGenerator.generate(), coupon, userId, STARTED_AT);

        transaction().executeWithoutResult(status -> {
            couponRepository.save(coupon);
            userCouponRepository.save(userCoupon);
            entityManager.flush();
        });

        assertThat(couponRepository.findActiveById(coupon.getId())).isPresent();
        assertThat(userCouponRepository.findById(userCoupon.getId()))
                .get()
                .extracting(UserCoupon::getCouponId, UserCoupon::getUserId)
                .containsExactly(coupon.getId(), userId);
        assertThat(userCouponRepository.existsByCouponIdAndUserId(coupon.getId(), userId)).isTrue();
    }

    @Test
    @DisplayName("발급 가능한 쿠폰만 조건부로 수량과 수정 감사를 갱신한다")
    void when_coupon_is_issuable_conditional_update_changes_quantity_and_audit() {
        Coupon coupon = coupon(1);
        UUID userId = uuidGenerator.generate();
        Instant issuedAt = STARTED_AT.plusSeconds(1);
        transaction().executeWithoutResult(status -> couponRepository.save(coupon));

        Integer affectedRows = transaction().execute(status ->
                couponRepository.increaseIssuedQuantityIfIssuable(coupon.getId(), userId, issuedAt));
        Coupon updated = couponRepository.findActiveById(coupon.getId()).orElseThrow();
        Integer soldOutRows = transaction().execute(status ->
                couponRepository.increaseIssuedQuantityIfIssuable(coupon.getId(), userId, issuedAt));

        assertThat(affectedRows).isEqualTo(1);
        assertThat(updated.getIssuedQuantity()).isEqualTo(1);
        assertThat(updated.getUpdatedBy()).isEqualTo(userId);
        assertThat(updated.getUpdatedAt()).isEqualTo(issuedAt);
        assertThat(soldOutRows).isZero();
    }

    @Test
    @DisplayName("기간 밖이거나 삭제된 쿠폰은 조건부 수량 갱신에서 제외한다")
    void when_coupon_is_outside_period_or_deleted_conditional_update_changes_nothing() {
        Coupon coupon = coupon(10);
        transaction().executeWithoutResult(status -> couponRepository.save(coupon));

        Integer beforeStart = transaction().execute(status -> couponRepository
                .increaseIssuedQuantityIfIssuable(coupon.getId(), uuidGenerator.generate(), STARTED_AT.minusNanos(1)));
        coupon.delete(uuidGenerator.generate(), STARTED_AT.plusSeconds(1));
        transaction().executeWithoutResult(status -> couponRepository.save(coupon));
        Integer deleted = transaction().execute(status -> couponRepository
                .increaseIssuedQuantityIfIssuable(coupon.getId(), uuidGenerator.generate(), STARTED_AT.plusSeconds(2)));

        assertThat(beforeStart).isZero();
        assertThat(deleted).isZero();
    }

    @Test
    @DisplayName("쿠폰 테이블의 제약, 인덱스와 모든 시간 컬럼을 생성한다")
    void when_schema_is_created_expected_constraints_indexes_and_timestamp_types_exist() {
        Set<String> constraints = Set.copyOf(jdbcTemplate.queryForList("""
                select constraint_name
                  from information_schema.table_constraints
                 where table_schema = 'public'
                   and table_name in ('p_coupons', 'p_user_coupons')
                """, String.class));
        Set<String> indexes = Set.copyOf(jdbcTemplate.queryForList("""
                select indexname
                  from pg_indexes
                 where schemaname = 'public'
                   and tablename in ('p_coupons', 'p_user_coupons')
                """, String.class));

        assertThat(constraints).contains(
                "chk_coupon_discount_rate", "chk_coupon_total_quantity",
                "chk_coupon_quantity", "chk_coupon_period",
                "uk_user_coupon_user_coupon", "chk_user_coupon_status",
                "chk_user_coupon_usage", "fk_user_coupons_coupon");
        assertThat(indexes).contains(
                "idx_coupon_period", "idx_coupon_deleted_at",
                "idx_user_coupon_user_status", "idx_user_coupon_coupon");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*)
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name in ('p_coupons', 'p_user_coupons')
                   and column_name in ('created_at', 'updated_at', 'deleted_at',
                                       'started_at', 'expired_at', 'issued_at', 'used_at')
                   and data_type = 'timestamp with time zone'
                """, Integer.class)).isEqualTo(10);
    }

    @Test
    @DisplayName("동일 사용자의 동일 쿠폰 중복 발급을 데이터베이스가 거부한다")
    void when_same_coupon_is_issued_to_same_user_twice_database_rejects_duplicate() {
        Coupon coupon = coupon(10);
        UUID userId = uuidGenerator.generate();
        transaction().executeWithoutResult(status -> {
            couponRepository.save(coupon);
            userCouponRepository.save(UserCoupon.issue(uuidGenerator.generate(), coupon, userId, STARTED_AT));
            entityManager.flush();
        });

        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            UserCoupon duplicate = UserCoupon.issue(uuidGenerator.generate(), coupon, userId, STARTED_AT.plusSeconds(1));
            userCouponRepository.save(duplicate);
            entityManager.flush();
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("사용 상태와 사용 정보가 일치하지 않는 사용자 쿠폰을 데이터베이스가 거부한다")
    void when_user_coupon_usage_fields_conflict_with_status_database_rejects_write() {
        Coupon coupon = coupon(10);
        transaction().executeWithoutResult(status -> couponRepository.save(coupon));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into p_user_coupons (
                    id, coupon_id, user_id, status, issued_at, used_at, order_id,
                    created_at, created_by, updated_at
                ) values (?, ?, ?, 'ISSUED', ?, ?, ?, ?, ?, ?)
                """,
                uuidGenerator.generate(), coupon.getId(), uuidGenerator.generate(), STARTED_AT,
                STARTED_AT.plusSeconds(1), uuidGenerator.generate(),
                CREATED_AT, uuidGenerator.generate(), CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Coupon coupon(int totalQuantity) {
        return Coupon.create(
                uuidGenerator.generate(), "영속성 테스트 쿠폰", 10, totalQuantity,
                STARTED_AT, EXPIRED_AT, uuidGenerator.generate(), CREATED_AT);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
