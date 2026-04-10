# User Defined Functions Deployment Lifecycle

In this document we examine the deployment lifecycle of a user defined function and the SQL statements using it.

> Note: the lifecycle is identical for scalar UDF, UDTF, and PTF.

The lifecycle uses shell [scripts](./scripts) and a simple [Terraform module](./terraform) provided as demonstration.
Check out the detailed documentation in the subfolders.


## Lifecycle Summary

### Prerequisites

#### Local prerequisites

* JDK 17+
* Maven
* Terraform
* Confluent CLI

#### Confluent Cloud Prerequisites

Top level Confluent Cloud resources:
* Environment
* A Kafka Cluster, in the Environment
* Flink Compute Pool, in the same Environment

The creation of these resources is out of scope for this document.

> ⚠️Kafka Cluster and Flink Compute Pool must be in the same Cloud and Region

Service Accounts and API keys:
* Service Account and API Key with Cloud Resource Management scope (see [Terraform - *Platform Manager* Service Account & API Key](./terraform/README.md#platform-manager-service-account--api-key))
* Service Account and API Key with Flink region scope (see [Terraform - *App Manager* Service Account & API Key](./terraform/README.md#app-manager-service-account--api-key))


> ℹ️ In a real scenario, Environment, Compute Pool, Kafka Cluster, Service Accounts, and API keys are probably created
> at top level by the team managing the platform, and provided to the team responsible for the Flink statements and UDFs.
> For these examples, scripts to create the Service Accounts and API Keys are provided.
> Environment, Compute Pool, and Kafka cluster are assumed to be already in place. They can be easily created via Confluent Cloud UI.

> ⚠️Note that the authentication used by Confluent CLI, used by the scripts, and by Terraform use different mechanisms.
> The scripts require a valid authenticated session with the CLI, via email/password or SSO.
> Conversely, Terraform uses API keys.


### First deployment

1. Build artifact v1 - maven
2. Upload artifact v1 - script (`upload-artifact.sh`)
3. Register function(s) - script (`register-function.sh`)
4. Create SQL statements which use the UDF - Terraform


### UDF update - no function signature change

1. Build version v2
2. Upload artifact v2 - script (`upload-artifact.sh`)
3. Un-register function(s) - script (`drop-function.sh`)
4. Register function(s) using artifact v2 - script (`register-function.sh`)
5. Stop the SQL statement - Terraform
6. Restart the SQL statement - Terraform
7. (test)
8. (optional) Delete artifact v1 - script (`delete-artifact.sh`)

#### Rollback to v1

1. Un-register function(s) - script (`drop-function.sh`)
2. Register function(s) using artifact v1 - script (`register-function.sh`)
3. Stop/restart SQL statement - Terraform

#### UDF update - signature change

Identical to no-signature change, but since it requires changes in the SQL statements, it requires creating a new statement and 
using carry-over offsets to continue consuming from sources.

---

## Step-by-step Lifecycle Example

To simplify the number of parameters passed to the scripts and to Terraform, we assume you have set the following parameters via environment
variables (all the scripts in this repository accept parameters via env variables; we pass them to Terraform via command line
getting the values from the environment):

| Env variable | Used by | Description |
| --- | --- | --- |
| `CONFLUENT_FLINK_ENVIRONMENT_ID` | Scripts, Terraform | Confluent Cloud environment ID (e.g. `env-abc123`) |
| `CONFLUENT_FLINK_CLOUD_PROVIDER` | Scripts, Terraform | Cloud provider (e.g. `aws`) |
| `CONFLUENT_FLINK_CLOUD_REGION` | Scripts, Terraform | Cloud region (e.g. `eu-west-1`) |
| `CONFLUENT_FLINK_COMPUTE_POOL_ID` | Scripts, Terraform | Flink compute pool ID (e.g. `lfcp-abc123`) |
| `CONFLUENT_FLINK_DATABASE` | Scripts | Kafka cluster used as the default database |
| `CONFLUENT_FLINK_CATALOG` | Scripts | Flink catalog name |
| `CONFLUENT_KAFKA_CLUSTER_ID` | Terraform | Kafka cluster ID (e.g. `lkc-abc123`) |
| `CONFLUENT_CLOUD_API_KEY` | Terraform | Cloud API key (Platform Manager service account) |
| `CONFLUENT_CLOUD_API_SECRET` | Terraform | Cloud API secret |
| `CONFLUENT_FLINK_API_KEY` | Terraform | Flink API key (App Manager service account) |
| `CONFLUENT_FLINK_API_SECRET` | Terraform | Flink API secret |
| `CONFLUENT_APP_MANAGER_SERVICE_ACCOUNT_ID` | Terraform | App Manager service account ID (e.g. `sa-abc123`) |

### Terraform INIT

The following steps assume you have already initialized Terraform with `terraform init`.

### First deployment

1. Build artifact (1.0)
   * POM file: `version` = `1.0` 
   * Build artifact: `mvn package` - creates `udf-examples-1.0.jar`
2. Upload artifact (1.0)
   ```shell
   scripts/upload-artifact.sh --path <path-to-jar> [--description '<description>'] [--quiet]
   ```
   Example:
   ```shell
   ARTIFACT_ID=$(scripts/upload-artifact.sh --path target/udf-examples-1.0.jar --description "UDF examples v1.0" --quiet)
   ```
   With `--quiet`, the script prints only the artifact ID (e.g. `cfa-abc123`) to stdout, which can be captured for subsequent steps.
3. Register the function
   ```shell
   scripts/register-function.sh --function <function-name> \
     --class <fully-qualified-class-name> \
     --artifact-id <artifact-id> [--quiet]
   ```
   Example (using the artifact ID captured in the previous step):
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "${ARTIFACT_ID}"
   ```
   * You can verify that the function was actually registered executing `SHOW USER FUNCTIONS` in an interactive Flink SQL Workspace selecting the correct Catalog (the Environment) and Database (Kafka cluster)
4. Create SQL statements which use the UDF - using Terraform
   1. Plan
      ```shell
      terraform -chdir=terraform plan -out=plan.tfplan \
        -var="confluent_cloud_api_key=${CONFLUENT_CLOUD_API_KEY}" \
        -var="confluent_cloud_api_secret=${CONFLUENT_CLOUD_API_SECRET}" \
        -var="flink_api_key=${CONFLUENT_FLINK_API_KEY}" \
        -var="flink_api_secret=${CONFLUENT_FLINK_API_SECRET}" \
        -var="app_manager_service_account_id=${CONFLUENT_APP_MANAGER_SERVICE_ACCOUNT_ID}" \
        -var="environment_id=${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
        -var="cloud_provider=${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
        -var="cloud_region=${CONFLUENT_FLINK_CLOUD_REGION}" \
        -var="compute_pool_id=${CONFLUENT_FLINK_COMPUTE_POOL_ID}" \
        -var="kafka_cluster_id=${CONFLUENT_KAFKA_CLUSTER_ID}"
      ```
   2. Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * You can observe data written into the `extended_products` table by the running statement executing `SELECT * FROM extended_products` in an interactive Flink SQL Workspace selecting the correct Catalog (the Environment) and Database (Kafka cluster)



### UDF update - no function signature change

We do not really change the code, but we edit the `pom.xml` bumping the version to `1.1` to pretend an update.

1. Build new version (1.1)
   1. Edit the `pom.xml` changing `version` to `1.1`
   2. Build artifact: `mvn clean package` - creates `udf-examples-1.1.jar`
2. Upload new artifact (1.1)
   ```shell
   ARTIFACT_ID=$(scripts/upload-artifact.sh --path target/udf-examples-1.1.jar --description "UDF examples v1.1" --quiet)
   ```
3. Un-register the function
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
   * Note that the running statement keeps running with the old function.
     If you query `extended_products` you can see data being processed. And you can see the statement running from the Console, in the Compute Pool.
     But if you try to describe the function (`DESCRIBE FUNCTION concat_with_separator`) you can see that it is gone.
4. Register the function using the new artifact, just uploaded
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "${ARTIFACT_ID}"
   ```
   * If you run `DESCRIBE FUNCTION concat_with_separator` you can see the function is there, and uses the new artifact (`plugin id`)
5. Stop the SQL statement
   1. Plan (note the additional parameter `-var="statement_stopped=true"` )
      ```shell
      terraform -chdir=terraform plan -var="statement_stopped=true" -out=plan.tfplan \
        -var="confluent_cloud_api_key=${CONFLUENT_CLOUD_API_KEY}" \
        -var="confluent_cloud_api_secret=${CONFLUENT_CLOUD_API_SECRET}" \
        -var="flink_api_key=${CONFLUENT_FLINK_API_KEY}" \
        -var="flink_api_secret=${CONFLUENT_FLINK_API_SECRET}" \
        -var="app_manager_service_account_id=${CONFLUENT_APP_MANAGER_SERVICE_ACCOUNT_ID}" \
        -var="environment_id=${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
        -var="cloud_provider=${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
        -var="cloud_region=${CONFLUENT_FLINK_CLOUD_REGION}" \
        -var="compute_pool_id=${CONFLUENT_FLINK_COMPUTE_POOL_ID}" \
        -var="kafka_cluster_id=${CONFLUENT_KAFKA_CLUSTER_ID}"
      ```
   2. Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * In the Console, Compute Pool, you can see the statement is now "Stopped"
6. Restart the SQL statement
   1. Plan (no additional parameter)
      ```shell
      terraform -chdir=terraform plan -out=plan.tfplan \
        -var="confluent_cloud_api_key=${CONFLUENT_CLOUD_API_KEY}" \
        -var="confluent_cloud_api_secret=${CONFLUENT_CLOUD_API_SECRET}" \
        -var="flink_api_key=${CONFLUENT_FLINK_API_KEY}" \
        -var="flink_api_secret=${CONFLUENT_FLINK_API_SECRET}" \
        -var="app_manager_service_account_id=${CONFLUENT_APP_MANAGER_SERVICE_ACCOUNT_ID}" \
        -var="environment_id=${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
        -var="cloud_provider=${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
        -var="cloud_region=${CONFLUENT_FLINK_CLOUD_REGION}" \
        -var="compute_pool_id=${CONFLUENT_FLINK_COMPUTE_POOL_ID}" \
        -var="kafka_cluster_id=${CONFLUENT_KAFKA_CLUSTER_ID}"
      ```
   2. Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * In the Console, Compute Pool, you can see the statement is now "Running"
7. (Run smoke tests) - For the sake of this example, you can see whether `SELECT $rowtime, * FROM extended_products` keeps emitting records
8. (Optionally) delete the old artifact
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.0
   ```



--- 
## Cleanup

To eliminate all resources created by this example:

1. Terraform destroy
   ```shell
   terraform -chdir=terraform destroy \
     -var="confluent_cloud_api_key=${CONFLUENT_CLOUD_API_KEY}" \
     -var="confluent_cloud_api_secret=${CONFLUENT_CLOUD_API_SECRET}" \
     -var="flink_api_key=${CONFLUENT_FLINK_API_KEY}" \
     -var="flink_api_secret=${CONFLUENT_FLINK_API_SECRET}" \
     -var="app_manager_service_account_id=${CONFLUENT_APP_MANAGER_SERVICE_ACCOUNT_ID}" \
     -var="environment_id=${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
     -var="cloud_provider=${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
     -var="cloud_region=${CONFLUENT_FLINK_CLOUD_REGION}" \
     -var="compute_pool_id=${CONFLUENT_FLINK_COMPUTE_POOL_ID}" \
     -var="kafka_cluster_id=${CONFLUENT_KAFKA_CLUSTER_ID}"
   ```
2. Drop function
   ```shell
   scripts/drop-function.sh --function <function-name> [--quiet]
   ```
   Example:
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
3. Delete Artifacts
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.0
   scripts/delete-artifact.sh --artifact-name udf-examples-1.1   
   ```
