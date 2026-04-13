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

##### Confluent Cloud resources
* Environment
* A Kafka Cluster, in the Environment
* Flink Compute Pool, in the same Environment

The creation of these resources is out of scope for this document.

> ⚠️Kafka Cluster and Flink Compute Pool must be in the same Cloud and Region

##### Service Accounts and API keys

* Service Account and API Key with Cloud Resource Management scope (see [Terraform - *Platform Manager* Service Account & API Key](./terraform/README.md#platform-manager-service-account--api-key))
* Service Account and API Key with Flink region scope (see [Terraform - *App Manager* Service Account & API Key](./terraform/README.md#app-manager-service-account--api-key))


> ℹ️ In a real scenario, Environment, Compute Pool, Kafka Cluster, Service Accounts, and API keys are probably created
> at top level by the team managing the platform, and provided to the team responsible for the Flink statements and UDFs.
> For these examples, scripts to create the Service Accounts and API Keys are provided.
> Environment, Compute Pool, and Kafka cluster are assumed to be already in place. They can be easily created via Confluent Cloud UI.

> ⚠️Note that the authentication used by Confluent CLI, used by the scripts, and by Terraform use different mechanisms.
> The scripts require a valid authenticated session with the CLI, via email/password or SSO.
> Conversely, Terraform uses API keys.

---

### First deployment

1. Build artifact v1 - maven
2. Upload artifact v1 - script (`upload-artifact.sh`)
3. Register function(s) - script (`register-function.sh`)
4. Create SQL statements which use the UDF - Terraform


### UDF update - no change to the SQL statement required

Let's test deploying a change which does not require changing the SQL statement.
We still need to stop and restart the statement, after having deployed and registered the new function version, to ensure
the new implementation is used.

1. Build function version v2
2. Upload artifact v2 - script (`upload-artifact.sh`)
3. Un-register function(s) - script (`drop-function.sh`)
4. Register function(s) using artifact v2 (the running statement is still using the old version of the function) - script (`register-function.sh`)
5. Stop the SQL statement - Terraform
6. Restart the SQL statement (will use the new version of the function) - Terraform
7. (test, rollback to v1 if something goes wrong)
8. (optional) Delete artifact v1 - script (`delete-artifact.sh`)

### Rollback to v1

1. Un-register function(s) - script (`drop-function.sh`)
2. Register function(s) using artifact v1 - script (`register-function.sh`)
3. Stop the SQL statement - Terraform
4. Restart the SQL statement - Terraform
5. (optional) Delete artifact v2 (the one not working correctly) - script (`delete-artifact.sh`)


### UDF update - change to the SQL statement required

If the UDF change requires modifying the SQL statement too, for example for a change in the function signature, you need
to replace the SQL statement v1 with a new v2 statement. 
Using [Carry-over Offsets](https://docs.confluent.io/cloud/current/flink/operate-and-deploy/carry-over-offsets.html) allows
restarting the v2 statement from the point the v1 statement is stopped.

(the initial steps are identical to the previous update)
1. Build function version v2
2. Upload artifact v2 - script (`upload-artifact.sh`)
3. Un-register function(s) - script (`drop-function.sh`)
4. Register function(s) using artifact v2 (the running statement is still using the old version of the function) - script (`register-function.sh`)

(the process changes here)
5. Stop the statement v1 and create a statement v2 which uses carry-over offsets from the old statement - Terraform
6. (test, rollback to v1 if something goes wrong)
7. Stop the v2 - Terraform
8. Restart v2 without carry-over offsets and delete statement v1 - Terraform


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
   1. Terraform Plan
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
   2. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * You can observe data written into the `extended_products` table by the running statement executing `SELECT * FROM extended_products` in an interactive Flink SQL Workspace selecting the correct Catalog (the Environment) and Database (Kafka cluster)



### UDF update - no change to the SQL statement required

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
   1. Terraform Plan (note the additional parameter `-var="statement_stopped=true"` )
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
   2. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * In the Console, Compute Pool, you can see the statement is now "Stopped"
6. Restart the SQL statement
   1. Terraform Plan (no additional parameter)
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
   2. Terraform Apply - restart the statement, using the new function version
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * In the Console, Compute Pool, you can see the statement is now "Running"
7. (Run tests) - For the sake of this example, you can see whether `SELECT $rowtime, * FROM extended_products` keeps emitting records
8. (Optionally) delete the old artifact
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.0
   ```


### Rollback to a previous UDF version

Assuming your tests failed after deploying the new UDF version, and you haven't yet deleted the old artifact, you can rollback to the
previous version of the function.


1. Un-register function
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
2. Register function using the previous artifact version
   * In a real automation scenario the old artifact-id must be saved somewhere and passed to the script.
     For the sake of this example, we can run `scripts/list-artifacts.sh` to show all artifact uploaded in the specific Environment and check the ID
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "<previous-artifact-id>"
   ```   
3. Stop & Restart the SQL statement - Use Terraform, same as in the previous case
4. (optional) Delete the artifact 1.1, which wasn't working properly
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.1
   ```

### UDF update - change to the SQL statement required

We use [Carry-over Offsets](https://docs.confluent.io/cloud/current/flink/operate-and-deploy/carry-over-offsets.html) to
ensure the new statement version starts from the point in the source where the old statement is stopped.

> ℹ️ We cannot rely on Terraform to update the statement, because Carry-over offsets require creating a new statement
> and passing the name of the old statement as a start point (using the `sql.tables.initial-offset-from` property).

> ⚠️ Carry-over offsets have limitations. 
> Currently they are only supported for stateless statements.
> Note that statements writing to an upsert table - like a table with a primary key - require stateful internal operators.
> Statements emitting upsert changelog cannot use carry-over offsets.


For this test we do not need to modify the UDF. The `concat_with_separator` UDF implements multiple signatures with different
number of parameters. We just modify the statement passing a different number of parameters to the function.
To simulate a major change in the UDF we bump version in the POM to `2.0`.


The initial steps are identical to the previous update. 
See steps [UDF update - no change to the SQL statement required](#udf-update---no-change-to-the-sql-statement-required-1)
for the details:
1. Build new version (2.0) - bump the version to `2.0` and rebuild.
2. Upload new artifact (2.0)
3. Un-register the function
4. Register the function using the new artifact, just uploaded

The following steps change:
5. Stop statement v1 and create statement v2 using carry-over offsets.
   Make the following changes to the Terraform code in [main.tf](terraform/main.tf)
   1. In the old `confluent_flink_statement.insert_into_extended_products_v1` resource, force the property `stopped = true`.
      This ensures the old statement is stopped, but not deleted.
   2. Add a new `confluent_flink_statement.insert_into_extended_products_v2` (uncomment the code in this example).
      Set the property `"sql.tables.initial-offset-from"` to the name of the previous statement 
      (`confluent_flink_statement.insert_into_extended_products_v1.statement_name`)
   3. Terraform Plan
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
   4. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
6. (Run tests) - For the sake of this example, you can see whether `SELECT $rowtime, * FROM extended_products` keeps emitting records
7. Stop the statement v2 (statement v1 is already stopped)
   1. Terraform Plan (note the additional parameter `-var="statement_stopped=true"` )
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
   2. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
8. Restart v2 without carry-over offsets and delete statement v1. Modify the Terraform code in [main.tf](terraform/main.tf):
   1. Remove (comment out) the resource of the v1 statement
      (`confluent_flink_statement.insert_into_extended_products_v1`)
   2. Remove (comment out) the property `"sql.tables.initial-offset-from" = confluent_flink_statement.insert_into_extended_products_v1.statement_name`
      in the v2 statement  (`confluent_flink_statement.insert_into_extended_products_v2`)
   3. Terraform Plan
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
   4. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```


> ℹ️ If your statement does not support carry-over offsets, follow the same process creating the v2 statement,
> but set the starting position using the `scan.startup.mode` property, depending on your use case.
> This process may generate duplicates or lose data, depending on the query and the `scan.startup.mode` chosen.

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
2. Drop the tables (Terraform does not remove them). Run the following as two separate SQL statements
   ```sql
   DROP TABLE IF EXISTS `base_products`;
   DROP TABLE IF EXISTS `extended_products`;
   ```
3. Drop function
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
4. Delete Artifacts
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.0
   scripts/delete-artifact.sh --artifact-name udf-examples-1.1
   scripts/delete-artifact.sh --artifact-name udf-examples-2.0
   ```
