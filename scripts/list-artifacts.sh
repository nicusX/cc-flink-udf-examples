#!/usr/bin/env bash
# list-artifacts.sh — List all Flink artifacts in the specified environment and cloud region
#
# Usage:
#   list-artifacts.sh [--environment-id <id>] [--cloud <provider>] [--region <region>]
#
# Required env vars, overridable via parameters:
#   CONFLUENT_FLINK_ENVIRONMENT_ID  (--environment-id)
#   CONFLUENT_FLINK_CLOUD_PROVIDER  (--cloud)
#   CONFLUENT_FLINK_CLOUD_REGION    (--region)

set -euo pipefail

log()   { echo "$@"; }
error() { echo "$@" >&2; }

# --- Argument parsing ---

OPT_ENVIRONMENT_ID=""
OPT_CLOUD=""
OPT_REGION=""

usage="Usage: $0 [--environment-id <id>] [--cloud <provider>] [--region <region>]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment-id) OPT_ENVIRONMENT_ID="$2"; shift 2 ;;
    --cloud)          OPT_CLOUD="$2";          shift 2 ;;
    --region)         OPT_REGION="$2";         shift 2 ;;
    *) error "Unknown parameter: $1"
       error "${usage}"
       exit 1 ;;
  esac
done

# --- Parameter resolution (CLI params override env vars) ---

[[ -n "${OPT_ENVIRONMENT_ID}" ]] && CONFLUENT_FLINK_ENVIRONMENT_ID="${OPT_ENVIRONMENT_ID}"
[[ -n "${OPT_CLOUD}" ]]          && CONFLUENT_FLINK_CLOUD_PROVIDER="${OPT_CLOUD}"
[[ -n "${OPT_REGION}" ]]         && CONFLUENT_FLINK_CLOUD_REGION="${OPT_REGION}"

# --- Environment variable validation ---

REQUIRED_VARS=(
  CONFLUENT_FLINK_ENVIRONMENT_ID
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

# --- List artifacts ---

confluent flink artifact list \
  --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
  --cloud "${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
  --region "${CONFLUENT_FLINK_CLOUD_REGION}"