#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -n "${NEXUS_MAVEN_URL:-}" ]]; then
  : "${NEXUS_USERNAME:?NEXUS_USERNAME es obligatorio al usar Nexus}"
  : "${NEXUS_PASSWORD:?NEXUS_PASSWORD es obligatorio al usar Nexus}"
  exec mvn -B -ntp -s ci/settings-nexus.xml clean verify "$@"
fi

if [[ -n "${NEXUS_USERNAME:-}" || -n "${NEXUS_PASSWORD:-}" ]]; then
  echo "NEXUS_MAVEN_URL es obligatorio al proporcionar credenciales de Nexus" >&2
  exit 2
fi
exec mvn -B -ntp clean verify "$@"
