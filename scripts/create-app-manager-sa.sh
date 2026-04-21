#!/usr/bin/env bash
# create-app-manager-sa.sh — Create a Confluent Cloud service account with Flink App Manager RBAC roles
#
# Usage:
#   create-app-manager-sa.sh --name <sa-name> [--description '<description>']
#                            [--environment-id <id>] [--kafka-cluster-id <id>]
#                            [--cloud <provider>] [--region <region>]
#
# Required env vars, overridable via parameters:
#   CONFLUENT_FLINK_ENVIRONMENT_ID  (--environment-id)
#   CONFLUENT_KAFKA_CLUSTER_ID      (--kafka-cluster-id)
#   CONFLUENT_FLINK_CLOUD_PROVIDER  (--cloud)
#   CONFLUENT_FLINK_CLOUD_REGION    (--region)

set -euo pipefail

log()   { echo "$@"; }
error() { echo "$@" >&2; }

# --- Argument parsing ---

SA_NAME=""
SA_DESCRIPTION=""
OPT_ENVIRONMENT_ID=""
OPT_KAFKA_CLUSTER_ID=""
OPT_CLOUD=""
OPT_REGION=""

usage="Usage: $0 --name <sa-name> [--description '<description>'] [--environment-id <id>] [--kafka-cluster-id <id>] [--cloud <provider>] [--region <region>]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)             SA_NAME="$2";              shift 2 ;;
    --description)      SA_DESCRIPTION="$2";       shift 2 ;;
    --environment-id)   OPT_ENVIRONMENT_ID="$2";   shift 2 ;;
    --kafka-cluster-id) OPT_KAFKA_CLUSTER_ID="$2"; shift 2 ;;
    --cloud)            OPT_CLOUD="$2";            shift 2 ;;
    --region)           OPT_REGION="$2";           shift 2 ;;
    *) error "Unknown parameter: $1"
       error "${usage}"
       exit 1 ;;
  esac
done

if [[ -z "${SA_NAME}" ]]; then
  error "Error: --name is required."
  error "${usage}"
  exit 1
fi

# --- Parameter resolution (CLI params override env vars) ---

[[ -n "${OPT_ENVIRONMENT_ID}" ]]   && CONFLUENT_FLINK_ENVIRONMENT_ID="${OPT_ENVIRONMENT_ID}"
[[ -n "${OPT_KAFKA_CLUSTER_ID}" ]] && CONFLUENT_KAFKA_CLUSTER_ID="${OPT_KAFKA_CLUSTER_ID}"
[[ -n "${OPT_CLOUD}" ]]            && CONFLUENT_FLINK_CLOUD_PROVIDER="${OPT_CLOUD}"
[[ -n "${OPT_REGION}" ]]           && CONFLUENT_FLINK_CLOUD_REGION="${OPT_REGION}"

# --- Environment variable validation ---

REQUIRED_VARS=(
  CONFLUENT_FLINK_ENVIRONMENT_ID
  CONFLUENT_KAFKA_CLUSTER_ID
  CONFLUENT_FLINK_CLOUD_PROVIDER
  CONFLUENT_FLINK_CLOUD_REGION
)

missing=0
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    error "Error: required environment variable $var is not set."
    missing=1
  fi
done
[[ $missing -eq 0 ]] || exit 1

# --- Create service account ---

USER_DESCRIPTION="${SA_DESCRIPTION}"
: "${SA_DESCRIPTION:=App Manager service account for Flink}"

log "Creating service account '${SA_NAME}' ..."
if ! output=$(confluent iam service-account create "${SA_NAME}" \
    --description "${SA_DESCRIPTION}" \
    --output json 2>&1); then
  error "Error: failed to create service account."
  error "$(echo "${output}" | head -1)"
  exit 1
fi

