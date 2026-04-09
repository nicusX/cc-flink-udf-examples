#!/usr/bin/env bash
# register-function.sh — Register a Java UDF in Confluent Cloud Flink
#
# Usage:
#   register-function.sh --function <function-name> --class <class-name> --artifact-id <artifact-id> --database <database> [--quiet]
#                        [--environment-id <id>] [--cloud <provider>] [--region <region>]
#                        [--compute-pool-id <id>] [--catalog <catalog>]
#
# Required env vars (set by .secrets/credentials.sh), overridable via parameters:
#   CONFLUENT_FLINK_ENVIRONMENT_ID  (--environment-id)
#   CONFLUENT_FLINK_COMPUTE_POOL_ID (--compute-pool-id)
#   CONFLUENT_FLINK_CLOUD_PROVIDER  (--cloud)
#   CONFLUENT_FLINK_CLOUD_REGION    (--region)
#   CONFLUENT_FLINK_CATALOG         (--catalog)

set -euo pipefail

log()   { [[ "${QUIET:-false}" == true ]] || echo "$@"; }
error() { echo "$@" >&2; }

# --- Argument parsing ---

FUNCTION_NAME=""
CLASS_NAME=""
ARTIFACT_ID=""
QUIET=false
OPT_ENVIRONMENT_ID=""
OPT_CLOUD=""
OPT_REGION=""
OPT_COMPUTE_POOL_ID=""
OPT_DATABASE=""
OPT_CATALOG=""

usage="Usage: $0 --function <function-name> --class <class-name> --artifact-id <artifact-id> [--quiet] [--environment-id <id>] [--cloud <provider>] [--region <region>] [--compute-pool-id <id>] [--database <database>] [--catalog <catalog>]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --function)        FUNCTION_NAME="$2";       shift 2 ;;
    --class)           CLASS_NAME="$2";          shift 2 ;;
    --artifact-id)     ARTIFACT_ID="$2";         shift 2 ;;
    --quiet)           QUIET=true;               shift   ;;
    --environment-id)  OPT_ENVIRONMENT_ID="$2";  shift 2 ;;
    --cloud)           OPT_CLOUD="$2";           shift 2 ;;
    --region)          OPT_REGION="$2";          shift 2 ;;
    --compute-pool-id) OPT_COMPUTE_POOL_ID="$2"; shift 2 ;;
    --database)        OPT_DATABASE="$2";        shift 2 ;;
    --catalog)         OPT_CATALOG="$2";         shift 2 ;;
    *) error "Unknown parameter: $1"
       error "${usage}"
       exit 1 ;;
  esac
done

missing_args=0
for param in FUNCTION_NAME CLASS_NAME ARTIFACT_ID; do
  if [[ -z "${!param}" ]]; then
    error "Error: --$(echo "${param}" | tr '[:upper:]' '[:lower:]' | tr '_' '-') is required."
    missing_args=1
  fi
done
if [[ $missing_args -ne 0 ]]; then
  error "${usage}"
  exit 1
fi

# --- Parameter resolution (CLI params override env vars) ---

[[ -n "${OPT_ENVIRONMENT_ID}" ]]  && CONFLUENT_FLINK_ENVIRONMENT_ID="${OPT_ENVIRONMENT_ID}"
[[ -n "${OPT_CLOUD}" ]]           && CONFLUENT_FLINK_CLOUD_PROVIDER="${OPT_CLOUD}"
[[ -n "${OPT_REGION}" ]]          && CONFLUENT_FLINK_CLOUD_REGION="${OPT_REGION}"
[[ -n "${OPT_COMPUTE_POOL_ID}" ]] && CONFLUENT_FLINK_COMPUTE_POOL_ID="${OPT_COMPUTE_POOL_ID}"
[[ -n "${OPT_DATABASE}" ]]        && CONFLUENT_FLINK_DATABASE="${OPT_DATABASE}"
[[ -n "${OPT_CATALOG}" ]]         && CONFLUENT_FLINK_CATALOG="${OPT_CATALOG}"

# --- Environment variable validation ---

REQUIRED_VARS=(
  CONFLUENT_FLINK_ENVIRONMENT_ID
  CONFLUENT_FLINK_COMPUTE_POOL_ID
  CONFLUENT_FLINK_CLOUD_PROVIDER
  CONFLUENT_FLINK_CLOUD_REGION
  CONFLUENT_FLINK_DATABASE
)

missing=0
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    error "Error: required environment variable $var is not set."
    missing=1
  fi
done
[[ $missing -eq 0 ]] || exit 1

# --- Check artifact exists ---

log "Checking artifact '${ARTIFACT_ID}' in ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION} ..."
if ! confluent flink artifact describe "${ARTIFACT_ID}" \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --cloud "${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
    --region "${CONFLUENT_FLINK_CLOUD_REGION}" \
    > /dev/null 2>&1; then
  error "Error: artifact '${ARTIFACT_ID}' not found in ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION}."
  exit 1
fi

# --- Build SQL statement ---

SQL="CREATE FUNCTION \`${FUNCTION_NAME}\`"
SQL+=" AS '${CLASS_NAME}'"
SQL+=" USING JAR 'confluent-artifact://${ARTIFACT_ID}';"

# --- Build CLI flags ---

CLI_ARGS=(
  flink statement create
  --sql "${SQL}"
  --compute-pool "${CONFLUENT_FLINK_COMPUTE_POOL_ID}"
  --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}"
  --database "${CONFLUENT_FLINK_DATABASE}"
  --output json
  --wait
)

# --- Execute ---

log "Registering function '${FUNCTION_NAME}' (${CLASS_NAME}) using artifact ${ARTIFACT_ID} ..."
if [[ "${QUIET}" == true ]]; then
  output=$(confluent "${CLI_ARGS[@]}" 2>/dev/null)
else
  output=$(confluent "${CLI_ARGS[@]}")
fi

status=$(echo "${output}" | grep -o '"status": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')

if [[ "${status}" != "COMPLETED" ]]; then
  status_detail=$(echo "${output}" | grep -o '"status_detail": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
  error "${status}: ${status_detail}"
  exit 1
fi

echo "${status}"
