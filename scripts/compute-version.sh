#!/usr/bin/env bash
set -euo pipefail

RAW_RUN_NUMBER="${RUN_NUMBER:-${GITHUB_RUN_NUMBER:-}}"
RAW_REF="${REF:-${GITHUB_REF:-}}"
RAW_REF_NAME="${REF_NAME:-${GITHUB_REF_NAME:-}}"
RAW_OFFSET="${VERSION_CODE_OFFSET:-0}"

if [[ -z "$RAW_OFFSET" ]]; then
  RAW_OFFSET="0"
fi

if [[ ! "$RAW_RUN_NUMBER" =~ ^[0-9]+$ ]]; then
  echo "Error: RUN_NUMBER must be a positive integer, got '$RAW_RUN_NUMBER'." >&2
  exit 1
fi

RUN_NUM_INT=$(( 10#$RAW_RUN_NUMBER ))
if (( RUN_NUM_INT < 1 )); then
  echo "Error: RUN_NUMBER must be >= 1, got '$RAW_RUN_NUMBER'." >&2
  exit 1
fi

if [[ ! "$RAW_OFFSET" =~ ^-?[0-9]+$ ]]; then
  echo "Error: VERSION_CODE_OFFSET must be an integer, got '$RAW_OFFSET'." >&2
  exit 1
fi

if [[ "$RAW_OFFSET" == -* ]]; then
  OFFSET_INT=$(( -10#${RAW_OFFSET#-} ))
else
  OFFSET_INT=$(( 10#$RAW_OFFSET ))
fi

VERSION_CODE=$(( RUN_NUM_INT + OFFSET_INT ))
if (( VERSION_CODE < 1 )); then
  echo "Error: computed VERSION_CODE ($VERSION_CODE) must be >= 1." >&2
  exit 1
fi

if [[ "$RAW_REF" == refs/tags/v* ]]; then
  TAG_NAME="${RAW_REF_NAME:-${RAW_REF#refs/tags/}}"
  VERSION_NAME="${TAG_NAME#v}"
  if [[ -z "$VERSION_NAME" ]]; then
    echo "Error: empty version name derived from tag '$TAG_NAME'." >&2
    exit 1
  fi
else
  VERSION_NAME="1.0.${RUN_NUM_INT}"
fi

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "VERSION_CODE=$VERSION_CODE"
    echo "VERSION_NAME=$VERSION_NAME"
  } >> "$GITHUB_ENV"
fi

echo "VERSION_CODE=$VERSION_CODE"
echo "VERSION_NAME=$VERSION_NAME"
