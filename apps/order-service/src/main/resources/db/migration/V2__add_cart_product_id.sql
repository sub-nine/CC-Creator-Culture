ALTER TABLE p_carts
    ADD COLUMN product_id uuid NOT NULL;

CREATE INDEX idx_carts_product_id ON p_carts (product_id);
