#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.21.11}"
TARGET_DIR="docs/paper-javadocs-${VERSION}"

mkdir -p "${TARGET_DIR}"
cd "${TARGET_DIR}"

wget \
  --mirror \
  --convert-links \
  --adjust-extension \
  --page-requisites \
  --no-parent \
  "https://jd.papermc.io/paper/${VERSION}/"

echo "Javadocs salvati in: ${TARGET_DIR}/jd.papermc.io/paper/${VERSION}/index.html"
