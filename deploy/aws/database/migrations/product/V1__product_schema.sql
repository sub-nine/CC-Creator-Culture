CREATE TABLE p_categories (
    id uuid PRIMARY KEY,
    merged_category_id uuid,
    name varchar(255) NOT NULL,
    description varchar(255),
    status varchar(255) NOT NULL,
    unique_version uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    deleted_by uuid,
    CONSTRAINT uk_categories_name UNIQUE (name, unique_version)
);

CREATE TABLE p_hashtags (
    id uuid PRIMARY KEY,
    name varchar(255) NOT NULL,
    usage_count bigint NOT NULL,
    unique_version uuid NOT NULL,
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    deleted_by uuid,
    CONSTRAINT uk_hashtags_name UNIQUE (name, unique_version)
);

CREATE TABLE p_categories_hashtags (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL,
    hashtag_id uuid NOT NULL,
    match_type varchar(255) NOT NULL,
    status varchar(255) NOT NULL,
    similarity_score double precision NOT NULL,
    unique_version uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    deleted_by uuid,
    CONSTRAINT uk_categories_hashtags_category_hashtag UNIQUE (category_id, hashtag_id, unique_version),
    CONSTRAINT fk_categories_hashtags_category FOREIGN KEY (category_id) REFERENCES p_categories (id),
    CONSTRAINT fk_categories_hashtags_hashtag FOREIGN KEY (hashtag_id) REFERENCES p_hashtags (id)
);

CREATE TABLE p_products (
    id uuid PRIMARY KEY,
    creator_id uuid NOT NULL,
    name varchar(100) NOT NULL,
    content text NOT NULL,
    view_count bigint,
    average_rating numeric(2, 1),
    review_count bigint,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    deleted_by uuid
);

CREATE INDEX idx_products_creator_id ON p_products (creator_id);

CREATE TABLE p_skus (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL,
    name varchar(25) NOT NULL,
    price bigint NOT NULL,
    is_default boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    deleted_by uuid,
    CONSTRAINT ck_skus_price CHECK (price >= 0)
);

CREATE INDEX idx_skus_product_id ON p_skus (product_id);

CREATE TABLE p_stocks (
    id uuid PRIMARY KEY,
    sku_id uuid NOT NULL,
    quantity integer NOT NULL,
    updated_at timestamp with time zone,
    CONSTRAINT uk_stocks_sku_id UNIQUE (sku_id),
    CONSTRAINT ck_stocks_quantity CHECK (quantity >= 0)
);

CREATE TABLE p_images (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL,
    original_key varchar(500),
    processed_key varchar(500),
    status varchar(20) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    created_by uuid NOT NULL,
    processed_at timestamp with time zone,
    deleted_at timestamp with time zone,
    CONSTRAINT uk_images_product_id_image_order UNIQUE (product_id, sort_order)
);

CREATE TABLE p_stock_history (
    id uuid PRIMARY KEY,
    order_id uuid,
    sku_id uuid NOT NULL,
    quantity integer NOT NULL,
    reason varchar(255) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_stock_history_sku_order_reason UNIQUE (sku_id, order_id, reason)
);

CREATE TABLE p_product_daily_views (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL,
    view_count bigint NOT NULL,
    view_date date NOT NULL,
    CONSTRAINT uk_product_daily_view_product_id_view_date UNIQUE (product_id, view_date)
);

CREATE TABLE p_hashtags_products (
    id uuid PRIMARY KEY,
    hashtag_id uuid NOT NULL,
    product_id uuid NOT NULL,
    unique_version uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    deleted_by uuid,
    CONSTRAINT uk_hashtags_products_hashtag_product UNIQUE (hashtag_id, product_id, unique_version),
    CONSTRAINT fk_hashtags_products_hashtag FOREIGN KEY (hashtag_id) REFERENCES p_hashtags (id)
);

CREATE TABLE p_category_outbox_events (
    id uuid PRIMARY KEY,
    type varchar(255) NOT NULL,
    payload text NOT NULL,
    status varchar(255) NOT NULL,
    attempt_count integer NOT NULL,
    error_message varchar(255),
    claimed_at timestamp with time zone,
    published_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE p_leaderboard_snapshots (
    id uuid PRIMARY KEY,
    type integer NOT NULL,
    target_id uuid NOT NULL,
    score double precision NOT NULL,
    ranking bigint NOT NULL,
    date date NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    created_by uuid,
    updated_by uuid,
    deleted_by uuid,
    CONSTRAINT uk_leaderboard_snapshots_type_score_ranking UNIQUE (type, score, ranking, date)
);
