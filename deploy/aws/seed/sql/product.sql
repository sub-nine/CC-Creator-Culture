INSERT INTO p_categories (
    id, merged_category_id, name, description, status, unique_version,
    created_at, updated_at, created_by, updated_by
) VALUES (
    :'category_id', NULL, '피규어', '시연용 카테고리', 'ACTIVE', :'unique_version',
    TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00',
    :'master_user_id', :'master_user_id'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO p_hashtags (
    id, name, usage_count, unique_version, version,
    created_at, updated_at, created_by, updated_by
) VALUES (
    :'hashtag_id', '피규어', 1, :'unique_version', 0,
    TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00',
    :'master_user_id', :'master_user_id'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO p_categories_hashtags (
    id, category_id, hashtag_id, match_type, status, similarity_score, unique_version,
    created_at, updated_at, created_by, updated_by
) VALUES (
    :'category_hashtag_id', :'category_id', :'hashtag_id', 'MANUAL', 'MERGED', 1.0, :'unique_version',
    TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00',
    :'master_user_id', :'master_user_id'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO p_products (
    id, creator_id, name, content, view_count, average_rating, review_count, status,
    created_at, updated_at, created_by, updated_by
) VALUES (
    :'product_id', :'creator_user_id', 'AWS 시연 피규어', '시연용 상품', 0, NULL, 0, 'ACTIVE',
    TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00',
    :'creator_user_id', :'creator_user_id'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO p_skus (
    id, product_id, name, price, is_default,
    created_at, updated_at, created_by, updated_by
) VALUES (
    :'sku_id', :'product_id', '기본', 15000, TRUE,
    TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00',
    :'creator_user_id', :'creator_user_id'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO p_stocks (id, sku_id, quantity, updated_at)
VALUES (:'stock_id', :'sku_id', 50, TIMESTAMPTZ '2026-09-13 00:00:00+00')
ON CONFLICT (id) DO NOTHING;

INSERT INTO p_hashtags_products (
    id, hashtag_id, product_id, unique_version,
    created_at, updated_at, created_by, updated_by
) VALUES (
    :'hashtag_product_id', :'hashtag_id', :'product_id', :'unique_version',
    TIMESTAMPTZ '2026-09-13 00:00:00+00', TIMESTAMPTZ '2026-09-13 00:00:00+00',
    :'creator_user_id', :'creator_user_id'
)
ON CONFLICT (id) DO NOTHING;
