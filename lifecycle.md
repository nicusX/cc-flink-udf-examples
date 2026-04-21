# User Defined Functions Deployment Lifecycle

In this document we examine the deployment lifecycle of a user defined function (it works the same for scalar UDF, UDTF, or PTF) 
and the SQL statements which use the function.

The lifecycle uses a combination of shell scripts, leveraging Confluent CLI, and Terraform.

The goal is demonstrating these scenarios:
1. Initial deployment
2. Update to function, not requiring any change to the SQL statement
3. Rollback from an update
4. Update to a function requiring a change to the SQL statement

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

Some Confluent Cloud resources such as Environment, Flink Compute Pool, Kafka cluster, Service Accounts, and API keys.
Their creation is out of scope for this example.
Also, in a real scenario, these resources are normally managed by a "platform team", separate from the team responsible for
Flink.


#### Confluent Cloud resources

* An **Environment**
* A **Kafka Cluster**, in the Environment
* A Flink **Compute Pool**, in the same Environment and in the **same cloud region** as the Cluster

#### Service Accounts and API keys

> ⚠️ Confluent CLI and Terraform use different ways to pass credentials.
> The CLI authenticates with a user (email/password or SSO).
> Terraform uses API keys from the Service Accounts.


You need two [Service Accounts](https://docs.confluent.io/cloud/current/security/authenticate/workload-identities/service-accounts/index.html) for the automation.

* A *"Platform Manager"* Service Account + API Key with Cloud Resource Management scope (see [Terraform - *Platform Manager* Service Account & API Key](./terraform/README.md#platform-manager-service-account--api-key))
* An *"App Manager"* Service Account + API Key with Flink region scope (see [Terraform - *App Manager* Service Account & API Key](./terraform/README.md#app-manager-service-account--api-key))

#### Confluent Cloud User - for CLI/scripts

For minimum permissions for the user for Confluent CLI see [Scripts README - Minimum permissions](./scripts/README.md#minimum-permissions).

---

## Lifecycle Summary

High level lifecycle of the different scenarios (see [Step-by-step Lifecycle](#step-by-step-lifecycle) for the detailed steps).

### Scenario 1: First deployment

First deployment in an environment. Neither the function nor the Flink SQL statements exist.

1. Build artifact v1.0 - maven
2. Upload artifact v1.0 - script (`upload-artifact.sh`)
3. Register function(s) - script (`register-function.sh`)
4. Create SQL statements which use the UDF - Terraform


### Scenario 2: UDF update - no change to the SQL statement required

The statement is running, using the version 1.0 of the function.
We want to update the function.
No change is required to the SQL statement.

1. Build artifact v1.1
2. Upload artifact v1.1 - script (`upload-artifact.sh`)
3. Un-register function(s) - script (`drop-function.sh`)
4. Register function(s) using artifact v1.1 (the running statement is still using the old version of the function) - script (`register-function.sh`)
5. Stop the SQL statement - Terraform
6. Restart the SQL statement (will use the new version of the function) - Terraform
7. (test)
8. (optional) Delete artifact v1.0 - script (`delete-artifact.sh`)

> This process ensures the statement is stopped and restarted from the same position in the source topics. 
> No data loss nor duplicates are expected (duplicates may appear if isolation level is changed to "read-uncommitted").
> Also, if the query is stateful, any state is preserved.

### Scenario 3: Rollback to v1

Version 1.1 of the function is in use, but we want to roll back to v1.0.

1. Un-register function(s) - script (`drop-function.sh`)
2. Register function(s) using artifact v1.0 - script (`register-function.sh`)
3. Stop the SQL statement - Terraform
4. Restart the SQL statement - Terraform
5. (optional) Delete artifact v1.1 (the one not working correctly) - script (`delete-artifact.sh`)

> This process stops the running statement using function v1.1 and restart it from the same position using function v1.0.
> This generates no data loss and no duplicates.
> But depending on the problem detected with the function, data may have been corrupted.
> In this case, you may want to go back and replay data from the source. How to do this exactly depends on the use case and the problem,
> so it is not covered in this example.


### Scenario 4: UDF update - change to the SQL statement required

The statement is running.
We want to update the function to a new v2.0.
The change also requires some changes in the SQL statement.

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

> This process restarts the modified statement from the position the old statement is stopped.
> This guarantees no re-processing. However, if the statement is stateful, the state is not preserved. The impact depends
> on the statement and the use case.

---

## Step-by-step Lifecycle

Step-by-step process for each scenario.

In this example, the steps are executed manually via command line.
In a real scenario, some CI/CD automation will orchestrate the process. 
How to implement the orchestration depends on the CI/CD tool and is out of scope for this example.

 
### Parameters for scripts and Terraform

The scripts and Terraform module expect several parameters. 
* Scripts: pass parameters explicitly or via environment variables (see also [scripts README - Parameters](./scripts/README.md#parameters)).
* Terraform: create a copy of [`terraform/example.tfvars`](terraform/example.tfvars) named `my_env.tfvars`. 
  Edit with your actual values. This configuration file is passed to `terraform` with `-var-file=my_env.tfvars`.

> ⚠️ Some of these variables contain secrets. Make sure you do not commit these to a shared repository.

#### Variables for scripts

The following parameters can be passed to the scripts setting env variables:

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
F
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

The initial version of the UDF is now deployed and being used by the running `INSERT INTO ...` statement, which writes into `extended_products`.
A second `INSERT INTO ...` statement is also running to populate `base_products`, to have a complete end-to-end pipeline.
This second statement is not relevant for the example.

You can observe source  data in `base_products` and the transformed data in `extended_products`
by running `SELECT * FROM base_products` and `SELECT * FROM extended_products` from a SQL Workspace. 

In the Compute Pool UI, you can also observe the two `INSERT INTO ...` statements running.

### Scenario 2: UDF update - no change to the SQL statement required

We now simulate a change in the UDF internal implementation. 
The change does not require modifying the SQL statement. 

For this example, we do not really need to change the code. 
We just bump the artifact `version` to `1.1` editing [pom.xml](pom.xml) and pretending an update.

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
   * (optional) Observe the statement keeps running: see the Compute Pool UI and query `SELECT $rowcount, * FROM extended_products`.
   * (optional) Try `DESCRIBE FUNCTION concat_with_separator`: you get an error, because the UDF has been un-registered.
4. Register the function using the new artifact
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "${ARTIFACT_ID}"
   ```
   * (optional) Execute `DESCRIBE FUNCTION concat_with_separator`: the function is there now, and uses the new artifact (`plugin id`).
5. Stop the SQL statement
   (we must stop and restart the statement to use the new function)
   1. Terraform Plan
      (note the additional parameter `-var="stop_statement_v1=true"`)
      ```shell
      terraform -chdir=terraform plan -var="stop_statement_v1=true" -out=plan.tfplan  -var-file=my_env.tfvars
      ```
   2. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * (optional) In the Console > Compute Pool: the statement is now "Stopped"
6. Restart the SQL statement
   1. Terraform Plan (note: no `stop_statement_v1` parameter; default is `false`)
      ```shell
      terraform -chdir=terraform plan -out=plan.tfplan -var-file=my_env.tfvars
      ```
   2. Terraform Apply
      ```shell
      terraform -chdir=terraform apply plan.tfplan
      ```
   * (optional) In the Console > Compute Pool: the statement is now "Running"
7. (Run tests) - You normally run some tests to verify the new UDF is working as expected.
   * In this example, simply execute `SELECT $rowtime, * FROM extended_products` and verify new records are emitted.
8. (Optional) Delete the old artifact
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.0
   ```

> ℹ️ Deleting the old artifact is optional, because we are versioning artifacts by name.
> There is no cost directly associated with uploaded artifacts.
> However, there are [limitations](https://docs.confluent.io/cloud/current/flink/concepts/user-defined-functions.html#udf-limitations)
> to the total number of artifacts per environment and cloud region.


### Scenario 3: Rollback to a previous UDF version

Rollback to the previous function version (1.0) after the statement is running with function 1.1.

1. Un-register the function
   ```shell
   scripts/drop-function.sh --function concat_with_separator
   ```
2. Register the function using the previous artifact version
   * In a real automation scenario, the old artifact-id must be saved or retrieved externally and passed to the script.
     For the sake of this example, we show all the artifacts with `scripts/list-artifacts.sh`.
   ```shell
   scripts/register-function.sh \
     --function concat_with_separator \
     --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
     --artifact-id "<previous-artifact-id>"
   ```   
3. Stop & Restart the SQL statement - Use Terraform, same as in the previous scenario
4. (Optional) Delete artifact v1.1
   ```shell
   scripts/delete-artifact.sh --artifact-name udf-examples-1.1
   ```

### Scenario 4: UDF update - change to the SQL statement required

Update the function to version.
The change also requires some changes to the SQL statement (for example, because the function signature has changed).

For this example, we do not need to change the code of the function.
`concat_with_separator` supports multiple signatures. 
We will pretend we made a major change by bumping the POM version to `2.0` and creating new SQL statement which uses more parameters.

Confluent Flink statements are immutable.
Any change to the SQL code requires creating a new statements.

In Terraform, you cannot just modify the existing statement, because Terraform would delete the old one and create 
the new Flink statement. If this happens, the new statement will restart consuming from the source table from the position
defined by the `scan.startup.mode` option.
This is **not** what you normally want in production.

The process shown below stops the old statement, retrieves the offset where it stopped, and pass it to the new statement as starting position.
This prevents reprocessing.

> ℹ️ This scenario requires editing the Terraform files. 
> In a real scenario, some of this editing, such as adding the new SQL statement, will be done by the developers.
> Other changes, such as updating `variables.tf` to make the initial offsets permanent, can be automated.


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
   
The steps that follow are different from the simple update.

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

> ⚠️ The last step is important. It ensures that Terraform does not restart the statement v1 and does not modify statement v2
> at the next change.
> If Terraform detects a change in the statement, for example, the absence of the `initial_offsets_v2` variable, it will destroy
> the statement and create a new one, losing the starting position.

--- 

## Cleanup

Cleanup the resources created by this example.

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
