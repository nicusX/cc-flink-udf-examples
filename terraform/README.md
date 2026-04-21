# Terraform — Deploy Flink SQL Statements Using a UDF

Simple Terraform module to deploy the Flink SQL statements using one of the UDF examples 
(`concat_with_separator` - it can be easily modified to test other UDFs in this repo).

> ⚠️ This Terraform module is provided as an example. It is not production-ready and may contain bugs.
> Do not use it as-is in a production environment.


Deploys the following Flink SQL statements to Confluent Cloud:

1. `CREATE TABLE base_products` — source table, populated from the Confluent examples
2. `INSERT INTO base_products` — copies data from the `examples.marketplace.products` faker table
3. `CREATE TABLE extended_products` — destination table (append-only, no primary key)
4. `INSERT INTO extended_products` (v1) — streaming insert using the `concat_with_separator` UDF
5. `INSERT INTO extended_products` (v2, commented out) — alternative version for demonstrating carry-over offsets

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
| `FlinkAdmin` | Environment | — | Allows managing resources in the Flink environment |
| `ResourceOwne`r | Cluster | Topic `*` (prefixed) | Read/write access to all Kafka topics |


Create an API Key with *Cloud Resource Management* scope associated with the *Platform Manager* Service Account.

These Confluent Cloud API Key and Secret must be passed to Terraform as the main credentials 
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

Create an API Key with *Flink region* scope associated with the *App Manager* Service Account.

The key must be associated with:
* Environment
* Cloud Provider
* Cloud Region

These Flink API Key and Secret must also be passed to Terraform 
(`flink_api_key` and `flink_api_secret` in the Terraform module) along with the corresponding Service Account ID 
(`app_manager_service_account_id`).


You can use the script [create-app-manager.sh](../scripts/README.md#create-app-manager-sash---create-app-manager-service-account--api-keys) 
to create the Service Account and API key.


## Passing variables to Terraform

The Terraform module requires several parameters:

| Variable | Description                                                             |
|---|-------------------------------------------------------------------------|
| `confluent_cloud_api_key` | Confluent Cloud API key (Cloud API ID)                                  |
| `confluent_cloud_api_secret` | Confluent Cloud API secret                                              |
| `flink_api_key` | Flink API key associated with the App Manager service account           |
| `flink_api_secret` | Flink API secret                                                        |
| `app_manager_service_account_id` | App Manager service account ID (e.g. `sa-xxxxxx`)                       |
| `environment_id` | Confluent Cloud environment ID (e.g. `env-xxxxxx`)                      |
| `cloud_provider` | Cloud provider, lowercase (e.g. `AWS` - uppercase)                      |
| `cloud_region` | Cloud provider region (e.g. `eu-west-1`)                                |
| `compute_pool_id` | Flink compute pool ID (e.g. `lfcp-xxxxxx`)                              |
| `kafka_cluster_id` | Kafka cluster ID used as the Flink default database (e.g. `lkc-xxxxxx`) |


### Option 1 (preferred) - Pass the tfvars file on every terraform invocation

```shell
terraform plan -var-file=example.tfvars
```

### Option 2 - tfvars file + env variables

If you do not want to put any secret in the tfvars file, you can pass those variables to Terraform using env variables
named `TF_VAR_variable_name`.

Remove the variables `confluent_cloud_api_key`, `confluent_cloud_api_secret`, `flink_api_key`, and `flink_api_secret`
from `example.tfvars`, and set the following env variables:

```shell
export TF_VAR_confluent_cloud_api_key="<platform-manager-api-key>"
export TF_VAR_confluent_cloud_api_secret="<platform-manager-api-secret>"
export TF_VAR_flink_api_key="<app-manager-flink-api-key>"
export TF_VAR_flink_api_secret="<app-manager-flink-api-secret>"
```

Then, when invoking `terraform`, pass the tfvars file with the other variables.

```shell
terraform plan -var-file=example.tfvars
```


### Option 3 (verbose) — Pass variables on the command line

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

---

## Selectively stopping the INSERT INTO statement

To be able to selectively stop the running `INSERT INTO` statement, without destroying it, the Terraform module specifies
the parameter `stopped` for that `confluent_flink_statement` resource.
By default, this is set to `false`, meaning that Terraform will start the statement on first deployment and keep it running
on any new apply (unless the statement is modified).

### UDF update without SQL statement changes

To selectively stop and restart the statement, as required when a new version of the UDF is deployed, we proceed in two steps:
1. Terraform apply setting the variable `stop_statement_v1`=`true` (passing the parameter `-var="stop_statement_v1=true"`) - this stops the v1 statement
  ```bash
  terraform apply -var="stop_statement_v1=true" ...
  ```
2. Another Terraform apply, without setting it - this restarts the statement
  ```bash
  terraform apply ...
  ```

The v2 statement can be controlled independently with `stop_statement_v2`.

### UDF update with SQL statement changes (carry-over offsets)

When a UDF change requires modifying the SQL statement (e.g. a function signature change), the old statement must be
replaced with a new one. The module includes a commented-out v2 resource that uses
[carry-over offsets](https://docs.confluent.io/cloud/current/flink/operate-and-deploy/carry-over-offsets.html)
to resume processing from where the old statement stopped.

See [lifecycle.md](../lifecycle.md#scenario-4-udf-update---change-to-the-sql-statement-required-1) for the detailed step-by-step process.



## Destroying the resources

```bash
terraform destroy
```

Note: destroying the Terraform resources removes the Flink *statements* but does not remove the tables.

To fully clean up, run the following statements before re-applying:

```sql
DROP TABLE `base_products`;

DROP TABLE `extended_products`;
```

This can be executed in a SQL Workspace or using the [`drop-table.sh`](../scripts/README.md#drop-tablesh--drop-a-flink-table) script:

```shell
scripts/drop-table.sh --table base_products
scripts/drop-table.sh --table extended_products
```

