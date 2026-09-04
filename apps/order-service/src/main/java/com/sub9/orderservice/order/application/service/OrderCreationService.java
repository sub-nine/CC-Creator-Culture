package com.sub9.orderservice.order.application.service;

import com.sub9.common.dto.response.ApiResponse;
import com.sub9.common.dto.response.ErrorResponse;
import com.sub9.common.exception.BusinessException;
import com.sub9.common.exception.CommonErrorCode;
import com.sub9.common.exception.ErrorCode;
import com.sub9.common.identifier.UuidV7Generator;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort;
import com.sub9.orderservice.order.application.port.output.CartSnapshotPort.CartItemSnapshot;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.AppliedCoupon;
import com.sub9.orderservice.order.application.port.output.CouponApplicationPort.CouponApplicationRequest;
import com.sub9.orderservice.order.application.port.output.StockOperationUncertainException;
import com.sub9.orderservice.order.application.port.output.StockPort;
import com.sub9.orderservice.order.application.port.output.StockPort.RestoreReason;
import com.sub9.orderservice.order.application.port.output.StockPort.StockItem;
import com.sub9.orderservice.order.domain.exception.OrderErrorCode;
import com.sub9.orderservice.order.domain.model.Money;
import com.sub9.orderservice.order.domain.model.Order;
import com.sub9.orderservice.order.domain.model.OrderCommandType;
import com.sub9.orderservice.order.domain.model.OrderItem;
import com.sub9.orderservice.order.domain.model.ProductSnapshot;
import com.sub9.orderservice.order.domain.model.ShippingAddress;
import com.sub9.orderservice.order.presentation.response.CreateOrderResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCreationService {

    private final OrderCommandIdempotencyService idempotencyService;
    private final CartSnapshotPort cartSnapshotPort;
    private final CouponApplicationPort couponApplicationPort;
    private final StockPort stockPort;
    private final OrderCreationTransactionService transactionService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public OrderCreationResult create(UUID customerId, String idempotencyKey,
            CreateOrderCommand command) {
        OrderCommandAcquireResult acquireResult = idempotencyService.acquire(
                customerId, OrderCommandType.CREATE_ORDER, idempotencyKey, command);
        if (acquireResult instanceof OrderCommandAcquireResult.Replay replay) {
            return new OrderCreationResult(replay.httpStatus(), replay.responseBody());
        }

        UUID commandRequestId = ((OrderCommandAcquireResult.Started) acquireResult).commandRequestId();
        PreparedOrder preparedOrder = null;
        boolean stockDeducted = false;
        try {
            preparedOrder = prepare(customerId, command);
            stockPort.deduct(preparedOrder.order().getId(), preparedOrder.stockItems());
            stockDeducted = true;
            transactionService.save(
                    commandRequestId,
                    preparedOrder.order(),
                    preparedOrder.appliedCoupons(),
                    preparedOrder.responseBody());
            return new OrderCreationResult(201, preparedOrder.responseBody());
        } catch (StockOperationUncertainException exception) {
            throw exception;
        } catch (BusinessException exception) {
            if (stockDeducted) {
                restoreStock(preparedOrder, exception);
            }
            completeFailure(commandRequestId, exception.getErrorCode());
            throw exception;
        } catch (RuntimeException exception) {
            if (stockDeducted) {
                restoreStock(preparedOrder, exception);
            }
            completeFailure(commandRequestId, CommonErrorCode.INTERNAL_SERVER_ERROR);
            throw exception;
        }
    }

    private PreparedOrder prepare(UUID customerId, CreateOrderCommand command) {
        List<CreateOrderCommand.Item> requestedItems = validateRequestedItems(command);
        List<UUID> cartItemIds = requestedItems.stream()
                .map(CreateOrderCommand.Item::cartItemId)
                .toList();
        Map<UUID, CartItemSnapshot> snapshots = validateSnapshots(
                requestedItems,
                cartSnapshotPort.getCartItems(customerId, cartItemIds));
        List<AppliedCoupon> appliedCoupons = applyCoupons(customerId, requestedItems, snapshots);
        Map<UUID, AppliedCoupon> couponsByCartItemId = appliedCoupons.stream()
                .collect(Collectors.toMap(AppliedCoupon::cartItemId, coupon -> coupon));

        List<OrderItem> orderItems = new ArrayList<>(requestedItems.size());
        List<StockItem> stockItems = new ArrayList<>(requestedItems.size());
        for (CreateOrderCommand.Item requestedItem : requestedItems) {
            CartItemSnapshot snapshot = snapshots.get(requestedItem.cartItemId());
            AppliedCoupon appliedCoupon = couponsByCartItemId.get(requestedItem.cartItemId());
            Money discount = Money.won(appliedCoupon == null ? 0 : appliedCoupon.discountAmount());
            orderItems.add(OrderItem.create(
                    uuidGenerator.generate(),
                    snapshot.creatorId(),
                    snapshot.productId(),
                    snapshot.skuId(),
                    requestedItem.userCouponId(),
                    toProductSnapshot(snapshot),
                    discount));
            stockItems.add(new StockItem(snapshot.skuId(), snapshot.quantity()));
        }

        UUID orderId = uuidGenerator.generate();
        Order order = Order.create(
                orderId,
                customerId,
                toShippingAddress(command.shippingAddress()),
                orderItems,
                clock.instant());
        ApiResponse<CreateOrderResponse> responseBody = ApiResponse.success(
                "주문 생성 성공",
                new CreateOrderResponse(
                        order.getOrderNumber().toString(),
                        order.getStatus(),
                        order.getOriginalAmount().getAmount(),
                        order.getDiscountAmount().getAmount(),
                        order.getPaymentAmount().getAmount(),
                        order.getExpiresAt()));
        return new PreparedOrder(order, appliedCoupons, stockItems, responseBody);
    }

    private List<CreateOrderCommand.Item> validateRequestedItems(CreateOrderCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
        Set<UUID> uniqueCartItemIds = command.items().stream()
                .filter(Objects::nonNull)
                .map(CreateOrderCommand.Item::cartItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (uniqueCartItemIds.size() != command.items().size()) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
        return command.items();
    }

    private Map<UUID, CartItemSnapshot> validateSnapshots(
            List<CreateOrderCommand.Item> requestedItems,
            List<CartItemSnapshot> snapshots) {
        if (snapshots == null || snapshots.size() != requestedItems.size()) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
        Set<UUID> requestedIds = requestedItems.stream()
                .map(CreateOrderCommand.Item::cartItemId)
                .collect(Collectors.toSet());
        Map<UUID, CartItemSnapshot> indexed = new LinkedHashMap<>();
        for (CartItemSnapshot snapshot : snapshots) {
            if (snapshot == null
                    || !requestedIds.contains(snapshot.cartItemId())
                    || indexed.putIfAbsent(snapshot.cartItemId(), snapshot) != null) {
                throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
            }
        }
        return indexed;
    }

    private List<AppliedCoupon> applyCoupons(
            UUID customerId,
            List<CreateOrderCommand.Item> requestedItems,
            Map<UUID, CartItemSnapshot> snapshots) {
        List<CouponApplicationRequest> couponRequests = requestedItems.stream()
                .filter(item -> item.userCouponId() != null)
                .map(item -> toCouponRequest(item, snapshots.get(item.cartItemId())))
                .toList();
        if (couponRequests.isEmpty()) {
            return List.of();
        }

        List<AppliedCoupon> appliedCoupons = couponApplicationPort.apply(customerId, couponRequests);
        if (appliedCoupons == null || appliedCoupons.size() != couponRequests.size()) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
        Map<UUID, CouponApplicationRequest> requestsByCartItemId = couponRequests.stream()
                .collect(Collectors.toMap(CouponApplicationRequest::cartItemId, request -> request));
        Map<UUID, AppliedCoupon> indexed = new LinkedHashMap<>();
        for (AppliedCoupon appliedCoupon : appliedCoupons) {
            CouponApplicationRequest request = appliedCoupon == null
                    ? null
                    : requestsByCartItemId.get(appliedCoupon.cartItemId());
            if (request == null
                    || !request.userCouponId().equals(appliedCoupon.userCouponId())
                    || indexed.putIfAbsent(appliedCoupon.cartItemId(), appliedCoupon) != null) {
                throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
            }
            Money.won(request.originalAmount()).subtract(Money.won(appliedCoupon.discountAmount()));
        }
        return List.copyOf(indexed.values());
    }

    private CouponApplicationRequest toCouponRequest(
            CreateOrderCommand.Item item,
            CartItemSnapshot snapshot) {
        long originalAmount = Money.won(snapshot.unitPrice())
                .multiply(snapshot.quantity())
                .getAmount();
        return new CouponApplicationRequest(
                item.cartItemId(),
                snapshot.productId(),
                snapshot.skuId(),
                item.userCouponId(),
                originalAmount);
    }

    private ProductSnapshot toProductSnapshot(CartItemSnapshot snapshot) {
        try {
            return ProductSnapshot.of(
                    snapshot.productName(),
                    snapshot.skuName(),
                    Money.won(snapshot.unitPrice()),
                    snapshot.quantity());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_ITEMS);
        }
    }

    private ShippingAddress toShippingAddress(CreateOrderCommand.ShippingAddress address) {
        if (address == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        try {
            return ShippingAddress.of(
                    address.recipientName(),
                    address.recipientPhone(),
                    address.postalCode(),
                    address.addressLine1(),
                    address.addressLine2());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
    }

    private void restoreStock(PreparedOrder preparedOrder, RuntimeException originalFailure) {
        try {
            stockPort.restore(
                    preparedOrder.order().getId(),
                    preparedOrder.stockItems(),
                    RestoreReason.ORDER_CREATION_FAILED);
        } catch (RuntimeException restoreFailure) {
            StockOperationUncertainException uncertain = new StockOperationUncertainException(
                    "주문 생성 실패 후 재고 복구 결과를 확인할 수 없습니다.",
                    restoreFailure);
            uncertain.addSuppressed(originalFailure);
            throw uncertain;
        }
    }

    private void completeFailure(UUID commandRequestId, ErrorCode errorCode) {
        idempotencyService.completeFailure(
                commandRequestId,
                null,
                errorCode.status().value(),
                ErrorResponse.from(errorCode));
    }

    private record PreparedOrder(
            Order order,
            List<AppliedCoupon> appliedCoupons,
            List<StockItem> stockItems,
            ApiResponse<CreateOrderResponse> responseBody
    ) {

        private PreparedOrder {
            appliedCoupons = List.copyOf(appliedCoupons);
            stockItems = List.copyOf(stockItems);
        }
    }
}
