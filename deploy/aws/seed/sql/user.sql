INSERT INTO private.p_users (
    id, email, password, nickname, phone, address, slack_id, role,
    created_at, created_by, updated_at, updated_by
) VALUES
    (
        :'master_user_id', 'master@aws-fixture.example', :'password_hash',
        'fixture-master', '01011111111', '서울시 시연구 1', NULL, 'MASTER',
        TIMESTAMPTZ '2026-09-13 00:00:00+00', NULL,
        TIMESTAMPTZ '2026-09-13 00:00:00+00', :'master_user_id'
    ),
    (
        :'manager_user_id', 'manager@aws-fixture.example', :'password_hash',
        'fixture-manager', '01022222222', '서울시 시연구 2', NULL, 'MANAGER',
        TIMESTAMPTZ '2026-09-13 00:00:00+00', :'master_user_id',
        TIMESTAMPTZ '2026-09-13 00:00:00+00', :'master_user_id'
    ),
    (
        :'creator_user_id', 'creator@aws-fixture.example', :'password_hash',
        'fixture-creator', '01033333333', '서울시 시연구 3', NULL, 'CREATOR',
        TIMESTAMPTZ '2026-09-13 00:00:00+00', NULL,
        TIMESTAMPTZ '2026-09-13 00:00:00+00', :'creator_user_id'
    ),
    (
        :'customer_user_id', 'customer@aws-fixture.example', :'password_hash',
        'fixture-customer', '01044444444', '서울시 시연구 4', NULL, 'CUSTOMER',
        TIMESTAMPTZ '2026-09-13 00:00:00+00', NULL,
        TIMESTAMPTZ '2026-09-13 00:00:00+00', :'customer_user_id'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO private.p_creators (
    id, user_id, creator_name, business_registration_number, approval_status,
    approved_at, approved_by, created_at, created_by, updated_at, updated_by
) VALUES (
    :'creator_id', :'creator_user_id', 'AWS Fixture Shop', '1234567890', 'APPROVED',
    TIMESTAMP '2026-09-13 00:05:00', :'master_user_id',
    TIMESTAMPTZ '2026-09-13 00:00:00+00', :'master_user_id',
    TIMESTAMPTZ '2026-09-13 00:05:00+00', :'master_user_id'
)
ON CONFLICT (id) DO NOTHING;
