# Confluent Cloud Flink UDF Scripts

Shell scripts for managing Flink UDF artifacts and functions on Confluent Cloud using the Confluent CLI.

> ⚠️These scripts are provided as examples. They are not production-ready and may contain bugs. 
> Do not use them in a production environment. 

Scripts:
* `upload-artifact.sh`: Upload an artifact
* `delete-artifact.sh`: Delete an artifact (not reversible!)
* `register-function.sh`: Register a function (execute `CREATE FUNCTION ...`)
* `drop-function.sh`: Un-register a function (execute `DROP FUNCTION ...`)

## Prerequisites

The **Confluent CLI** is the only prerequisite. No other tools are required.


## Authentication

Before running any script, you must authenticate:

```shell
confluent login
```

If you are not logged in, scripts will fail with an error such as:
*"Error: you must log in to Confluent Cloud with a username and password to use this command"*.

See the [Confluent CLI authentication documentation](https://docs.confluent.io/confluent-cli/current/command-reference/confluent_login.html) for details, including SSO and API key login options.

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