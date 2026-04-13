-- The actual statement using the UDF
INSERT INTO `extended_products`
SELECT
  product_id,
  `name`,
  concat_with_separator(`name`, `brand`, `vendor`, ' : ') AS extended_name
FROM `base_products`;