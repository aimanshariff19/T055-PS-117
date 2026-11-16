#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$PROJECT_ROOT/scripts/java-env.sh"
cd "$PROJECT_ROOT"
JAR="$PROJECT_ROOT/target/T055-PS-117-1.0.0.jar"

if [[ ! -f "$JAR" ]] || [[ "$PROJECT_ROOT/pom.xml" -nt "$JAR" ]] \
    || find "$PROJECT_ROOT/src/main/java" -type f -newer "$JAR" -print -quit | grep -q .; then
    "$PROJECT_ROOT/mvnw" -q -DskipTests package
fi

exec java -cp "$JAR" com.ps117.api.ApiServerMain "$@"
