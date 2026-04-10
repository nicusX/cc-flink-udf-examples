variable "confluent_cloud_api_key" {
  description = "Confluent Cloud API Key (also referred as Cloud API ID) with EnvironmentAdmin and AccountAdmin roles provided by Kafka Ops team"
  type        = string
  sensitive   = true
}

variable "confluent_cloud_api_secret" {
  description = "Confluent Cloud API Secret"
  type        = string
  sensitive   = true
}

variable "app_manager_service_account_id" {
  description = "App Manager Service Account used to create and run the statements"
  type        = string
}

variable "flink_api_key" {
  description = "Flink API key - associated with the App Manager Service Account"
  type        = string
  sensitive   = true
}

variable "flink_api_secret" {
  description = "Flink API secret - corresponding to the Flink API key"
  type        = string
  sensitive   = true
}

variable "cloud_provider" {
  description = "Cloud provider"
  type        = string
}

variable "cloud_region" {
  description = "Cloud Provider region"
  type        = string
}

variable "environment_id" {
  description = "Flink Environment ID"
  type        = string
}

variable "compute_pool_id" {
  description = "Flink Compute Pool ID"
  type        = string
}

variable "kafka_cluster_id" {
  description = "Kafka Cluster ID used as the Flink default database"
  type        = string
}

variable "statement_stopped" {
  description = "Whether the INSERT INTO statement should be stopped"
  type        = bool
  default     = false
}