SA_ID=$(echo "${output}" | grep -o '"id": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
if [[ -z "${SA_ID}" ]]; then
  error "Error: could not parse service account ID from output."
  exit 1
fi

log "Service account created: ${SA_ID}"

# --- Discover Schema Registry cluster ---

log "Discovering Schema Registry cluster for environment '${CONFLUENT_FLINK_ENVIRONMENT_ID}' ..."
if ! output=$(confluent schema-registry cluster describe \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --output json 2>&1); then
  error "Error: failed to describe Schema Registry cluster."
  error "$(echo "${output}" | head -1)"
  exit 1
fi

SR_CLUSTER_ID=$(echo "${output}" | grep -o '"cluster": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
if [[ -z "${SR_CLUSTER_ID}" ]]; then
  error "Error: could not parse Schema Registry cluster ID from output."
  exit 1
fi

log "Schema Registry cluster: ${SR_CLUSTER_ID}"

# --- Assign FlinkDeveloper role ---

log "Assigning FlinkDeveloper role to '${SA_ID}' on environment '${CONFLUENT_FLINK_ENVIRONMENT_ID}' ..."
if ! confluent iam rbac role-binding create \
    --principal "User:${SA_ID}" \
    --role FlinkDeveloper \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" > /dev/null 2>&1; then
  error "Error: failed to assign FlinkDeveloper role."
  exit 1
fi

# --- Assign ResourceOwner on Kafka topics ---

log "Assigning ResourceOwner on all topics to '${SA_ID}' ..."
if ! confluent iam rbac role-binding create \
    --principal "User:${SA_ID}" \
    --role ResourceOwner \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --cloud-cluster "${CONFLUENT_KAFKA_CLUSTER_ID}" \
    --kafka-cluster "${CONFLUENT_KAFKA_CLUSTER_ID}" \
    --resource "Topic:" \
    --prefix > /dev/null 2>&1; then
  error "Error: failed to assign ResourceOwner on topics."
  exit 1
fi

# --- Assign ResourceOwner on Flink transactional IDs ---

log "Assigning ResourceOwner on TransactionalId:_confluent-flink_ to '${SA_ID}' ..."
if ! confluent iam rbac role-binding create \
    --principal "User:${SA_ID}" \
    --role ResourceOwner \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --cloud-cluster "${CONFLUENT_KAFKA_CLUSTER_ID}" \
    --kafka-cluster "${CONFLUENT_KAFKA_CLUSTER_ID}" \
    --resource "Transactional-Id:_confluent-flink_" \
    --prefix > /dev/null 2>&1; then
  error "Error: failed to assign ResourceOwner on transactional IDs."
  exit 1
fi

# --- Assign DeveloperWrite on Schema Registry subjects ---

log "Assigning DeveloperWrite on all subjects to '${SA_ID}' ..."
if ! confluent iam rbac role-binding create \
    --principal "User:${SA_ID}" \
    --role DeveloperWrite \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --schema-registry-cluster "${SR_CLUSTER_ID}" \
    --resource "Subject:" \
    --prefix > /dev/null 2>&1; then
  error "Error: failed to assign DeveloperWrite on Schema Registry subjects."
  exit 1
fi

# --- Generate Flink API key ---

api_key_extra_args=()
[[ -n "${USER_DESCRIPTION}" ]] && api_key_extra_args+=(--description "${USER_DESCRIPTION}")

log "Generating Flink API key for '${SA_ID}' ..."
if ! output=$(confluent api-key create \
    --resource flink \
    --service-account "${SA_ID}" \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --cloud "${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
    --region "${CONFLUENT_FLINK_CLOUD_REGION}" \
    "${api_key_extra_args[@]+${api_key_extra_args[@]}}" \
    --output json 2>&1); then
  error "Error: failed to create Flink API key."
  error "$(echo "${output}" | head -1)"
  exit 1
fi

API_KEY=$(echo "${output}" | grep -o '"api_key": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
API_SECRET=$(echo "${output}" | grep -o '"api_secret": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')

if [[ -z "${API_KEY}" || -z "${API_SECRET}" ]]; then
  error "Error: could not parse API key/secret from output."
  exit 1
fi

# --- Output ---

log ""
log "App Manager service account created successfully."
log ""
log "TAKE NOTE OF THE SECRET BELOW. IT CANNOT BE RETRIEVED LATER."
log "  Service Account ID : ${SA_ID}"
log "  Flink API Key      : ${API_KEY}"
log "  Flink API Secret   : ${API_SECRET}"