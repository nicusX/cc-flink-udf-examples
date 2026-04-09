#!/usr/bin/env bash
# upload-artifact.sh — Upload a JAR artifact to Confluent Cloud Flink
#
# Usage:
#   upload-artifact.sh --path <path-to-jar> [--description "<description>"] [--quiet]
#
# Required env vars (set by .secrets/credentials.sh):
#   CONFLUENT_FLINK_ENVIRONMENT_ID
#   CONFLUENT_FLINK_CLOUD_PROVIDER
#   CONFLUENT_FLINK_CLOUD_REGION

set -euo pipefail

log()   { [[ "${QUIET:-false}" == true ]] || echo "$@"; }
error() { echo "$@" >&2; }

# --- Argument parsing ---

ARTIFACT_FILE=""
ARTIFACT_DESCRIPTION=""
QUIET=false

usage="Usage: $0 --path <path-to-jar> [--description \"<description>\"] [--quiet]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --path)        ARTIFACT_FILE="$2";        shift 2 ;;
    --description) ARTIFACT_DESCRIPTION="$2"; shift 2 ;;
    --quiet)       QUIET=true;                shift   ;;
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
  "${extra_args[@]}" \
  --output json)

artifact_id=$(echo "${output}" | grep -o '"id": *"[^"]*"' | head -1 | sed 's/.*"\([^"]*\)"$/\1/')
log "Artifact ID:"
echo "${artifact_id}"
