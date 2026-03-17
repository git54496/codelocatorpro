#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
ROOT_DIR="${SCRIPT_DIR}"
ADAPTER_DIR="${ROOT_DIR}/adapter"
BIN="${ADAPTER_DIR}/build/install/grab/bin/grab"
DEFAULT_SOURCE_ROOT="${ROOT_DIR}/../TestApplication"

echo "Building local grab..."
(
  cd "${ADAPTER_DIR}"
  ./gradlew installDist --no-daemon
)

if [[ ! -x "${BIN}" ]]; then
  echo "Local grab binary not found: ${BIN}"
  exit 1
fi

if [[ -z "${CODELOCATOR_SOURCE_ROOT:-}" && -d "${DEFAULT_SOURCE_ROOT}" ]]; then
  export CODELOCATOR_SOURCE_ROOT="${DEFAULT_SOURCE_ROOT}"
fi

exec "${BIN}" "$@"
