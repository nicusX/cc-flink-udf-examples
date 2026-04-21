-- Destination table
CREATE TABLE `extended_products` (
  `product_id`      STRING NOT NULL,
  `name`            STRING,
  `extended_name`   STRING
) WITH (
    -- These options are just to simplify testing. They are not related to the UDF lifecycle
    'kafka.consumer.isolation-level'='read-uncommitted'
);