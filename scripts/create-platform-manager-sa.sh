#!/usr/bin/env bash
# create-platform-manager-sa.sh — Create a Confluent Cloud service account with Flink Platform Manager RBAC roles
#
# Usage:
#   create-platform-manager-sa.sh --name <sa-name> [--description '<description>']
#                                 [--environment-id <id>] [--kafka-cluster-id <id>]
#
# Required env vars, overridable via parameters:
#   CONFLUENT_FLINK_ENVIRONMENT_ID  (--environment-id)
#   CONFLUENT_KAFKA_CLUSTER_ID      (--kafka-cluster-id)

set -euo pipefail

log()   { echo "$@"; }
error() { echo "$@" >&2; }

# --- Argument parsing ---

SA_NAME=""
SA_DESCRIPTION=""
OPT_ENVIRONMENT_ID=""
OPT_KAFKA_CLUSTER_ID=""

usage="Usage: $0 --name <sa-name> [--description '<description>'] [--environment-id <id>] [--kafka-cluster-id <id>]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --name)             SA_NAME="$2";              shift 2 ;;
    --description)      SA_DESCRIPTION="$2";       shift 2 ;;
    --environment-id)   OPT_ENVIRONMENT_ID="$2";   shift 2 ;;
    --kafka-cluster-id) OPT_KAFKA_CLUSTER_ID="$2"; shift 2 ;;
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

# --- Environment variable validation ---

REQUIRED_VARS=(
  CONFLUENT_FLINK_ENVIRONMENT_ID
  CONFLUENT_KAFKA_CLUSTER_ID
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
: "${SA_DESCRIPTION:=Platform Manager service account for Flink}"

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

# --- Assign FlinkAdmin role ---

log "Assigning FlinkAdmin role to '${SA_ID}' on environment '${CONFLUENT_FLINK_ENVIRONMENT_ID}' ..."
if ! confluent iam rbac role-binding create \
    --principal "User:${SA_ID}" \
    --role FlinkAdmin \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" > /dev/null 2>&1; then
  error "Error: failed to assign FlinkAdmin role."
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

# --- Generate Cloud API key ---

api_key_extra_args=()
[[ -n "${USER_DESCRIPTION}" ]] && api_key_extra_args+=(--description "${USER_DESCRIPTION}")

log "Generating Cloud API key for '${SA_ID}' ..."
if ! output=$(confluent api-key create \
    --resource cloud \
    --service-account "${SA_ID}" \
    "${api_key_extra_args[@]+${api_key_extra_args[@]}}" \
    --output json 2>&1); then
  error "Error: failed to create Cloud API key."
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
log "Platform Manager service account created successfully."
log ""
log "TAKE NOTE OF THE SECRET BELOW. IT CANNOT BE RETRIEVED LATER."
log "  Service Account ID : ${SA_ID}"
log "  Cloud API Key      : ${API_KEY}"
log "  Cloud API Secret   : ${API_SECRET}"
