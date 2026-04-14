## Example of variable file setting all variables required by this Terraform module
## Replace the placeholders with actual values

# Cloud API Key with EnvironmentAdmin and AccountAdmin roles (like the Platform Manager Service Account in this example)
confluent_cloud_api_key    = "PLATFORM_MANAGER_API_KEY"
confluent_cloud_api_secret = "PLATFORM_MANAGER_API_SECRET"

# Flink Service Account
# App Manager Service Account ID (format: sa-xxxxx)
app_manager_service_account_id = "sa-123456"

# Flink API Credentials
# API key and secret associated with the App Manager Service Account
flink_api_key    = "YOUR_FLINK_API_KEY"
flink_api_secret = "YOUR_FLINK_API_SECRET"

# Cloud Infrastructure
# Example: "AWS", "GCP", or "AZURE"
cloud_provider = "AWS"
# Example: "us-east-1", "us-west-2", etc.
cloud_region   = "us-east-1"

# Confluent Cloud Resources
# Environment ID (format: env-xxxxx)
environment_id = "env-123456"
# Compute Pool ID (format: lfcp-xxxxx)
compute_pool_id = "lfcp-123456"
# Kafka Cluster ID (format: lkc-xxxxx)
kafka_cluster_id = "lkc-123456"