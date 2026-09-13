CREATE TABLE p_carts (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    quantity integer NOT NULL,
    CONSTRAINT uk_carts_user_id_sku_id UNIQUE (user_id, sku_id)
);

CREATE INDEX idx_carts_user_id ON p_carts (user_id);

CREATE TABLE p_coupons (
    id uuid PRIMARY KEY,
    coupon_name varchar(100) NOT NULL,
    discount_rate integer NOT NULL,
    total_quantity integer NOT NULL,
    issued_quantity integer NOT NULL DEFAULT 0,
    started_at timestamp with time zone NOT NULL,
    expired_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    CONSTRAINT chk_coupon_discount_rate CHECK (discount_rate between 1 and 100),
    CONSTRAINT chk_coupon_total_quantity CHECK (total_quantity >= 1),
    CONSTRAINT chk_coupon_quantity CHECK (issued_quantity >= 0 and issued_quantity <= total_quantity),
    CONSTRAINT chk_coupon_period CHECK (started_at < expired_at)
);

CREATE INDEX idx_coupon_period ON p_coupons (started_at, expired_at);
CREATE INDEX idx_coupon_deleted_at ON p_coupons (deleted_at);

CREATE TABLE p_user_coupons (
    id uuid PRIMARY KEY,
    coupon_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    order_id uuid,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid NOT NULL,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    CONSTRAINT uk_user_coupon_user_coupon UNIQUE (user_id, coupon_id),
    CONSTRAINT chk_user_coupon_status CHECK (status in ('ISSUED', 'USED')),
    CONSTRAINT chk_user_coupon_usage CHECK (
        (status = 'ISSUED' and used_at is null and order_id is null)
        or (status = 'USED' and used_at is not null and order_id is not null)
    ),
    CONSTRAINT fk_user_coupons_coupon FOREIGN KEY (coupon_id) REFERENCES p_coupons (id)
);

CREATE INDEX idx_user_coupon_user_status ON p_user_coupons (user_id, status);
CREATE INDEX idx_user_coupon_coupon ON p_user_coupons (coupon_id);

CREATE TABLE p_orders (
    id uuid PRIMARY KEY,
    order_number varchar(40) NOT NULL,
    user_id uuid NOT NULL,
    status varchar(30) NOT NULL,
    original_amount bigint NOT NULL,
    discount_amount bigint NOT NULL,
    payment_amount bigint NOT NULL,
    recipient_name varchar(50) NOT NULL,
    recipient_phone varchar(20) NOT NULL,
    postal_code varchar(10) NOT NULL,
    address_line1 varchar(200) NOT NULL,
    address_line2 varchar(200),
    expires_at timestamp with time zone NOT NULL,
    paid_at timestamp with time zone,
    canceled_at timestamp with time zone,
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT ck_orders_status CHECK (status in ('PENDING_PAYMENT', 'PAID', 'PROCESSING', 'COMPLETED', 'EXPIRED', 'FAILED', 'CANCELED')),
    CONSTRAINT ck_orders_original_amount CHECK (original_amount >= 0),
    CONSTRAINT ck_orders_discount_amount CHECK (discount_amount >= 0 and discount_amount <= original_amount),
    CONSTRAINT ck_orders_payment_amount CHECK (payment_amount = original_amount - discount_amount),
    CONSTRAINT ck_orders_paid_at CHECK (
        (status in ('PENDING_PAYMENT', 'FAILED', 'EXPIRED') and paid_at is null)
        or (status in ('PAID', 'PROCESSING', 'COMPLETED', 'CANCELED') and paid_at is not null)
    ),
    CONSTRAINT ck_orders_canceled_at CHECK (
        (status = 'CANCELED' and canceled_at is not null)
        or (status <> 'CANCELED' and canceled_at is null)
    )
);

CREATE INDEX idx_orders_user_created_at ON p_orders (user_id, created_at DESC);
CREATE INDEX idx_orders_status_expires_at ON p_orders (status, expires_at);

