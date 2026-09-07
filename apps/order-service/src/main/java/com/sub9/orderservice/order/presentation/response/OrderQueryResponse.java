package com.sub9.orderservice.order.presentation.response;

import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.OrderItemStatus;
import com.sub9.orderservice.order.domain.model.OrderStatus;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrderQueryResponse {

    private OrderQueryResponse() {
    }

    public record CustomerOrderSummary(
            String orderNumber,
            OrderStatus status,
            long originalAmount,
            long discountAmount,
            long paymentAmount,
            Instant createdAt,
            Instant expiresAt
    ) {
        public static CustomerOrderSummary from(Order order) {
            return new CustomerOrderSummary(
                    order.getOrderNumber().toString(),
                    order.getStatus(),
                    order.getOriginalAmount().getAmount(),
                    order.getDiscountAmount().getAmount(),
                    order.getPaymentAmount().getAmount(),
                    order.getCreatedAt(),
                    order.getExpiresAt());
        }
    }

    public record CustomerOrderDetail(
            String orderNumber,
            OrderStatus status,
            long originalAmount,
            long discountAmount,
            long paymentAmount,
            Instant createdAt,
            Instant expiresAt,
            ShippingAddressResponse shippingAddress,
            List<CreatorGroup> creatorGroups
    ) {
        public static CustomerOrderDetail from(Order order) {
            return new CustomerOrderDetail(
                    order.getOrderNumber().toString(),
                    order.getStatus(),
                    order.getOriginalAmount().getAmount(),
                    order.getDiscountAmount().getAmount(),
                    order.getPaymentAmount().getAmount(),
                    order.getCreatedAt(),
                    order.getExpiresAt(),
                    ShippingAddressResponse.from(order.getShippingAddress()),
                    toCreatorGroups(order));
        }
    }

    public record CreatorOrderItemSummary(
            UUID orderItemId,
            String orderNumber,
            OrderStatus orderStatus,
            String productName,
            String skuName,
            int quantity,
            long paymentAmount,
            OrderItemStatus status,
            Instant createdAt
    ) {
        public static CreatorOrderItemSummary from(OrderItem item) {
            return new CreatorOrderItemSummary(
                    item.getId(),
                    item.getOrder().getOrderNumber().toString(),
                    item.getOrder().getStatus(),
                    item.getProductSnapshot().getProductName(),
                    item.getProductSnapshot().getSkuName(),
                    item.getProductSnapshot().getQuantity(),
                    item.getPaymentAmount().getAmount(),
                    item.getStatus(),
                    item.getCreatedAt());
        }
    }

    public record CreatorOrderItemDetail(
            UUID orderItemId,
            String orderNumber,
            OrderStatus orderStatus,
            UUID productId,
            UUID skuId,
            String productName,
            String skuName,
            long unitPrice,
            int quantity,
            long originalAmount,
            long discountAmount,
            long paymentAmount,
            OrderItemStatus status,
            Instant createdAt
    ) {
        public static CreatorOrderItemDetail from(OrderItem item) {
            return new CreatorOrderItemDetail(
                    item.getId(),
                    item.getOrder().getOrderNumber().toString(),
                    item.getOrder().getStatus(),
                    item.getProductId(),
                    item.getSkuId(),
                    item.getProductSnapshot().getProductName(),
                    item.getProductSnapshot().getSkuName(),
                    item.getProductSnapshot().getUnitPrice().getAmount(),
                    item.getProductSnapshot().getQuantity(),
                    item.getOriginalAmount().getAmount(),
                    item.getDiscountAmount().getAmount(),
                    item.getPaymentAmount().getAmount(),
                    item.getStatus(),
                    item.getCreatedAt());
        }
    }

    public record AdminOrderSummary(
            String orderNumber,
            UUID customerId,
            OrderStatus status,
            long originalAmount,
            long discountAmount,
            long paymentAmount,
            Instant createdAt,
            Instant expiresAt
    ) {
        public static AdminOrderSummary from(Order order) {
            return new AdminOrderSummary(
                    order.getOrderNumber().toString(),
                    order.getCustomerId(),
                    order.getStatus(),
                    order.getOriginalAmount().getAmount(),
                    order.getDiscountAmount().getAmount(),
                    order.getPaymentAmount().getAmount(),
                    order.getCreatedAt(),
                    order.getExpiresAt());
        }
    }

    public record AdminOrderDetail(
            String orderNumber,
            UUID customerId,
            OrderStatus status,
            long originalAmount,
            long discountAmount,
            long paymentAmount,
            Instant createdAt,
            Instant expiresAt,
            List<CreatorGroup> creatorGroups
    ) {
        public static AdminOrderDetail from(Order order) {
            return new AdminOrderDetail(
                    order.getOrderNumber().toString(),
                    order.getCustomerId(),
                    order.getStatus(),
                    order.getOriginalAmount().getAmount(),
                    order.getDiscountAmount().getAmount(),
                    order.getPaymentAmount().getAmount(),
                    order.getCreatedAt(),
                    order.getExpiresAt(),
                    toCreatorGroups(order));
        }
    }

    public record ShippingAddressResponse(
            String recipientName,
            String recipientPhone,
            String postalCode,
            String addressLine1,
            String addressLine2
    ) {
        public static ShippingAddressResponse from(ShippingAddress address) {
            return new ShippingAddressResponse(
                    address.getRecipientName(),
                    address.getRecipientPhone(),
                    address.getPostalCode(),
                    address.getAddressLine1(),
                    address.getAddressLine2());
        }
    }

    public record CreatorGroup(UUID creatorId, List<OrderItemDetail> items) {
    }

    public record OrderItemDetail(
            UUID orderItemId,
            UUID productId,
            UUID skuId,
            String productName,
            String skuName,
            long unitPrice,
            int quantity,
            long discountAmount,
            long paymentAmount,
            OrderItemStatus status
    ) {
        public static OrderItemDetail from(OrderItem item) {
            return new OrderItemDetail(
                    item.getId(),
                    item.getProductId(),
                    item.getSkuId(),
                    item.getProductSnapshot().getProductName(),
                    item.getProductSnapshot().getSkuName(),
                    item.getProductSnapshot().getUnitPrice().getAmount(),
                    item.getProductSnapshot().getQuantity(),
                    item.getDiscountAmount().getAmount(),
                    item.getPaymentAmount().getAmount(),
                    item.getStatus());
        }
    }

    private static List<CreatorGroup> toCreatorGroups(Order order) {
        Map<UUID, List<OrderItemDetail>> groupedItems = new LinkedHashMap<>();
        order.getItems().forEach(item -> groupedItems
                .computeIfAbsent(item.getCreatorId(), ignored -> new ArrayList<>())
                .add(OrderItemDetail.from(item)));
        return groupedItems.entrySet().stream()
                .map(entry -> new CreatorGroup(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }
}
