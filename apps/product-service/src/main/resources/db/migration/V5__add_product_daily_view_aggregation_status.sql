ALTER TABLE p_product_daily_views
    ADD COLUMN aggregated BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_product_daily_views_pending
    ON p_product_daily_views (view_date) WHERE aggregated = false;
