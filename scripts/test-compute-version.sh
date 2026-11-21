#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPUTE_SCRIPT="$SCRIPT_DIR/compute-version.sh"

assert_eq() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$expected" != "$actual" ]]; then
    echo "FAIL [$label]: expected '$expected', got '$actual'" >&2
    exit 1
  fi
}

run_case() {
  local label="$1"
  local run_number="$2"
  local ref="$3"
  local ref_name="$4"
  local offset="$5"
  local expected_code="$6"
  local expected_name="$7"

  local env_file
  env_file="$(mktemp)"
  local out
  out="$(
    env -u GITHUB_RUN_NUMBER -u GITHUB_REF -u GITHUB_REF_NAME \
      RUN_NUMBER="$run_number" \
      REF="$ref" \
      REF_NAME="$ref_name" \
      VERSION_CODE_OFFSET="$offset" \
      GITHUB_ENV="$env_file" \
      bash "$COMPUTE_SCRIPT"
  )"

  local actual_code actual_name env_code env_name
  actual_code="$(printf '%s\n' "$out" | sed -n 's/^VERSION_CODE=//p')"
  actual_name="$(printf '%s\n' "$out" | sed -n 's/^VERSION_NAME=//p')"
  env_code="$(sed -n 's/^VERSION_CODE=//p' "$env_file")"
  env_name="$(sed -n 's/^VERSION_NAME=//p' "$env_file")"
  rm -f "$env_file"

  assert_eq "$expected_code" "$actual_code" "$label (stdout VERSION_CODE)"
  assert_eq "$expected_name" "$actual_name" "$label (stdout VERSION_NAME)"
  assert_eq "$expected_code" "$env_code" "$label (GITHUB_ENV VERSION_CODE)"
  assert_eq "$expected_name" "$env_name" "$label (GITHUB_ENV VERSION_NAME)"
  echo "PASS [$label]: VERSION_CODE=$actual_code VERSION_NAME=$actual_name"
}

run_fail_case() {
  local label="$1"
  local run_number="$2"
  local ref="$3"
  local ref_name="$4"
  local offset="$5"

  if env -u GITHUB_RUN_NUMBER -u GITHUB_REF -u GITHUB_REF_NAME -u GITHUB_ENV \
    RUN_NUMBER="$run_number" \
    REF="$ref" \
    REF_NAME="$ref_name" \
    VERSION_CODE_OFFSET="$offset" \
    bash "$COMPUTE_SCRIPT" >/dev/null 2>&1; then
    echo "FAIL [$label]: expected non-zero exit status, but succeeded" >&2
    exit 1
  fi
  echo "PASS [$label]: rejected invalid input as expected"
}

# Tag builds
run_case "tag v1.2" "15" "refs/tags/v1.2" "v1.2" "0" "15" "1.2"
run_case "tag v2.0.1-rc1 with offset" "7" "refs/tags/v2.0.1-rc1" "v2.0.1-rc1" "100" "107" "2.0.1-rc1"
run_case "tag fallback without REF_NAME" "9" "refs/tags/v1.2" "" "" "9" "1.2"

# Branch builds
run_case "main branch build" "42" "refs/heads/main" "main" "0" "42" "1.0.42"
run_case "branch build with empty offset" "8" "refs/heads/feat/ci" "feat/ci" "" "8" "1.0.8"

# Offset handling
run_case "branch build with positive offset" "5" "refs/heads/main" "main" "250" "255" "1.0.5"
run_case "branch build with negative offset above 1" "10" "refs/heads/main" "main" "-3" "7" "1.0.10"

# Non-numeric & invalid inputs
run_fail_case "empty RUN_NUMBER" "" "refs/heads/main" "main" "0"
run_fail_case "non-numeric RUN_NUMBER" "abc" "refs/heads/main" "main" "0"
run_fail_case "mixed RUN_NUMBER" "12a" "refs/heads/main" "main" "0"
run_fail_case "zero RUN_NUMBER" "0" "refs/heads/main" "main" "0"
run_fail_case "non-numeric offset" "10" "refs/heads/main" "main" "abc"
run_fail_case "decimal offset" "10" "refs/heads/main" "main" "1.5"
run_fail_case "offset yielding versionCode < 1" "5" "refs/heads/main" "main" "-5"
run_fail_case "empty version after v tag" "10" "refs/tags/v" "v" "0"

echo "All compute-version self-tests passed."
