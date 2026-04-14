## Deploy the SQL statements to test the `concat_with_separator` UDF


terraform {
  required_providers {
    confluent = {
      source  = "confluentinc/confluent"
      version = "2.62.0"
    }
  }
}

locals {
  cloud  = upper(var.cloud_provider)
  region = var.cloud_region
}

provider "confluent" {
  cloud_api_key    = var.confluent_cloud_api_key
  cloud_api_secret = var.confluent_cloud_api_secret
}

data "confluent_organization" "main" {}

data "confluent_environment" "dev" {
  id = var.environment_id
}

data "confluent_service_account" "app_manager" {
  id = var.app_manager_service_account_id
}


data "confluent_flink_region" "main" {
  cloud  = local.cloud
  region = local.region
}

data "confluent_kafka_cluster" "main" {
  # Note that the Flink Database == the Kafka Cluster
  id = var.kafka_cluster_id
  environment {
    id = var.environment_id
  }
}

data "confluent_flink_compute_pool" "main" {
  id = var.compute_pool_id
  environment {
    id = data.confluent_environment.dev.id
  }
}

### Deploy the SQL statements

## Deploy a few statements required by the example

# Create base_products table: the source table
resource "confluent_flink_statement" "create_base_products" {
  organization {
    id = data.confluent_organization.main.id
  }
  environment {
    id = data.confluent_environment.dev.id
  }
  compute_pool {
    id = data.confluent_flink_compute_pool.main.id
  }
  rest_endpoint = data.confluent_flink_region.main.rest_endpoint

  principal {
    id = data.confluent_service_account.app_manager.id
  }

  credentials {
    key    = var.flink_api_key
    secret = var.flink_api_secret
  }

  properties = {
    "sql.current-catalog"  = data.confluent_environment.dev.display_name
    "sql.current-database" = data.confluent_kafka_cluster.main.display_name
  }

  statement = file("./sql/01_create_base_products.sql")
}

# Insert into the source table from the "faker" examples table
resource "confluent_flink_statement" "insert_into_base_products" {
  organization {
    id = data.confluent_organization.main.id
  }
  environment {
    id = data.confluent_environment.dev.id
  }
  compute_pool {
    id = data.confluent_flink_compute_pool.main.id
  }
  rest_endpoint = data.confluent_flink_region.main.rest_endpoint

  principal {
    id = data.confluent_service_account.app_manager.id
  }

  credentials {
    key    = var.flink_api_key
    secret = var.flink_api_secret
  }

  properties = {
    "sql.current-catalog"  = data.confluent_environment.dev.display_name
    "sql.current-database" = data.confluent_kafka_cluster.main.display_name
  }

  statement = file("./sql/02_insert_into_base_products.sql")

  depends_on = [confluent_flink_statement.create_base_products]
}

# Create the destination table
resource "confluent_flink_statement" "create_extended_products" {
  organization {
    id = data.confluent_organization.main.id
  }
  environment {
    id = data.confluent_environment.dev.id
  }
  compute_pool {
    id = data.confluent_flink_compute_pool.main.id
  }
  rest_endpoint = data.confluent_flink_region.main.rest_endpoint

  principal {
    id = data.confluent_service_account.app_manager.id
  }

  credentials {
    key    = var.flink_api_key
    secret = var.flink_api_secret
  }

  properties = {
    "sql.current-catalog"  = data.confluent_environment.dev.display_name
    "sql.current-database" = data.confluent_kafka_cluster.main.display_name
  }

  statement = file("./sql/03_create_extended_products.sql")
}

## Deploy the statement that uses the UDF

# Insert into extended_products from base_products (v1)
resource "confluent_flink_statement" "insert_into_extended_products_v1" {
  organization {
    id = data.confluent_organization.main.id
  }
  environment {
    id = data.confluent_environment.dev.id
  }
  compute_pool {
    id = data.confluent_flink_compute_pool.main.id
  }
  rest_endpoint = data.confluent_flink_region.main.rest_endpoint

  principal {
    id = data.confluent_service_account.app_manager.id
  }

  credentials {
    key    = var.flink_api_key
    secret = var.flink_api_secret
  }

  properties = {
    "sql.current-catalog"  = data.confluent_environment.dev.display_name
    "sql.current-database" = data.confluent_kafka_cluster.main.display_name
  }

  statement = file("./sql/04a_insert_extended_products_v1.sql")

  # When `stopped` is set to `true` Terraform stops the statement without destroying it
  # Note that the default, from variables.tf, is `false`, meaning "start it or keep it running"
  stopped   = var.statement_stopped
  ## COMMENT THE LINE ABOVE AND UNCOMMENT THE LINE BELOW TO DEMONSTRATE UPDATING THE UDF WITH FUNCTION SIGNATURE CHANGES
  # stopped = true

  depends_on = [confluent_flink_statement.create_extended_products, confluent_flink_statement.create_base_products]
}


## UNCOMMENT THE BLOCK BELOW TO DEMONSTRATE UPDATING THE UDF WITH FUNCTION SIGNATURE CHANGES.
## ALSO CHANGE THE stopped ATTRIBUTE IN insert_into_extended_products_v1
##
## When the UDF change requires modifying the SQL statement, the old statement must be replaced with a new one.
## We use carry-over offsets to restart processing form the point where the old statement was stopped. To do this we
## cannot modify the terraform resource in-place, but we need to create a new statement to pass the previous statement
## name as starting position.
#
# resource "confluent_flink_statement" "insert_into_extended_products_v2" {
#   organization {
#     id = data.confluent_organization.main.id
#   }
#   environment {
#     id = data.confluent_environment.dev.id
#   }
#   compute_pool {
#     id = data.confluent_flink_compute_pool.main.id
#   }
#   rest_endpoint = data.confluent_flink_region.main.rest_endpoint
#
#   principal {
#     id = data.confluent_service_account.app_manager.id
#   }
#
#   credentials {
#     key    = var.flink_api_key
#     secret = var.flink_api_secret
#   }
#
#   properties = {
#     "sql.current-catalog"            = data.confluent_environment.dev.display_name
#     "sql.current-database"           = data.confluent_kafka_cluster.main.display_name
#     # The following property is used to enable carry-over offsets.
#     # It should be removed, stopping and starting the statement unchanged, after you used it once
#     "sql.tables.initial-offset-from" = confluent_flink_statement.insert_into_extended_products_v1.statement_name
#   }
#
#   statement = file("./sql/04b_insert_extended_products_v2.sql")
#
#   stopped   = var.statement_stopped
#
#   depends_on = [confluent_flink_statement.create_extended_products, confluent_flink_statement.create_base_products]
# }
