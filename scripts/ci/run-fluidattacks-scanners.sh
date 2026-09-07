#!/bin/bash

function fail() {
  echo "Error: $*"
  exit 1
}

# Check if tools are installed
command -v docker &> /dev/null || fail "Docker is not installed"

# Default values
debug=false

# Reviewed on 2026-08-15. Keep scanner updates explicit and independently reviewable.
IMAGE_SAST="fluidattacks/sast@sha256:265860ce69205b4c69e07354bc592d7c4f64a260529c3c6e89c51e101ee9284c"
IMAGE_SCA="fluidattacks/sca@sha256:2777cc84ee9d4253398da56d587bb2abd002fdd054117aa96f148621ac6e022e"

# Parse command-line arguments
for arg in "$@"; do
  case $arg in
    --debug)
      debug=true
      shift
      ;;
    *)
      fail "Unknown argument: $arg"
      ;;
  esac
done

if [ "$debug" = true ]; then
  docker run --rm -v "$(pwd)":/repo -it "$IMAGE_SAST" /bin/bash
  docker run --rm -v "$(pwd)":/repo -it "$IMAGE_SCA" /bin/bash
  exit
fi

docker run --rm -v "$(pwd)":/repo "$IMAGE_SAST" \
  sast scan /repo/config/fluidattacks/config-sast.yaml
docker run --rm -v "$(pwd)":/repo "$IMAGE_SCA" \
  sca scan /repo/config/fluidattacks/config-sca.yaml
