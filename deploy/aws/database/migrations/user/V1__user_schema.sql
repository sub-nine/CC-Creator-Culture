CREATE TABLE p_users (
    id uuid PRIMARY KEY,
    email varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    nickname varchar(50) NOT NULL,
    phone varchar(20) NOT NULL,
    address varchar(255) NOT NULL,
    slack_id varchar(100),
    role varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES p_users (id),
    CONSTRAINT fk_users_updated_by FOREIGN KEY (updated_by) REFERENCES p_users (id),
    CONSTRAINT fk_users_deleted_by FOREIGN KEY (deleted_by) REFERENCES p_users (id)
);

CREATE INDEX idx_users_role ON p_users (role);

CREATE TABLE p_creators (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    creator_name varchar(100) NOT NULL,
    business_registration_number varchar(20) NOT NULL,
    approval_status varchar(20) NOT NULL,
    approved_at timestamp,
    approved_by uuid,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by uuid,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by uuid NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by uuid,
    CONSTRAINT uk_creators_user UNIQUE (user_id),
    CONSTRAINT uk_creators_business_number UNIQUE (business_registration_number),
    CONSTRAINT uk_creators_name UNIQUE (creator_name),
    CONSTRAINT fk_creators_user FOREIGN KEY (user_id) REFERENCES p_users (id),
    CONSTRAINT fk_creators_approved_by FOREIGN KEY (approved_by) REFERENCES p_users (id),
    CONSTRAINT fk_creators_created_by FOREIGN KEY (created_by) REFERENCES p_users (id),
    CONSTRAINT fk_creators_updated_by FOREIGN KEY (updated_by) REFERENCES p_users (id),
    CONSTRAINT fk_creators_deleted_by FOREIGN KEY (deleted_by) REFERENCES p_users (id)
);

CREATE TABLE notification (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    user_id uuid NOT NULL,
    type varchar(50) NOT NULL,
    title varchar(200) NOT NULL,
    content varchar(500) NOT NULL,
    reference_type varchar(30) NOT NULL,
    reference_id uuid NOT NULL,
    is_read boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    read_at timestamp with time zone
);

CREATE TABLE notification_event (
    event_id uuid PRIMARY KEY,
    event_type varchar(50) NOT NULL,
    source_service varchar(50) NOT NULL,
    reference_type varchar(30) NOT NULL,
    reference_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    retry_count integer NOT NULL,
    error_message varchar(1000),
    received_at timestamp with time zone NOT NULL,
    processed_at timestamp with time zone
);

CREATE TABLE p_notification_slack (
    slack_delivery_id uuid PRIMARY KEY,
    event_id uuid NOT NULL,
    destination varchar(200) NOT NULL,
    slack_message text NOT NULL,
    status varchar(20) NOT NULL,
    retry_count integer NOT NULL,
    attempt_count integer NOT NULL,
    error_code varchar(100),
    error_message varchar(1000),
    requested_at timestamp with time zone NOT NULL,
    next_retry_at timestamp with time zone,
    sent_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL
);
