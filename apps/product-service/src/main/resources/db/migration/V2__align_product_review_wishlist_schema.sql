CREATE UNIQUE INDEX uk_skus_product_default
    ON p_skus (product_id)
    WHERE is_default = TRUE
    AND deleted_at IS NULL;

CREATE INDEX idx_images_product_id_deleted_at
    ON p_images (product_id, deleted_at);

CREATE INDEX idx_images_deleted_at
    ON p_images (deleted_at);

CREATE TABLE p_reviews (
    id uuid PRIMARY KEY,
    order_item_id uuid NOT NULL,
    product_id uuid NOT NULL,
    user_id uuid NOT NULL,
    rating integer NOT NULL,
    content varchar(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    deleted_by uuid
);

CREATE INDEX idx_reviews_product_id_created_at
    ON p_reviews (product_id, created_at);

CREATE UNIQUE INDEX uk_reviews_order_item
    ON p_reviews (order_item_id)
    WHERE deleted_at IS NULL;

CREATE TABLE p_wishlists (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    product_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_wishlist_user_id_product_id UNIQUE (user_id, product_id)
);

CREATE INDEX idx_wishlist_product_id
    ON p_wishlists (product_id);
