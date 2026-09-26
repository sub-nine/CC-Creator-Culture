CREATE
EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_products_name_trgm
    ON p_products
    USING GIN (LOWER (name) gin_trgm_ops)
    WHERE deleted_at IS NULL;