#!/bin/bash
#
# Compatibility tests against third-party MXF tools (asdcplib, bmx, ffmpeg).
#
# Usage: compat.sh <regmxflib-output-dir> <third-party-output-dir>
#
# Runs in the Docker image defined in /Dockerfile:
#
# 1. Checks that the third-party tools can read the files produced by regmxflib
#    in <regmxflib-output-dir>, i.e. the files written by `mvn test`
# 2. Generates files with the third-party tools in <third-party-output-dir>,
#    which ReadSmokeTest then reads using regmxflib (on the host, since
#    the Docker image does not include a JDK)
#
set -e

if [ $# -ne 2 ]; then
  echo "Usage: $0 <regmxflib-output-dir> <third-party-output-dir>" >&2
  exit 1
fi

REGMXFLIB_DIR=$1
OUT_DIR=$2

#
# Part 1: read files produced by regmxflib using third-party tools
#

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
  "${REGMXFLIB_DIR}/testVBE.mxf"
  "${REGMXFLIB_DIR}/testCBE.mxf"
  "${REGMXFLIB_DIR}/testClipVBE.mxf|ffmpeg,mxf2raw"
  "${REGMXFLIB_DIR}/testPHDR.mxf"
  "${REGMXFLIB_DIR}/testTimedEvents.mxf|as-02,ffmpeg"
  "${REGMXFLIB_DIR}/testADMAudioTrackFile.mxf|as-02,ffmpeg"
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

#
# Part 2: generate files using third-party tools, for reading by regmxflib
#

SRC_DIR=$(mktemp -d)
trap 'rm -rf "${SRC_DIR}"' EXIT

rm -rf "${OUT_DIR}"
mkdir -p "${OUT_DIR}"

FF="ffmpeg -loglevel error -y"
DURATION=1

generate() {
  local name=$1
  shift
  echo "Generating: ${name}"
  "$@"
}

# synthetic sources

${FF} -f lavfi -i "sine=frequency=1000:sample_rate=48000:duration=${DURATION}" \
  -ac 2 -c:a pcm_s24le "${SRC_DIR}/audio.wav"

${FF} -f lavfi -i "testsrc=size=720x576:rate=25:duration=${DURATION}" \
  -pix_fmt uyvy422 -f rawvideo "${SRC_DIR}/unc-576.yuv"

${FF} -f lavfi -i "testsrc=size=720x576:rate=25:duration=${DURATION}" \
  -pix_fmt yuv420p -c:v dvvideo -f dv "${SRC_DIR}/dv-576.dv"

mkdir "${SRC_DIR}/j2c"
${FF} -f lavfi -i "testsrc=size=1920x1080:rate=24:duration=${DURATION}" \
  -pix_fmt rgb48le -c:v libopenjpeg -format j2k -f image2 "${SRC_DIR}/j2c/%06d.j2c"

cat > "${SRC_DIR}/tt.xml" <<'TTML'
<?xml version="1.0" encoding="UTF-8"?>
<tt xml:lang="en" xmlns="http://www.w3.org/ns/ttml"
  xmlns:ttp="http://www.w3.org/ns/ttml#parameter"
  ttp:profile="http://www.w3.org/ns/ttml/profile/imsc1/text">
  <body><div><p begin="0s" end="1s">Hello</p></div></body>
</tt>
TTML

# ffmpeg

generate ffmpeg-op1a-mpeg2-pcm.mxf \
  ${FF} -f lavfi -i "testsrc=size=720x576:rate=25:duration=${DURATION}" \
  -f lavfi -i "sine=sample_rate=48000:duration=${DURATION}" \
  -c:v mpeg2video -b:v 10M -c:a pcm_s16le -f mxf "${OUT_DIR}/ffmpeg-op1a-mpeg2-pcm.mxf"

generate ffmpeg-op1a-dv.mxf \
  ${FF} -i "${SRC_DIR}/dv-576.dv" -c copy -f mxf "${OUT_DIR}/ffmpeg-op1a-dv.mxf"

generate ffmpeg-opatom-dv.mxf \
  ${FF} -i "${SRC_DIR}/dv-576.dv" -c copy -f mxf_opatom "${OUT_DIR}/ffmpeg-opatom-dv.mxf"

generate ffmpeg-opatom-pcm.mxf \
  ${FF} -f lavfi -i "sine=sample_rate=48000:duration=${DURATION}" \
  -c:a pcm_s24le -mxf_audio_edit_rate 25 -f mxf_opatom "${OUT_DIR}/ffmpeg-opatom-pcm.mxf"

# bmx

generate bmx-op1a-unc-pcm.mxf \
  raw2bmx -t op1a -o "${OUT_DIR}/bmx-op1a-unc-pcm.mxf" -f 25 \
  --unc "${SRC_DIR}/unc-576.yuv" --wave "${SRC_DIR}/audio.wav"

generate bmx-op1a-dv.mxf \
  raw2bmx -t op1a -o "${OUT_DIR}/bmx-op1a-dv.mxf" -f 25 --dv "${SRC_DIR}/dv-576.dv"

generate bmx-op1a-clip-pcm.mxf \
  raw2bmx -t op1a --clip-wrap -o "${OUT_DIR}/bmx-op1a-clip-pcm.mxf" -f 25 \
  --wave "${SRC_DIR}/audio.wav"

generate bmx-imf-pcm.mxf \
  raw2bmx -t imf -o "${OUT_DIR}/bmx-imf-pcm.mxf" -f 24 --wave "${SRC_DIR}/audio.wav"

generate "bmx-as02-*.mxf" \
  raw2bmx -t as02 -o "${SRC_DIR}/as02" -f 25 \
  --unc "${SRC_DIR}/unc-576.yuv" --wave "${SRC_DIR}/audio.wav"
for f in "${SRC_DIR}"/as02/media/*.mxf; do
  cp "$f" "${OUT_DIR}/bmx-as02-$(basename "$f")"
done

# asdcplib

generate asdcp-imf-pcm.mxf \
  as-02-wrap "${SRC_DIR}/audio.wav" "${OUT_DIR}/asdcp-imf-pcm.mxf"

generate asdcp-imf-j2c.mxf \
  as-02-wrap -R -D 16 "${SRC_DIR}/j2c" "${OUT_DIR}/asdcp-imf-j2c.mxf"

generate asdcp-imf-tt.mxf \
  as-02-wrap "${SRC_DIR}/tt.xml" "${OUT_DIR}/asdcp-imf-tt.mxf"

generate asdcp-dcp-j2c.mxf \
  asdcp-wrap -L "${SRC_DIR}/j2c" "${OUT_DIR}/asdcp-dcp-j2c.mxf"

generate asdcp-dcp-pcm.mxf \
  asdcp-wrap -L "${SRC_DIR}/audio.wav" "${OUT_DIR}/asdcp-dcp-pcm.mxf"

ls -l "${OUT_DIR}"
