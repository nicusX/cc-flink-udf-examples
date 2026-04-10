INSERT INTO `extended_products`
SELECT
  product_id,
  concat_with_separator(`name`, `brand`, ' - ') AS long_name,
  concat_with_separator(`name`, `brand`, `vendor`, ' : ') AS extended_name,
  concat_with_separator(`name`, `brand`, `vendor`, `department`, ' : ') AS extra_long_name
FROM `examples`.`marketplace`.`products`;