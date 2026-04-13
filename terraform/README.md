# Terraform — Deploy Flink SQL Statements Using a UDF

Simple Terraform module to deploy the Flink SQL statements using one of the UDF examples 
(`concat_with_separator` - it can be easily modified to test other UDF in this repo).

> ⚠️This Terraform module is provided as an example. It is not production-ready and may contain bugs.
> Do not use it as-is in a production environment.



Deploys two Flink SQL statements to Confluent Cloud:

1. `CREATE TABLE extended_products` — destination table with a primary key
2. `INSERT INTO extended_products` — streaming insert using the `concat_with_separator` UDF

The Terraform module assumes that the `concat_with_separator` UDF has been previously registered.

## Prerequisites

- [Terraform](https://developer.hashicorp.com/terraform/install) installed
- A Confluent Cloud Environment
- A Confluent Cloud Flink compute pool and a Kafka cluster in the target environment, both in the same cloud region 
- `concat_with_separator` UDF registered in your Flink environment (see `../docs/ConcatWithSeparator.md`)
- Valid Service Accounts and API keys
    1. A *Platform Manager* Service Account and API Key - Main credentials used by Terraform to communicate with Confluent Cloud
    2. An *App Manager* Service Account and API Key - Credentials used by Terraform to manage Flink resources

> ℹ️ In a real scenario, Environment, Compute Pool, Kafka Cluster, Service Accounts, and API keys are probably created
> at top level by the team managing the platform, and provided to the team responsible for the Flink statements and UDFs. 
> For these examples, scripts to create the Service Accounts and API Keys are provided. 
> Environment, Compute Pool, and Kafka cluster are assumed to be already in place. They can be easily created via Confluent Cloud UI.

### *Platform Manager* Service Account & API Key

This is the main Service Account used by Terraform to interact with Confluent Cloud.

The *Platform Manager* must have at minimum the following roles:

| Role | Scope | Resource | Notes |
|---|---|---|---|
| FlinkAdmin | Environment | — | Allow managing resources in the Flink environment |
| ResourceOwner | Cluster | Topic `*` (prefixed) | Read/write access to all Kafka topics |


Create an API Key with *Cloud Resource Management* scope associated to the *Platform Manager* Service Account.

These Confluent Cloud API Key and Secret must be passed to Terraform as main credential 
(`confluent_cloud_api_key` and `confluent_cloud_api_secret` in the Terraform module).

You can use the script [create-platform-manager.sh](../scripts/README.md#create-platform-manager-sash---create-platform-manager-service-account--api-keys)
to create the Service Account and API Key. 


### *App Manager* Service Account & API Key

The service account used to create and run Flink statements (`app_manager_service_account_id`) must have the following roles:

| Role | Scope | Resource | Notes |
|---|---|---|---|
| `FlinkDeveloper` | Environment | — | Allows creating and managing Flink statements |
| `ResourceOwner` | Kafka cluster | Topic `*` (prefixed) | Read/write access to all Kafka topics |
| `ResourceOwner` | Kafka cluster | TransactionalId `_confluent-flink_*` (prefixed) | Required for Flink's internal transactional producers |
| `DeveloperWrite` | Schema Registry cluster | Subject `*` (prefixed) | Allows registering schemas for new topics |

Create an API Key with *Flink region* scope associated to the *App Manager* Service Account.

The key must be associated with:
* Environment
* Cloud Provider
* Cloud Region

These Flink API Key and Secret must also be passed to Terraform 
(`flink_api_key` and `flink_api_secret` in the Terraform module) along with the corresponding Service Account ID 
(`app_manager_service_account_id`).


You can use the script [create-app-manager.sh](../scripts/README.md#create-app-manager-sash---create-app-manager-service-account--api-keys) 
to create the Service Account and API key.

## Running Terraform

The Terraform module requires several parameters:

| Variable | Description |
|---|---|
| `confluent_cloud_api_key` | Confluent Cloud API key (Cloud API ID) |
| `confluent_cloud_api_secret` | Confluent Cloud API secret |
| `flink_api_key` | Flink API key associated with the App Manager service account |
| `flink_api_secret` | Flink API secret |
| `app_manager_service_account_id` | App Manager service account ID (e.g. `sa-xxxxxx`) |
| `environment_id` | Confluent Cloud environment ID (e.g. `env-xxxxxx`) |
| `cloud_provider` | Cloud provider, lowercase (e.g. `aws`) |
| `cloud_region` | Cloud provider region (e.g. `eu-west-1`) |
| `compute_pool_id` | Flink compute pool ID (e.g. `lfcp-xxxxxx`) |
| `kafka_cluster_id` | Kafka cluster ID used as the Flink default database (e.g. `lkc-xxxxxx`) |

These parameters can be passed via command line on every invocation.
Alternatively, you can create a named `.auto.tfvars` where you specify all parameters.
(you can also pass variables to Terraform via env variables named `TF_VAR_*`, not covered in this example).

> ℹ️ Note that the API keys and secrets must be passed to Terraform explicitly.

### Option 1 — Pass variables on the command line

```bash
terraform apply \
  -var="confluent_cloud_api_key=<cloud-api-key>" \
  -var="confluent_cloud_api_secret=<cloud-api-secret>" \
  -var="flink_api_key=<flink-api-key>" \
  -var="flink_api_secret=<flink-api-secret>" \
  -var="app_manager_service_account_id=sa-xxxxxx" \
  -var="environment_id=env-xxxxxx" \
  -var="cloud_provider=aws" \
  -var="cloud_region=eu-west-1" \
  -var="compute_pool_id=lfcp-xxxxxx" \
  -var="kafka_cluster_id=lkc-xxxxxx"
```

### Option 2 — Use a `*.auto.tfvars` file

Create a file named `terraform.auto.tfvars` (or any name ending in `.auto.tfvars`) in this directory. 
Terraform loads it automatically — no extra flags needed.

```hcl
confluent_cloud_api_key        = "<cloud-api-key>"
confluent_cloud_api_secret     = "<cloud-api-secret>"
flink_api_key                  = "<flink-api-key>"
flink_api_secret               = "<flink-api-secret>"
app_manager_service_account_id = "sa-xxxxxx"
environment_id                 = "env-xxxxxx"
cloud_provider                 = "aws"
cloud_region                   = "eu-west-1"
compute_pool_id                = "lfcp-xxxxxx"
kafka_cluster_id               = "lkc-xxxxxx"
```
> ⚠️ `*.auto.tfvars` files contain secrets. Add them to `.gitignore` to avoid committing credentials.

Terraform will use these values on every invocation.


## Selectively stopping the INSERT INTO statement

To be able to selectively stop the running `INSERT INTO` statement, without destroying, the Terraform module specifies
the parameter `stopped` for that `confluent_flink_statement` resource.
By default, this is set to `false`, meaning that Terraform will start the statement on first deployment and keep it running
on any new apply (unless the statement is modified).

To selectively stop and restart the statement, as required when a new version of the UDF is deployed, we proceed in two steps:
1. Terraform apply setting the variable `statement_stopped`=`true` (passing the parameter `-var="statement_stopped=true"`) - this stops the statement
  ```bash
  terraform apply -var="statement_stopped=true" ...
  ```
2. Another Terraform apply, without setting it - this restarts the statement
  ```bash
  terraform apply  ...
  ```



## Destroying the resources

```bash
terraform destroy
```

Note: destroying the Terraform resources removes the Flink *statements* but does not remove the tables.

To fully clean up, run the following statements before re-applying:

```sql
DROP TABLE IF EXISTS `base_products`;

DROP TABLE IF EXISTS `extended_products`;
```

This can be executed in the Flink SQL shell:

```bash
confluent flink shell \
  --environment <environment-id> \
  --compute-pool <compute-pool-id> \
  --database <kafka-cluster-id>
```