CREATE TABLE p_order_items (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    cart_item_id uuid,
    creator_id uuid NOT NULL,
    product_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    user_coupon_id uuid,
    product_name varchar(200) NOT NULL,
    sku_name varchar(100) NOT NULL,
    unit_price bigint NOT NULL,
    quantity integer NOT NULL,
    original_amount bigint NOT NULL,
    discount_amount bigint NOT NULL,
    payment_amount bigint NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_order_items_order_sku UNIQUE (order_id, sku_id),
    CONSTRAINT ck_order_items_status CHECK (status in ('ORDERED', 'PREPARING', 'SHIPPED', 'DELIVERED', 'COMPLETED', 'CANCELED')),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_original_amount CHECK (original_amount = unit_price * quantity),
    CONSTRAINT ck_order_items_discount_amount CHECK (discount_amount >= 0 and discount_amount <= original_amount),
    CONSTRAINT ck_order_items_payment_amount CHECK (payment_amount = original_amount - discount_amount),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES p_orders (id)
);

CREATE INDEX idx_order_items_order_id ON p_order_items (order_id);
CREATE INDEX idx_order_items_creator_status_created ON p_order_items (creator_id, status, created_at DESC);

CREATE TABLE p_order_command_requests (
    id uuid PRIMARY KEY,
    actor_id uuid NOT NULL,
    command_type varchar(30) NOT NULL,
    idempotency_key varchar(100) NOT NULL,
    request_hash varchar(64) NOT NULL,
    status varchar(20) NOT NULL,
    order_id uuid,
    response_status smallint,
    response_payload jsonb,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_order_command_actor_type_key UNIQUE (actor_id, command_type, idempotency_key),
    CONSTRAINT ck_order_command_type CHECK (command_type in ('CREATE_ORDER', 'CANCEL_ORDER')),
    CONSTRAINT ck_order_command_status CHECK (status in ('PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_order_command_request_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_order_command_completion CHECK (
        (status = 'PROCESSING' and response_status is null and response_payload is null and completed_at is null)
        or (status = 'SUCCEEDED' and response_status between 200 and 299 and response_payload is not null and completed_at is not null)
        or (status = 'FAILED' and response_status between 400 and 599 and response_payload is not null and completed_at is not null)
    ),
    CONSTRAINT fk_order_command_requests_order FOREIGN KEY (order_id) REFERENCES p_orders (id)
);

CREATE INDEX idx_order_command_order_id ON p_order_command_requests (order_id);

CREATE TABLE p_order_cart_cleanup_tasks (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    payload text NOT NULL,
    created_at timestamp NOT NULL,
    next_attempt_at timestamp NOT NULL,
    CONSTRAINT uk_order_cart_cleanup_tasks_order_id UNIQUE (order_id)
);

CREATE INDEX idx_cart_cleanup_due ON p_order_cart_cleanup_tasks (next_attempt_at, id);

CREATE TABLE p_payments (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL,
    method varchar(20) NOT NULL,
    amount bigint NOT NULL,
    status varchar(20) NOT NULL,
    failure_code varchar(50),
    processed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT ck_payments_method CHECK (method = 'MOCK'),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0),
    CONSTRAINT ck_payments_status CHECK (status in ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_payments_failure_code CHECK (
        (status = 'SUCCESS' and failure_code is null)
        or (status = 'FAILED' and failure_code is not null and failure_code = 'MOCK_PAYMENT_FAILED')
    ),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES p_orders (id)
);

CREATE TABLE p_payment_cancellations (
    id uuid PRIMARY KEY,
    payment_id uuid NOT NULL,
    command_request_id uuid NOT NULL,
    amount bigint NOT NULL,
    reason_code varchar(50) NOT NULL,
    canceled_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_payment_cancellations_command_request_id UNIQUE (command_request_id),
    CONSTRAINT uk_payment_cancellations_payment_id UNIQUE (payment_id),
    CONSTRAINT ck_payment_cancellations_amount CHECK (amount >= 0),
    CONSTRAINT ck_payment_cancellations_reason_code CHECK (reason_code = 'CUSTOMER_REQUEST'),
    CONSTRAINT fk_payment_cancellations_payment FOREIGN KEY (payment_id) REFERENCES p_payments (id),
    CONSTRAINT fk_payment_cancellations_command FOREIGN KEY (command_request_id) REFERENCES p_order_command_requests (id)
);
