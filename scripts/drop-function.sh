#!/usr/bin/env bash
# drop-function.sh — Drop a Flink UDF from Confluent Cloud
#
# Usage:
#   drop-function.sh --function <function-name> --database <database> [--quiet]
#                    [--environment-id <id>]
#
# Required env vars (set by .secrets/credentials.sh), overridable via parameters:
#   CONFLUENT_FLINK_ENVIRONMENT_ID  (--environment-id)
#   CONFLUENT_FLINK_COMPUTE_POOL_ID
#
# Optional env vars:
#   CONFLUENT_FLINK_CATALOG  — passed to flink statement create if set

set -euo pipefail

log()   { [[ "${QUIET:-false}" == true ]] || echo "$@"; }
error() { echo "$@" >&2; }

# --- Argument parsing ---

FUNCTION_NAME=""
DATABASE=""
QUIET=false
OPT_ENVIRONMENT_ID=""

usage="Usage: $0 --function <function-name> --database <database> [--quiet] [--environment-id <id>]"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --function)       FUNCTION_NAME="$2";      shift 2 ;;
    --database)       DATABASE="$2";           shift 2 ;;
    --quiet)          QUIET=true;              shift   ;;
    --environment-id) OPT_ENVIRONMENT_ID="$2"; shift 2 ;;
    *) error "Unknown parameter: $1"
       error "${usage}"
       exit 1 ;;
  esac
done

missing_args=0
for param in FUNCTION_NAME DATABASE; do
  if [[ -z "${!param}" ]]; then
    error "Error: --${param,,} is required."
    missing_args=1
  fi
done
if [[ $missing_args -ne 0 ]]; then
  error "${usage}"
  exit 1
fi

# --- Parameter resolution (CLI params override env vars) ---

[[ -n "${OPT_ENVIRONMENT_ID}" ]] && CONFLUENT_FLINK_ENVIRONMENT_ID="${OPT_ENVIRONMENT_ID}"

# --- Environment variable validation ---

REQUIRED_VARS=(
  CONFLUENT_FLINK_ENVIRONMENT_ID
  CONFLUENT_FLINK_COMPUTE_POOL_ID
)

missing=0
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    error "Error: required environment variable $var is not set."
    missing=1
  fi
done
[[ $missing -eq 0 ]] || exit 1

# --- Build SQL statement ---

SQL="DROP FUNCTION \`${FUNCTION_NAME}\`;"

# --- Build CLI flags ---

CLI_ARGS=(
  flink statement create
  --sql "${SQL}"
  --compute-pool "${CONFLUENT_FLINK_COMPUTE_POOL_ID}"
  --environment "${CONFLUENT_FLINK_ENVIRONMENT_ID}"
  --database "${DATABASE}"
  --output json
  --wait
)

if [[ -n "${CONFLUENT_FLINK_CATALOG:-}" ]]; then
  CLI_ARGS+=(--catalog "${CONFLUENT_FLINK_CATALOG}")
fi

# --- Execute ---

log "Dropping function '${FUNCTION_NAME}' from database '${DATABASE}' ..."
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

if [[ "${QUIET}" == true ]]; then
  echo "${FUNCTION_NAME}"
else
  echo "${status}"
fi