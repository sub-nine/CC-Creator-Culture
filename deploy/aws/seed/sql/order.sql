INSERT INTO p_coupons (
    id, coupon_name, discount_rate, total_quantity, issued_quantity,
    started_at, expired_at, created_at, created_by, updated_at
) VALUES (
    :'coupon_id', '시연 할인 10%', 10, 100, 0,
    TIMESTAMPTZ '2026-01-01 00:00:00+00', TIMESTAMPTZ '2027-12-31 23:59:59+00',
    TIMESTAMPTZ '2026-09-13 00:00:00+00', :'master_user_id',
    TIMESTAMPTZ '2026-09-13 00:00:00+00'
)
ON CONFLICT (id) DO NOTHING;
