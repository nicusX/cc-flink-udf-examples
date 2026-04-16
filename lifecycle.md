# User Defined Functions Deployment Lifecycle

In this document we examine the deployment lifecycle of a user defined function (it works the same for scalar UDF, UDTF, or PTF) 
and the SQL statements using it.

The lifecycle uses a combination of shell scripts, leveraging Confluent CLI, 
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

1. Build artifact v1.0 - maven
2. Upload artifact v1.0 - script (`upload-artifact.sh`)
3. Register function(s) - script (`register-function.sh`)
4. Create SQL statements which use the UDF - Terraform


### Scenario 2: UDF update - no change to the SQL statement required

Let's test deploying a change which does not require changing the SQL statement.
We still need to stop and restart the statement, after having deployed and registered the new function version, to ensure
the new implementation is used.

1. Build artifact v1.1
2. Upload artifact v1.1 - script (`upload-artifact.sh`)
3. Un-register function(s) - script (`drop-function.sh`)
4. Register function(s) using artifact v1.1 (the running statement is still using the old version of the function) - script (`register-function.sh`)
5. Stop the SQL statement - Terraform
6. Restart the SQL statement (will use the new version of the function) - Terraform
7. (test)
8. (optional) Delete artifact v1.0 - script (`delete-artifact.sh`)

### Scenario 3: Rollback to v1

If something wrong is detected during tests, in the previous workflow:

1. Un-register function(s) - script (`drop-function.sh`)
2. Register function(s) using artifact v1.0 - script (`register-function.sh`)
3. Stop the SQL statement - Terraform
4. Restart the SQL statement - Terraform
5. (optional) Delete artifact v1.1 (the one not working correctly) - script (`delete-artifact.sh`)


### Scenario 4: UDF update - change to the SQL statement required

If the UDF change requires modifying the SQL statement too, for example for a change in the function signature, you need
to replace the SQL statement v1 (using UDF v1.x) with a new v2 statement (which uses UDF v2.0). 
We ensure statement v2 starts from exactly the same position in the source table where v1 was stopped.

The first steps are identical to Scenario 2:
1. Build artifact v2.0
2. Upload artifact v2.0 - script (`upload-artifact.sh`)
3. Un-register function(s) - script (`drop-function.sh`)
4. Register function(s) using artifact v2.0  - script (`register-function.sh`)
5. Stop the SQL statement - Terraform

From here, things are different, because we need to create a new statement and pass the offsets:
6. Fetch the latest offsets where the statement v1 was stopped - script (`get_latest_offsets.sh`)
7. Create the new v2 statement and start it from the latest offsets of v1; v1 stays stopped - Terraform
8. (Run tests)
9. Make the initial offsets permanent and v1 stopped, permanently - Terraform


---

## Step-by-step Lifecycle

We describe the step-by-step process for each scenario.

For the scope of this example, the steps are executed manually. In a real scenario, there will be some form of orchestrator
like a CI/CD automation tool combined with scripting.
 
### Parameters for scripts and Terraform

