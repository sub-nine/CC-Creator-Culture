ALTER TABLE p_products
    RENAME COLUMN average_rating TO rating_sum;

ALTER TABLE p_products
    ALTER COLUMN rating_sum TYPE bigint
    USING rating_sum::bigint;
