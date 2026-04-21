-- New version of statement using the UDF with a different number of parameters
INSERT INTO `extended_products`
SELECT
    product_id,
    `name`,
    concat_with_separator(`name`, `brand`, `vendor`, `department`, ' + ') AS extended_name
FROM `base_products`%{ if initial_offsets != "" } /*+ OPTIONS('scan.startup.mode' = 'specific-offsets', 'scan.startup.specific-offsets' = '${initial_offsets}') */%{ endif };