-- Copy data from Faker example table into the source table
INSERT INTO `base_products`
SELECT
    product_id,
    name,
    brand,
    vendor,
    department
FROM `examples`.`marketplace`.`products` /*+ OPTIONS('changelog.mode' = 'append') */;
