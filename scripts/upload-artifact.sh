#!/usr/bin/env bash
# upload-artifact.sh — Upload a JAR artifact to Confluent Cloud Flink
#
# Usage:
#   upload-artifact.sh --path <path-to-jar> [--description "<description>"] [--quiet]
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

ARTIFACT_FILE=""
ARTIFACT_DESCRIPTION=""
QUIET=false
OPT_ENVIRONMENT_ID=""
OPT_CLOUD=""
OPT_REGION=""

usage="Usage: $0 --path <path-to-jar> [--description \"<description>\"] [--quiet] [--environment-id <id>] [--cloud <provider>] [--region <region>]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --path)           ARTIFACT_FILE="$2";        shift 2 ;;
    --description)    ARTIFACT_DESCRIPTION="$2"; shift 2 ;;
    --quiet)          QUIET=true;                shift   ;;
    --environment-id) OPT_ENVIRONMENT_ID="$2";   shift 2 ;;
    --cloud)          OPT_CLOUD="$2";            shift 2 ;;
    --region)         OPT_REGION="$2";           shift 2 ;;
    *) error "Unknown parameter: $1"
       error "${usage}"
       exit 1 ;;
  esac
done

if [[ -z "${ARTIFACT_FILE}" ]]; then
  error "Error: --path is required."
  error "${usage}"
  exit 1
fi

if [[ ! -f "${ARTIFACT_FILE}" ]]; then
  error "Error: file not found: ${ARTIFACT_FILE}"
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

# --- Check for name collision ---

ARTIFACT_NAME="$(basename "${ARTIFACT_FILE}" .jar)"

log "Checking for existing artifact '${ARTIFACT_NAME}' in ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION} ..."
if confluent flink artifact list \
    --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
    --cloud "${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
    --region "${CONFLUENT_FLINK_CLOUD_REGION}" \
    --output json 2>/dev/null \
    | grep -q "\"${ARTIFACT_NAME}\""; then
  error "Error: artifact '${ARTIFACT_NAME}' already exists in ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION}."
  exit 1
fi

# --- Upload ---

log "Uploading '${ARTIFACT_FILE}' as artifact '${ARTIFACT_NAME}' to ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION} ..."
extra_args=()
[[ -n "${ARTIFACT_DESCRIPTION}" ]] && extra_args+=(--description "${ARTIFACT_DESCRIPTION}")

output=$(confluent flink artifact create "${ARTIFACT_NAME}" \
  --artifact-file "${ARTIFACT_FILE}" \
  --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
  --cloud "${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
  --region "${CONFLUENT_FLINK_CLOUD_REGION}" \
  "${extra_args[@]+${extra_args[@]}}" \
  --output json)

artifact_id=$(echo "${output}" | grep -o '"id": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
log "Artifact ID:"
echo "${artifact_id}"
