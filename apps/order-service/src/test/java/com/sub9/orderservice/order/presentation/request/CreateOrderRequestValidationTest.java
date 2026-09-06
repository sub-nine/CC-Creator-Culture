package com.sub9.orderservice.order.presentation.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주문 생성 요청 검증")
class CreateOrderRequestValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    @DisplayName("장바구니 항목과 배송지가 유효하면 요청을 허용한다")
    void when_order_request_is_valid_validation_succeeds() {
        CreateOrderRequest request = validRequest();

        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("주문 상품이 없거나 장바구니 항목 식별자가 없으면 요청을 거부한다")
    void when_order_items_are_invalid_validation_fails() {
        CreateOrderRequest emptyItems = new CreateOrderRequest(List.of(), validShippingAddress());
        CreateOrderRequest missingCartItemId = new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(null, UUID.randomUUID())),
                validShippingAddress());

        assertThat(pathsOf(VALIDATOR.validate(emptyItems))).containsExactly("items");
        assertThat(pathsOf(VALIDATOR.validate(missingCartItemId))).containsExactly("items[0].cartItemId");
    }

    @Test
    @DisplayName("배송지가 없으면 요청을 거부한다")
    void when_shipping_address_is_missing_validation_fails() {
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(UUID.randomUUID(), null)),
                null);

        assertThat(pathsOf(VALIDATOR.validate(request))).containsExactly("shippingAddress");
    }

    @Test
    @DisplayName("배송지 필수값이 공백이면 요청을 거부한다")
    void when_required_shipping_address_value_is_blank_validation_fails() {
        CreateOrderRequest.ShippingAddress address = new CreateOrderRequest.ShippingAddress(
                " ", " ", " ", " ", null);

        assertThat(pathsOf(VALIDATOR.validate(address)))
                .containsExactlyInAnyOrder("recipientName", "recipientPhone", "postalCode", "addressLine1");
    }

    @Test
    @DisplayName("배송지 필드가 최대 길이를 넘으면 요청을 거부한다")
    void when_shipping_address_value_exceeds_maximum_length_validation_fails() {
        CreateOrderRequest.ShippingAddress address = new CreateOrderRequest.ShippingAddress(
                "가".repeat(51),
                "1".repeat(21),
                "1".repeat(11),
                "가".repeat(201),
                "가".repeat(201));

        assertThat(pathsOf(VALIDATOR.validate(address)))
                .containsExactlyInAnyOrder(
                        "recipientName", "recipientPhone", "postalCode", "addressLine1", "addressLine2");
    }

    private static CreateOrderRequest validRequest() {
        return new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(UUID.randomUUID(), null)),
                validShippingAddress());
    }

    private static CreateOrderRequest.ShippingAddress validShippingAddress() {
        return new CreateOrderRequest.ShippingAddress(
                "홍길동", "010-1234-5678", "06236", "서울특별시 강남구 테헤란로 1", null);
    }

    private static Set<String> pathsOf(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
