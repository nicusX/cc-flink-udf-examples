# User Defined Functions Deployment Lifecycle

In this document we examine the deployment lifecycle of a user defined function (it works the same for scalar UDF, UDTF, or PTF) 
and the SQL statements using it.

The lifecycle uses a combination of shell scropts, leveraging Confluent CLI, 
and a simple Terraform module.

Check out the detailed documentation in the [terraform](./terraform) and [scripts](./scripts) subfolders.

**Table of Contents**
* [Prerequisites](#prerequisites)
* [Lifecycle Summary](#lifecycle-summary)
* [Step-by-step Lifecycle](#step-by-step-lifecycle)
* [Cleanup](#cleanup)

---

## Prerequisites

### Local prerequisites

* JDK 17+ (the POM compiles the UDF Java code with *target* version 17)
* Maven
* Terraform
* Confluent CLI

### Confluent Cloud Prerequisites

Before starting the process of deploying the UDF and the SQL statements using it, you need to create some resources.
How to create them is out of scope for this document.

> ℹ️ In a real scenario, Environment, Compute Pool, Kafka Cluster, Service Accounts, and API keys are probably created
> at top level by the team managing the platform, and provided to the team responsible for the Flink statements and UDFs.
> For these examples, scripts to create the Service Accounts and API Keys are provided.
> Environment, Compute Pool, and Kafka cluster are assumed to be already in place. They can be easily created via Confluent Cloud UI.

#### Confluent Cloud resources

* An **Environment**
* A **Kafka Cluster**, in the Environment
* Flink **Compute Pool**, in the same Environment and in the **same cloud region** as the Cluster

#### Service Accounts and API keys

You also need to create some [Service Accounts](https://docs.confluent.io/cloud/current/security/authenticate/workload-identities/service-accounts/index.html) 
the automation will use for authentication and authorization.

* Service Account and API Key with Cloud Resource Management scope (see [Terraform - *Platform Manager* Service Account & API Key](./terraform/README.md#platform-manager-service-account--api-key))
* Service Account and API Key with Flink region scope (see [Terraform - *App Manager* Service Account & API Key](./terraform/README.md#app-manager-service-account--api-key))

> ⚠️ Note that Confluent CLI and Terraform use different ways to pass credentials.
> The scripts require a valid authenticated session with the CLI, via email/password or SSO.
> Terraform uses API keys.

---

## Lifecycle Summary

Let's examine the lifecycle at a high level, for different scenarios.

### Scenario 1: First deployment

1. Build artifact v1 - maven
2. Upload artifact v1 - script (`upload-artifact.sh`)
3. Register function(s) - script (`register-function.sh`)
4. Create SQL statements which use the UDF - Terraform


### Scenario 2: UDF update - no change to the SQL statement required

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

### Scenario 3: Rollback to v1

1. Un-register function(s) - script (`drop-function.sh`)
2. Register function(s) using artifact v1 - script (`register-function.sh`)
3. Stop the SQL statement - Terraform
4. Restart the SQL statement - Terraform
5. (optional) Delete artifact v2 (the one not working correctly) - script (`delete-artifact.sh`)


### Scenario 4: UDF update - change to the SQL statement required

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
7. Stop v2, remove carry-over offsets, and delete statement v1 - Terraform
8. Restart v2 - Terraform


---

## Step-by-step Lifecycle


### Passing parameters to the scripts and Terraform

The scripts provided and the Terraform module expect several parameters.

* Scripts: you can pass parameters explicitly or via environment variables
* Terraform: you need to pass all parameters explicitly, when invoking `terraform <command>`.
  In these examples we get the values of these parameters from environment variables.


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

> ℹ️ We recommend creating a script to set these environment variables.
> ⚠️Some of these env variables contain secrets. Make sure you do not commit the script to any shared repository.

### Confluent CLI Login

Before running any of the scripts you need to start an authenticated session for Confluent CLI.

Use [`confluent login`](https://docs.confluent.io/confluent-cli/current/command-reference/confluent_login.html) to authenticate.

The authentication method can be email/password or SSO, depending on the setup of the Confluent Cloud Organization.
If your organization uses email/password, you can set the environment variables `CONFLUENT_CLOUD_EMAIL` and `CONFLUENT_CLOUD_PASSWORD`.
`confluent login` will use them automatically to log you in.


### Terraform INIT

Before using the Terraform module the first time, run `terraform init` from within the `./terraform` folder.

### Scenario 1: First deployment

1. Build the artifact (version 1.0)
   * In the POM file: `version` = `1.0`
   * Build artifact: `mvn clean package` - creates the artifact `./target/udf-examples-1.0.jar`
2. Upload the artifact (version 1.0)
   ```shell
   scripts/upload-artifact.sh --path <path-to-jar> [--description '<description>'] [--quiet]
   ```
   Example:
   ```shell
   ARTIFACT_ID=$(scripts/upload-artifact.sh --path target/udf-examples-1.0.jar --description "UDF examples v1.0" --quiet)
   ```
   With `--quiet`, the script prints only the artifact ID (e.g. `cfa-abc123`) to stdout, which can be captured for subsequent steps.
3. Register the `concat_with_separator` function
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
   * You can verify that the function was actually registered by executing `SHOW USER FUNCTIONS` and `DESCRIBE FUNCTION concat_with_separator` in a SQL Workspace.
     Make sure you have selected the correct Catalog ( = Environment) and Database ( = Kafka cluster)
4. Create SQL statements which use the UDF - using Terraform
   1. Terraform Plan
      (pass all required variables getting values from environment variables; save the plan in `plan.tfplan`)
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
      (use the saved plan)
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```

The initial version of the UDF is now deployed and being used by the running `INSERT INTO ...` statement writing into `extended_products`.
A second `INSERT INTO ...` is also running to populate `base_products`, to have a complete end-to-end pipeline.

You can observe source data being written into `base_products` and the transformed data into `extended_products`
running `SELECT * FROM base_products`  and `SELECT * FROM extended_products` from a SQL Workspace. 

In the Compute Pool you can also observe the two `INSERT INTO ...` statements running.

### Scenario 2: UDF update - no change to the SQL statement required

We now simulate a change in the UDF internal implementation. The change does not require modifying the SQL statement. 

In this example, we do not really need to change the code. We edit the [pom.xml](pom.xml) bumping the `version` to `1.1`,
pretending an update.

1. Build the new version (1.1)
   1. Edit the `pom.xml`, change `version` to `1.1`
   2. Build artifact: `mvn clean package` - creates `target/udf-examples-1.1.jar`
2. Upload the new artifact (version 1.1)
   ```shell
   ARTIFACT_ID=$(scripts/upload-artifact.sh --path target/udf-examples-1.1.jar --description "UDF examples v1.1" --quiet)
   ```
3. Un-register the function
   (the running statement using the UDF keeps running, using the previous UDF version)
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
   * You can observe that the statement keeps running, checking the Compute Pool and querying `extended_products`
   * If you try to describe the function, with `DESCRIBE FUNCTION concat_with_separator` you get an error, because the UDF has been un-registered.
4. Register the function using the new artifact version
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "${ARTIFACT_ID}"
   ```
   * Run `DESCRIBE FUNCTION concat_with_separator`. You can see the function is there now, and uses the new artifact (`plugin id`)
5. Stop the SQL statement
   (we use Terraform to stop and restart the statement; this makes the statement using the new UDF version)
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
   * In the Console > Compute Pool, you can see the statement is now "Stopped"
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
   * In the Console > Compute Pool, you can see the statement is now "Running"
7. (Run tests) - You normally run some tests to verify the new UDF is working as expected.
   In this example, simply execute `SELECT $rowtime, * FROM extended_products` and verify new records are emitted.
8. (Optionally) delete the old artifact
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.0
   ```

> ℹ️ Deleting the old artifact is optional, because we are versioning artifacts by name.
> There is no cost directly associated with uploaded artifacts. 
> However, there are [limitations](https://docs.confluent.io/cloud/current/flink/concepts/user-defined-functions.html#udf-limitations)
> to the total number of artifacts per environment and cloud region.

### Scenario 3: Rollback to a previous UDF version

If your test failed (and you haven't yet deleted the artifact version 1.0), you can roll back to the previous version of 
the function.


1. Un-register function
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
2. Register function using the previous artifact version
   * In a real automation scenario the old artifact-id must be saved somewhere and passed to the script.
     For the sake of this example, we can run `scripts/list-artifacts.sh` to show all artifacts uploaded in the specific Environment and check the ID
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

### Scenario 4: UDF update - change to the SQL statement required

We want to simulate a change to the function which requires modifying the SQL statement. For example, a change in the function signature.

In this test we do not need to change the function code. `concat_with_separator` supports multiple signatures with different
numbers of parameters. We will pretend we made a major change bumping the POM version to `2.0` and updating the SQL statement
to use a different function signature.


In this case, we need to replace the statement with a new one (in the previous case, we just stopped and restarted the same statement).

Confluent Cloud Flink provides the [Carry-over Offsets](https://docs.confluent.io/cloud/current/flink/operate-and-deploy/carry-over-offsets.html) feature you can use to ensure the new statement starts from
the same position in the source topic where the old statement is stopped. This guarantees no data loss and no duplicates
when the update is executed.

> ⚠️ Carry-over offsets have limitations. It only supports stateless statements. If your statement is not stateless, you
> cannot use Carry-over offsets.
> Note that statements writing to an upsert table - like a table with a primary key - cannot use Carry-over offsets because 
> they internally use a stateful operator.

> ℹ️ Why we can't rely on Terraform to update the statement?
> To use Carry-over offsets we cannot just update the SQL code of the statement and let Terraform update it.
> Confluent Flink statements are immutable. Any change to the statement requires stopping, deleting, and creating a new statement.
> This is what Terraform would do if you modify an existing statement. However, Carry-over offsets require a reference
> to the old statement which cannot be deleted when the new statement starts. This implies stopping the first statement
> and creating a new one.
 


The initial steps are identical to the simple update.

1. Build a new version (2.0)
   * Bump the version to `2.0` 
   * Rebuild: `mvn clean package`
2. Upload new artifact (2.0)
   ```shell
   ARTIFACT_ID=$(scripts/upload-artifact.sh --path target/udf-examples-2.0.jar --description "UDF examples v2.0" --quiet)
   ```
3. Un-register the function
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
4. Register the function using the new artifact (2.0), just uploaded
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "${ARTIFACT_ID}"
   ```

The steps that follow are different from the simple update:
5. Stop statement v1 and create statement v2 using carry-over offsets.
   Edit [main.tf](terraform/main.tf) making the following changes (the code is already there, you need to comment/uncomment sections)
   1. Old v1 statement `confluent_flink_statement.insert_into_extended_products_v1`: force the property `stopped = true`.
      This ensures the old statement is stopped, but not deleted.
   2. New v2 statement `confluent_flink_statement.insert_into_extended_products_v2`: uncomment the entire resource.
      (Note this sets the property `"sql.tables.initial-offset-from"` to the auto-generated name previously assigned to the v1 statement)
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
6. (Run tests) - Execute `SELECT $rowtime, * FROM extended_products` to observe records are emitted
7. Stop v2, remove carry-over offsets, and delete statement v1
      1. Edit [main.tf](terraform/main.tf):
         * Remove (comment out) the resource of the v1 statement (`confluent_flink_statement.insert_into_extended_products_v1`)
         * Remove (comment out) the property `"sql.tables.initial-offset-from" = confluent_flink_statement.insert_into_extended_products_v1.statement_name`
            in the v2 statement  (`confluent_flink_statement.insert_into_extended_products_v2`)
      2. Terraform Plan (note the `-var="statement_stopped=true"` parameter)
         > ⚠️ The Terraform provider requires `stopped` to be toggled when modifying statement properties.
         > Since we are removing the carry-over offset property, `stopped` must also change.
         > We stop v2 here (combining the property change with the stop) and restart it in the next step.
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
      3. Terraform Apply
         ```shell
         terraform -chdir=terraform apply plan.tfplan
         ```
8. Restart v2
      1. Terraform Plan (no `-var="statement_stopped=true"` — the default `false` restarts v2)
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

#### What if my statement is stateful and cannot use carry-over offsets?

If your statement cannot use carry-over offsets, for example because the statement is stateful or emits an upsert changelog,
changes to the statement still require deleting and creating a new statement.
This is what Terraform does, in place, if you modify the SQL of a statement.

The new statement restarts from a position determined by the [`'scan.startup.mode'`](https://docs.confluent.io/cloud/current/flink/reference/statements/create-table.html#scan-startup-mode)
option, either the value on the table or what's overridden via [`SET`](https://docs.confluent.io/cloud/current/flink/reference/statements/set.html) 
or [hint](https://docs.confluent.io/cloud/current/flink/reference/statements/hints.html).

Which [startup mode](https://docs.confluent.io/cloud/current/flink/reference/statements/create-table.html#scan-startup-mode) to use
depends on the query and the use case.
In general, exactly-once guarantees are not possible across a statement change. You need to expect either duplicates or data loss, 
depending on the startup mode selected.

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
2. Drop the tables (Terraform does not remove them)
   ```shell
   scripts/drop-table.sh --table base_products
   scripts/drop-table.sh --table extended_products
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
