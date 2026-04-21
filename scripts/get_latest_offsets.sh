#!/usr/bin/env bash
# get_latest_offsets.sh — Get the latest offsets from a stopped Flink statement
#
# Usage:
#   get_latest_offsets.sh --statement-name <name> --table <table-name> [--quiet]
#                         [--environment-id <id>] [--cloud <provider>] [--region <region>]
#
# Required env vars (set by .secrets/credentials.sh), overridable via parameters:
#   CONFLUENT_FLINK_ENVIRONMENT_ID  (--environment-id)
#   CONFLUENT_FLINK_CLOUD_PROVIDER  (--cloud)
#   CONFLUENT_FLINK_CLOUD_REGION    (--region)
#
# Requires: jq

set -euo pipefail

log()   { [[ "${QUIET:-false}" == true ]] || echo "$@"; }
error() { echo "$@" >&2; }

# --- Argument parsing ---

STATEMENT_NAME=""
TABLE_NAME=""
QUIET=false
OPT_ENVIRONMENT_ID=""
OPT_CLOUD=""
OPT_REGION=""

usage="Usage: $0 --statement-name <name> --table <table-name> [--quiet] [--environment-id <id>] [--cloud <provider>] [--region <region>]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --statement-name) STATEMENT_NAME="$2";      shift 2 ;;
    --table)          TABLE_NAME="$2";           shift 2 ;;
    --quiet)          QUIET=true;                shift   ;;
    --environment-id) OPT_ENVIRONMENT_ID="$2";   shift 2 ;;
    --cloud)          OPT_CLOUD="$2";            shift 2 ;;
    --region)         OPT_REGION="$2";           shift 2 ;;
    *) error "Unknown parameter: $1"
       error "${usage}"
       exit 1 ;;
  esac
done

missing_args=0
for param in STATEMENT_NAME TABLE_NAME; do
  if [[ -z "${!param}" ]]; then
    error "Error: --$(echo "${param}" | tr '[:upper:]' '[:lower:]' | tr '_' '-') is required."
    missing_args=1
  fi
done
if [[ $missing_args -ne 0 ]]; then
  error "${usage}"
  exit 1
fi

# --- Prerequisite check ---

if ! command -v jq &>/dev/null; then
  error "Error: jq is required but not installed."
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

# --- Describe statement ---

log "Describing statement '${STATEMENT_NAME}' in ${CONFLUENT_FLINK_CLOUD_PROVIDER}/${CONFLUENT_FLINK_CLOUD_REGION} ..."
output=$(confluent flink statement describe "${STATEMENT_NAME}" \
  --cloud "${CONFLUENT_FLINK_CLOUD_PROVIDER}" \
  --region "${CONFLUENT_FLINK_CLOUD_REGION}" \
  --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}" \
  --output json)

# --- Check statement is stopped ---

status=$(echo "${output}" | jq -r '.status')

if [[ "${status}" != "STOPPED" ]]; then
  error "Error: statement '${STATEMENT_NAME}' is not stopped (current status: ${status})."
  error "Only stopped statements have reliable latest_offsets. Stop the statement first."
  exit 1
fi

# --- Extract offsets ---

offsets_json=$(echo "${output}" | jq -r '.latest_offsets')

if [[ "${offsets_json}" == "null" || "${offsets_json}" == "{}" ]]; then
  error "Error: no latest_offsets found for statement '${STATEMENT_NAME}'."
  exit 1
fi

offsets=$(echo "${offsets_json}" | jq -r --arg t "${TABLE_NAME}" '.[$t] // empty')

if [[ -z "${offsets}" ]]; then
  available=$(echo "${offsets_json}" | jq -r 'keys | join(", ")')
  error "Error: table '${TABLE_NAME}' not found in latest_offsets."
  error "Available tables: ${available}"
  exit 1
fi

# --- Output ---

log "Statement: ${STATEMENT_NAME}"
log "Table:     ${TABLE_NAME}"
log "Offsets:"
echo "${offsets}"
