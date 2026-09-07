#!/bin/bash

set -euo pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIRECTORY="$(cd "${SCRIPT_DIRECTORY}/../.." && pwd)"
readonly CYCLONEDX_CLI_VERSION="0.33.1"
readonly CYCLONEDX_CLI_SHA256="bfc8b2538da86fe239bc53658bbb63c1c8c510a293c1e6891aa5bea5d3c58746"
readonly CYCLONEDX_CLI_URL="https://github.com/CycloneDX/cyclonedx-cli/releases/download/v${CYCLONEDX_CLI_VERSION}/cyclonedx-linux-x64"
readonly KEMI_VERSION="20.1"
readonly KEMI_SBOM="${ROOT_DIRECTORY}/app-k9mail/build/reports/sbom/kemi-release.cdx.json"

readonly TOOL_DIRECTORY="$(mktemp -d)"
trap 'rm -rf "${TOOL_DIRECTORY}"' EXIT
readonly CYCLONEDX_CLI="${TOOL_DIRECTORY}/cyclonedx"

export PYTHONDONTWRITEBYTECODE=1

python3 "${SCRIPT_DIRECTORY}/validate_kemi_sbom.py" \
  --input "${KEMI_SBOM}" \
  --version "${KEMI_VERSION}"

curl --fail --location --silent --show-error "${CYCLONEDX_CLI_URL}" --output "${CYCLONEDX_CLI}"
echo "${CYCLONEDX_CLI_SHA256}  ${CYCLONEDX_CLI}" | sha256sum --check --status
chmod 0755 "${CYCLONEDX_CLI}"

"${CYCLONEDX_CLI}" validate \
  --input-file "${KEMI_SBOM}" \
  --input-format json \
  --input-version v1_7 \
  --fail-on-errors
