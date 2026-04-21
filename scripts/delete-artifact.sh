#!/usr/bin/env bash
# delete-artifact.sh — Delete a Flink artifact from Confluent Cloud
#
# Usage:
#   delete-artifact.sh (--artifact-id <id> | --artifact-name <name>) [--quiet]
#                      [--environment-id <id>] [--cloud <provider>] [--region <region>]
#
# Required env vars (set by .secrets/credentials.sh), overridable via parameters:
#   CONFLUENT_FLINK_ENVIRONMENT_ID  (--environment-id)
#   CONFLUENT_FLINK_CLOUD_PROVIDER  (--cloud)
#   CONFLUENT_FLINK_CLOUD_REGION    (--region)

set -euo pipefail

log()   { [[ "${QUIET:-false}" == true ]] || echo "$@"; }
error() { echo "$@" >&2; }

# --- Argument parsing ---

ARTIFACT_ID=""
ARTIFACT_NAME=""
QUIET=false
OPT_ENVIRONMENT_ID=""
OPT_CLOUD=""
OPT_REGION=""

usage="Usage: $0 (--artifact-id <id> | --artifact-name <name>) [--quiet] [--environment-id <id>] [--cloud <provider>] [--region <region>]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --artifact-id)    ARTIFACT_ID="$2";          shift 2 ;;
    --artifact-name)  ARTIFACT_NAME="$2";        shift 2 ;;
    --quiet)          QUIET=true;                shift   ;;
    --environment-id) OPT_ENVIRONMENT_ID="$2";   shift 2 ;;
    --cloud)          OPT_CLOUD="$2";            shift 2 ;;
    --region)         OPT_REGION="$2";           shift 2 ;;
    *) error "Unknown parameter: $1"
       error "${usage}"
       exit 1 ;;
  esac
done

if [[ -z "${ARTIFACT_ID}" && -z "${ARTIFACT_NAME}" ]]; then
  error "Error: either --artifact-id or --artifact-name is required."
  error "${usage}"
  exit 1
fi

if [[ -n "${ARTIFACT_ID}" && -n "${ARTIFACT_NAME}" ]]; then
  error "Error: --artifact-id and --artifact-name are mutually exclusive."
  error "${usage}"
  exit 1
fi

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

# --- Resolve artifact ID from name if needed ---

if [[ -n "${ARTIFACT_NAME}" ]]; then
  log "Looking up artifact '${ARTIFACT_NAME}' in ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION} ..."
  list_output=$(confluent flink artifact list \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --cloud "${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
    --region "${CONFLUENT_FLINK_CLOUD_REGION}" \
    --output json 2>/dev/null)

  # Walk the JSON line by line: track the most recent "id" value and match it
  # to the object whose "name" equals ARTIFACT_NAME (id field precedes name in the response)
  current_id=""
  while IFS= read -r line; do
    if [[ "${line}" =~ \"id\":\ *\"([^\"]+)\" ]]; then
      current_id="${BASH_REMATCH[1]}"
    fi
    if [[ "${line}" =~ \"name\":\ *\"${ARTIFACT_NAME}\" ]]; then
      ARTIFACT_ID="${current_id}"
      break
    fi
  done <<< "${list_output}"

  if [[ -z "${ARTIFACT_ID}" ]]; then
    error "Error: no artifact named '${ARTIFACT_NAME}' found in ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION}."
    exit 1
  fi
  log "Found artifact '${ARTIFACT_NAME}' with ID '${ARTIFACT_ID}'."
fi

# --- Delete ---

log "Deleting artifact '${ARTIFACT_ID}' from ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION} ..."
if ! cli_output=$(confluent flink artifact delete "${ARTIFACT_ID}" \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --cloud "${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
    --region "${CONFLUENT_FLINK_CLOUD_REGION}" \
    --force 2>&1); then
  error "$(echo "${cli_output}" | head -1)"
  exit 1
fi

echo "${ARTIFACT_ID} DELETED"