The scripts and Terraform module expect several parameters. They use two different mechanisms:
* Scripts: you can pass parameters explicitly or via environment variables (see [scripts README - Parameters](./scripts/README.md#parameters))
* Terraform: create a copy of [`terraform/example.tfvars`](terraform/example.tfvars) called for example `my_env.tfvars`, 
  edit with your actual values, and pass the file on every invocation of `terraform` with `-var-file=my_env.tfvars`

> ⚠️ Some of these variables contain secrets. Make sure you do not commit these to a shared repository.

#### Variables for scripts

| Env variable | Description                                       |
| --- |---------------------------------------------------|
| `CONFLUENT_FLINK_ENVIRONMENT_ID` | Confluent Cloud environment ID (e.g. `env-abc123`) |
| `CONFLUENT_FLINK_CLOUD_PROVIDER` | Cloud provider (e.g. `aws` - lowercase)           |
| `CONFLUENT_FLINK_CLOUD_REGION` | Cloud region (e.g. `eu-west-1`)                   |
| `CONFLUENT_FLINK_COMPUTE_POOL_ID` | Flink compute pool ID (e.g. `lfcp-abc123`)        |
| `CONFLUENT_FLINK_DATABASE` | Kafka cluster used as the default database        |
| `CONFLUENT_FLINK_CATALOG` | Flink catalog name                                |
| `CONFLUENT_KAFKA_CLUSTER_ID` | Kafka cluster ID (e.g. `lkc-abc123`)              |
| `CONFLUENT_CLOUD_API_KEY` | Cloud API key (Platform Manager service account)  |
| `CONFLUENT_CLOUD_API_SECRET` | Cloud API secret                                  |
| `CONFLUENT_FLINK_API_KEY` | Flink API key (App Manager service account)       |
| `CONFLUENT_FLINK_API_SECRET` | Flink API secret                                  |
| `CONFLUENT_APP_MANAGER_SERVICE_ACCOUNT_ID` | App Manager service account ID (e.g. `sa-abc123`) |

> ℹ️ We recommend creating a script to set these environment variables.

#### Variables for Terraform (.tfvars file)

Example of `.tfvars` file content (also see [terraform/example.tfvars](./terraform/example.tfvars))

```terraform
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
```


### Confluent CLI Login

The scripts use Confluent CLI which requires an authenticated session.
Use [`confluent login`](https://docs.confluent.io/confluent-cli/current/command-reference/confluent_login.html) to authenticate.

Supported authentication methods are email+password and SSO.
Which method to use depends on the setup of the Confluent Cloud Organization.
For email+password authentication, you can set the environment variables `CONFLUENT_CLOUD_EMAIL` and `CONFLUENT_CLOUD_PASSWORD`,
and `confluent login` will use them automatically to log you in.


### Terraform INIT

Initialize the Terraform project.

```shell
terraform -chdir=terraform init
```

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
     Make sure you have selected the correct Catalog ( = Environment) and Database ( = Kafka cluster).
4. Create SQL statements which use the UDF - using Terraform
   1. Terraform Plan - save the plan in `plan.tfplan`
      (replace `my_env.tfvars` with your configuration file)
      ```shell
      terraform -chdir=terraform plan -out=plan.tfplan -var-file=my_env.tfvars
      ```
   2. Terraform Apply
      (use the saved plan - variables are not required)
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```

The initial version of the UDF is now deployed and being used by the running `INSERT INTO ...` statement writing into `extended_products`.
A second `INSERT INTO ...` is also running to populate `base_products`, to have a complete end-to-end pipeline.

You can observe source data being written into `base_products` and the transformed data into `extended_products`
by running `SELECT * FROM base_products` and `SELECT * FROM extended_products` from a SQL Workspace. 

In the Compute Pool, you can also observe the two `INSERT INTO ...` statements running.

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
   * You can observe that the statement keeps running, checking the Compute Pool and querying `extended_products`.
   * If you try to describe the function, with `DESCRIBE FUNCTION concat_with_separator` you get an error, because the UDF has been un-registered.
4. Register the function using the new artifact version
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "${ARTIFACT_ID}"
   ```
   * Run `DESCRIBE FUNCTION concat_with_separator`. You can see the function is there now, and uses the new artifact (`plugin id`).
5. Stop the SQL statement
   (we use Terraform to stop and restart the statement; this ensures the statement uses the new UDF version)
   1. Terraform Plan
      (note the additional parameter `-var="stop_statement_v1=true"`)
      ```shell
      terraform -chdir=terraform plan -var="stop_statement_v1=true" -out=plan.tfplan  -var-file=my_env.tfvars
      ```
   2. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * In the Console > Compute Pool, you can see the statement is now "Stopped"
6. Restart the SQL statement
   1. Terraform Plan (note: no `stop_statement_v1` parameter; default is `false`)
      ```shell
      terraform -chdir=terraform plan -out=plan.tfplan -var-file=my_env.tfvars
      ```
   2. Terraform Apply - restart the statement, using the new function version
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * In the Console > Compute Pool, you can see the statement is now "Running"
7. (Run tests) - You normally run some tests to verify the new UDF is working as expected.
   In this example, simply execute `SELECT $rowtime, * FROM extended_products` and verify new records are emitted.
8. (Optional) Delete the old artifact
   > ℹ️ Deleting the old artifact is optional, because we are versioning artifacts by name.
   > There is no cost directly associated with uploaded artifacts.
   > However, there are [limitations](https://docs.confluent.io/cloud/current/flink/concepts/user-defined-functions.html#udf-limitations)
   > to the total number of artifacts per environment and cloud region.
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.0
   ```


### Scenario 3: Rollback to a previous UDF version

If your test failed (and you haven't yet deleted the artifact version 1.0), you can roll back to the previous version of 
the function.


1. Un-register the function
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
2. Register the function using the previous artifact version
   * In a real automation scenario, the old artifact-id must be saved somewhere and passed to the script.
     For the sake of this example, we can run `scripts/list-artifacts.sh` to show all artifacts uploaded in the specific Environment and check the ID.
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "<previous-artifact-id>"
   ```   
3. Stop & Restart the SQL statement - Use Terraform, same as in the previous scenario
4. (Optional) Delete artifact v1.1, which wasn't working properly
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.1
   ```

### Scenario 4: UDF update - change to the SQL statement required

We want to simulate a change to the function which requires modifying the SQL statement. For example, a change in the function signature.

In this test, we do not need to change the function code. `concat_with_separator` supports multiple signatures with different
numbers of parameters. We will pretend we made a major change by bumping the POM version to `2.0` and updating the SQL statement
to use a different function signature.

In this case, we need to replace the statement with a new one (in the previous case, we just stopped and restarted the same statement).

> ℹ️ This scenario requires editing the Terraform files. Some will be manual, like adding the new statement version.
> Other changes, such as updating `variables.tf` to make the initial offsets permanent, should be automated.


The initial steps are identical to the simple update, except we bump the version to 2.0:

1. Build a new version (2.0)
   * Bump the version to 2.0
   * Rebuild: `mvn clean package` - creates `target/udf-examples-2.0.jar`
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
5. Stop the SQL statement v1
   (we use Terraform to stop the statement)
   1. Terraform Plan
      (note the additional parameter `-var="stop_statement_v1=true"`)
      ```shell
      terraform -chdir=terraform plan -var="stop_statement_v1=true" -out=plan.tfplan  -var-file=my_env.tfvars
      ```
   2. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   
The steps that follow are different from the simple update:

6. Fetch the latest offsets where the statement v1 was stopped
   ```shell
   OFFSETS=$(scripts/get_latest_offsets.sh --statement-name "$(terraform -chdir=terraform output -raw v1-statement-name)" --table base_products --quiet)
   ```
   * Verify the offsets: `echo $OFFSETS`
7. Create the new v2 statement and start it from the latest offsets of v1; v1 stays stopped
   1. Edit [main.tf](terraform/main.tf): uncomment the block of code with the resource `insert_into_extended_products_v2`
      (in a real scenario, you would add a new resource now)
   2. Terraform Plan - pass the offsets, and keep v1 stopped
      (note `-var="stop_statement_v1=true"` and `-var="initial_offsets_v2=${OFFSETS}"`)
      ```shell
      terraform -chdir=terraform plan -var="stop_statement_v1=true" -var="initial_offsets_v2=${OFFSETS}" -out=plan.tfplan  -var-file=my_env.tfvars
      ```
   3. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
8. (Run tests to verify the statement v2 with UDF 2.0 works)
9. Make the initial offsets permanent and v1 stopped, permanently
   1. Get the latest offsets of statement v1
   ```shell
   scripts/get_latest_offsets.sh --statement-name "$(terraform -chdir=terraform output -raw v1-statement-name)" --table base_products --quiet
   ```
   > ℹ️ We haven't yet deleted statement v1, and its latest offsets are still available.
   2. Edit [variables.tf](./terraform/variables.tf)
      * Set the default for `initial_offsets_v2` to the latest offsets of v1
      * Set the default for `stop_statement_v1` to `true`
   3. Terraform Plan (note: no additional parameters, because we changed the defaults)
      ```shell
      terraform -chdir=terraform plan -out=plan.tfplan  -var-file=my_env.tfvars
      ```
   4. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```

> ⚠️ The last step is important to ensure Terraform does not restart the statement v1 and does not modify statement v2
> at the next change.
> If Terraform detects a change in the statement, for example, the absence of the `initial_offsets_v2` variable, it will destroy
> the statement and create a new one, losing the starting position.

--- 

## Cleanup

To eliminate all resources created by this example:

1. Terraform destroy
   ```shell
   terraform -chdir=terraform destroy -var-file=my_env.tfvars
   ```
2. Drop the tables (Terraform does not remove them)
   ```shell
   scripts/drop-table.sh --table base_products
   scripts/drop-table.sh --table extended_products
   ```
3. Drop the function
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
4. Delete artifacts
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.0
   scripts/delete-artifact.sh --artifact-name udf-examples-1.1
   scripts/delete-artifact.sh --artifact-name udf-examples-2.0
   ```
