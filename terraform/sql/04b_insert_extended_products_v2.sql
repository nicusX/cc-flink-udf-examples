-- New version of statement using the UDF with a different number of parameters
INSERT INTO `extended_products`
SELECT
    product_id,
    `name`,
    concat_with_separator(`name`, `brand`, `vendor`, `brand`, ' + ') AS extended_name
FROM `base_products`;