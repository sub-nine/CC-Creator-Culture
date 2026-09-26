CREATE TABLE p_image_uploads (
    id uuid PRIMARY KEY,
    object_key varchar(500) NOT NULL,
    content_type varchar(50) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    created_by uuid,
    CONSTRAINT uk_image_uploads_object_key UNIQUE (object_key)
);

CREATE INDEX idx_image_uploads_created_at
    ON p_image_uploads (created_at);
