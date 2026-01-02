#!/bin/bash
set -e

run_test() {
  local test_name=$1
  local file_path=$2

  echo "Function: ${test_name}, File: ${file_path}"

  case ${test_name} in
    "as-02-unwrap")
      as-02-unwrap "${file_path}" > /dev/null
      ;;
    "ffmpeg")
      ffmpeg -loglevel error -i "${file_path}" -c copy -f null - > /dev/null
      ;;
    "mxf2raw")
      mxf2raw --log-level 2 "${file_path}" > /dev/null
      ;;
    *)
      echo "Unknown test: ${test_name}" >&2
      return 1
      ;;
  esac
}

# Define files to test along with the format:
# "/path/to/file|exclusion1,exclusion2,..."
FILES_TO_TEST=(
  "/workspace/library/target/test-output/testVBE.mxf"
  "/workspace/library/target/test-output/testCBE.mxf"
  "/workspace/library/target/test-output/testClipVBE.mxf|ffmpeg,mxf2raw"
  "/workspace/library/target/test-output/testPHDR.mxf"
)

for item in "${FILES_TO_TEST[@]}"; do
  file="${item%%|*}"
  exclusions=""
  if [[ "$item" == *"|"* ]]; then
    exclusions="${item#*|}"
  fi

  if [[ "$exclusions" != *"as-02"* ]]; then
    run_test "as-02-unwrap" "${file}"
  fi

  if [[ "$exclusions" != *"ffmpeg"* ]]; then
    run_test "ffmpeg" "${file}"
  fi

  if [[ "$exclusions" != *"mxf2raw"* ]]; then
    run_test "mxf2raw" "${file}"
  fi
done