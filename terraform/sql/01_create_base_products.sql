-- Source table
CREATE TABLE `base_products` (
    `product_id` STRING,
    `name` STRING,
    `brand` STRING,
    `vendor` STRING,
    `department` STRING
) WITH (
    -- These options are just to simplify testing. They are not related to the UDF lifecycle
    'scan.startup.mode' = 'latest-offset',
    'kafka.consumer.isolation-level'='read-uncommitted'
);