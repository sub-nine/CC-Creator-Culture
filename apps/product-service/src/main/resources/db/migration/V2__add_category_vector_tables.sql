CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE p_categories_vector (
    category_id uuid PRIMARY KEY REFERENCES p_categories (id),
    embedding vector(768) NOT NULL,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE p_hashtags_vector (
    hashtag_id uuid PRIMARY KEY REFERENCES p_hashtags (id),
    embedding vector(768) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
