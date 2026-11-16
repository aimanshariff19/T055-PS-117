#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_ROOT/scripts/java-env.sh"
cd "$PROJECT_ROOT"
exec "$PROJECT_ROOT/mvnw" test "$@"
