# Confluent Cloud Flink UDF Scripts

Shell scripts for managing Flink UDF artifacts and functions on Confluent Cloud using the Confluent CLI.

> ⚠️These scripts are provided as examples. They are not production-ready and may contain bugs. 
> Do not use them as-os in a production environment. 

Scripts:
* [`upload-artifact.sh`](#upload-artifactsh--upload-a-jar-artifact): Upload an artifact
* [`delete-artifact.sh`](#delete-artifactsh--delete-an-artifact): Delete an artifact (not reversible!)
* [`register-function.sh`](#register-functionsh--register-a-flink-udf): Register a function (execute `CREATE FUNCTION ...`)
* [`drop-function.sh`](#drop-functionsh--drop-a-flink-udf): Un-register a function (execute `DROP FUNCTION ...`)

Additional scripts:
* [`create-app-manager-sa.sh`](#create-app-manager-sash---create-app-manager-service-account--api-keys): Creates the Service Account to manage SQL statements
* [`create-platform-manager-sa.sh`](#create-platform-manager-sash---create-platform-manager-service-account--api-keys): Creates the Service Account to administer Flink

## Prerequisites

The **Confluent CLI** is the only prerequisite. No other tools are required.


## Authentication

Before running any script, you must authenticate:

```shell
confluent login
```

Log in uses email/password or SSO.
For non-interactive login via email/password the credentials can be set in the `CONFLUENT_CLOUD_EMAIL` and `CONFLUENT_CLOUD_PASSWORD`
environment variables.

See the [Confluent CLI authentication documentation](https://docs.confluent.io/confluent-cli/current/command-reference/confluent_login.html) for details, including SSO and API key login options.

> ⚠️If you are not logged in, scripts will fail with an error such as:
> *"Error: you must log in to Confluent Cloud with a username and password to use this command"*.


---

## Parameters

Each parameter can be passed directly as a flag or set via an environment variable. The flag takes precedence when both are provided.

| Flag | Env variable                      | Used by | Description |
| --- |-----------------------------------| --- | --- |
| `--environment-id` | `CONFLUENT_FLINK_ENVIRONMENT_ID`  | all scripts | Confluent Cloud environment ID (e.g. `env-abc123`) |
| `--cloud` | `CONFLUENT_FLINK_CLOUD_PROVIDER`  | `upload-artifact.sh`, `delete-artifact.sh`, `register-function.sh` | Cloud provider (e.g. `aws`) |
| `--region` | `CONFLUENT_FLINK_CLOUD_REGION`    | `upload-artifact.sh`, `delete-artifact.sh`, `register-function.sh` | Cloud region (e.g. `eu-west-1`) |
| `--compute-pool-id` | `CONFLUENT_FLINK_COMPUTE_POOL_ID` | `register-function.sh`, `drop-function.sh` | Flink compute pool ID (e.g. `lfcp-abc123`) |
| `--database` | `CONFLUENT_FLINK_DATABASE`        | `register-function.sh`, `drop-function.sh` | Kafka cluster used as the default database (e.g. `lkc-abc123`) |
| `--catalog` | `CONFLUENT_FLINK_CATALOG`         | `register-function.sh`, `drop-function.sh` | Flink catalog name |

---

## Scripts

### `upload-artifact.sh` — Upload a JAR artifact

```shell
upload-artifact.sh --path <path-to-jar> \
                   [--description "<description>"] \
                   [--environment-id <id>] [--cloud <provider>] [--region <region>] \
                   [--quiet]
```

Uploads a JAR file to Confluent Cloud as a Flink artifact. 
The artifact name is derived from the JAR filename (without the `.jar` extension). 

Fails if an artifact with the same name already exists.

On success, prints the assigned artifact ID to stdout:
```
cfa-abc123
```

**Example:**
```shell
upload-artifact.sh --path target/udf-examples-1.0.jar --description "UDF examples"
```

---

### `delete-artifact.sh` — Delete an artifact

```shell
delete-artifact.sh (--artifact-id <id> | --artifact-name <name>) \
                   [--environment-id <id>] [--cloud <provider>] [--region <region>] \
                   [--quiet]
```

Deletes a Flink artifact. Exactly one of `--artifact-id` or `--artifact-name` must be specified. 
When `--artifact-name` is given, the artifact list is queried first to resolve the ID.

On success, prints the deleted artifact ID to stdout:
```
cfa-abc123 DELETED
```

**Examples:**
```shell
delete-artifact.sh --artifact-id cfa-abc123
delete-artifact.sh --artifact-name udf-examples-1.0
```

---

### `register-function.sh` — Register a Flink UDF

```shell
register-function.sh --function <function-name> \
                     --class <fully-qualified-class-name> \
                     --artifact-id <artifact-id> \
                     [--environment-id <id>] [--cloud <provider>] [--region <region>] \
                     [--compute-pool-id <id>] [--database <database>] [--catalog <catalog>] \
                     [--quiet]
```

Registers a Java UDF by executing a `CREATE FUNCTION` statement in Confluent Cloud Flink. 

Fails if:
- the artifact ID does not exist
- the class is not found in the artifact
- the database does not exist
- a function with the same name already exists

On success, prints `COMPLETED`
  
  ;;
esac
**Example:**
```shell
register-function.sh \
  --function concat_with_separator \
  --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
  --artifact-id cfa-abc123 \
  --database cluster_0
```

---

### `drop-function.sh` — Drop a Flink UDF

```shell
drop-function.sh --function <function-name> \
                 [--environment-id <id>] [--compute-pool-id <id>] \
                 [--database <database>] [--catalog <catalog>] \
                 [--quiet]
```

Drops a registered Flink UDF by executing a `DROP FUNCTION` statement. 

Fails if the function does not exist.

On success, prints `COMPLETED` (or, with `--quiet`, the function name).

**Example:**
```shell
drop-function.sh --function concat_with_separator --database cluster_0
```

---

## Common Options

| Flag | Applies to | Description |
| --- | --- | --- |
| `--quiet` | all scripts | Suppresses informational messages; on success prints only the key result (artifact ID or function name). Errors are always printed to stderr. |
| `--environment-id <id>` | all scripts | Overrides `CONFLUENT_FLINK_ENVIRONMENT_ID` for this invocation |
| `--cloud <provider>` | `upload-artifact.sh`, `delete-artifact.sh`, `register-function.sh` | Overrides `CONFLUENT_FLINK_CLOUD_PROVIDER` |
| `--region <region>` | `upload-artifact.sh`, `delete-artifact.sh`, `register-function.sh` | Overrides `CONFLUENT_FLINK_CLOUD_REGION` |
| `--compute-pool-id <id>` | `register-function.sh`, `drop-function.sh` | Overrides `CONFLUENT_FLINK_COMPUTE_POOL_ID` |
| `--database <database>` | `register-function.sh`, `drop-function.sh` | Overrides `CONFLUENT_FLINK_DATABASE` |
| `--catalog <catalog>` | `register-function.sh`, `drop-function.sh` | Overrides `CONFLUENT_FLINK_CATALOG` |

---

## Example Workflow

Let's see how you can upload the artifact from this project and register one of the UDFs. 

```shell
# 1. Log in
confluent login

# 2. Set environment variables
export CONFLUENT_FLINK_ENVIRONMENT_ID=env-abc123
export CONFLUENT_FLINK_CLOUD_PROVIDER=aws
export CONFLUENT_FLINK_CLOUD_REGION=eu-west-1
export CONFLUENT_FLINK_COMPUTE_POOL_ID=lfcp-abc123

# 3. Build the artifact
mvn package

# 4. Upload the artifact and capture its ID
ARTIFACT_ID=$(scripts/upload-artifact.sh --path target/udf-examples-1.0.jar --quiet)

# 5. Register a function from that artifact
scripts/register-function.sh \
  --function concat_with_separator \
  --class io.confluent.flink.examples.udf.scalar.ConcatWithSeparator \
  --artifact-id "${ARTIFACT_ID}" \
  --database my_cluster

# 6. Drop the function when no longer needed
scripts/drop-function.sh --function concat_with_separator --database my_cluster

# 7. Delete the artifact
scripts/delete-artifact.sh --artifact-id "${ARTIFACT_ID}"
```

---

## Additional Scripts

Additional scripts, not directly related to the UDF workflow

### `create-app-manager-sa.sh` - Create App Manager Service Account + API Keys

Create the Service Account with permissions to create and manage Flink statements.

This Service Account is used by the Terraform module that creates the statements.

```shell
create-app-manager-sa.sh --name <sa-name> [--description '<description>'] \
                         [--environment-id <id>] [--kafka-cluster-id <id>] \
                         [--cloud <provider>] [--region <region>]
```

Creates a Confluent Cloud service account and configures it with all the RBAC roles
required to run Flink statements:

- **FlinkDeveloper** on the environment
- **ResourceOwner** on all Kafka topics (prefixed)
- **ResourceOwner** on Flink transactional IDs `_confluent-flink_*` (prefixed)
- **DeveloperWrite** on all Schema Registry subjects (prefixed)

The Schema Registry cluster ID is auto-discovered from the environment.

Finally, generates a Flink API key for the service account.

| Flag | Env variable | Description |
| --- | --- | --- |
| `--name` | — | Service account name (required) |
| `--description` | — | Service account description (default: "App Manager service account for Flink"). When specified, also used as the Flink API key description |
| `--environment-id` | `CONFLUENT_FLINK_ENVIRONMENT_ID` | Confluent Cloud environment ID |
| `--kafka-cluster-id` | `CONFLUENT_KAFKA_CLUSTER_ID` | Kafka cluster ID |
| `--cloud` | `CONFLUENT_FLINK_CLOUD_PROVIDER` | Cloud provider (e.g. `aws`) |
| `--region` | `CONFLUENT_FLINK_CLOUD_REGION` | Cloud region (e.g. `eu-west-1`) |

On success, prints the service account ID, Flink API key, and Flink API secret.

> ⚠️Take note of the API Key and Secret displayed by the script. It won't be possible to retrieve the secret later.

**Example:**
```shell
create-app-manager-sa.sh --name app-manager \
  --description "App Manager for Flink UDF examples"
```

---

### `create-platform-manager-sa.sh` - Create Platform Manager Service Account + API Keys

Create the Service Account with admin permissions to manage Flink resources in the environment.

```shell
create-platform-manager-sa.sh --name <sa-name> [--description '<description>'] \
                               [--environment-id <id>] [--kafka-cluster-id <id>]
```

Creates a Confluent Cloud service account and configures it with the RBAC roles
required to administer Flink:

- **FlinkAdmin** on the environment
- **ResourceOwner** on all Kafka topics (prefixed)

Finally, generates a Cloud API key for the service account.

| Flag | Env variable | Description |
| --- | --- | --- |
| `--name` | — | Service account name (required) |
| `--description` | — | Service account description (default: "Platform Manager service account for Flink"). When specified, also used as the Cloud API key description |
| `--environment-id` | `CONFLUENT_FLINK_ENVIRONMENT_ID` | Confluent Cloud environment ID |
| `--kafka-cluster-id` | `CONFLUENT_KAFKA_CLUSTER_ID` | Kafka cluster ID |

On success, prints the service account ID, Cloud API key, and Cloud API secret.

> ⚠️Take note of the API Key and Secret displayed by the script. It won't be possible to retrieve the secret later.

**Example:**
```shell
create-platform-manager-sa.sh --name platform-manager \
  --description "Platform Manager for Flink administration"
```