#!/usr/bin/env bash
set -euo pipefail

# A running executable JAR must not be overwritten by subsequent Gradle builds.
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir/backend"
./gradlew bootJar
mapfile -t jars < <(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar')
if [[ ${#jars[@]} != 1 ]]; then
  echo "Expected one executable backend JAR in backend/build/libs" >&2
  exit 1
fi
runtime_dir="$(mktemp -d "${TMPDIR:-/tmp}/equitylens-backend.XXXXXXXX")"
trap 'rm -f -- "$runtime_dir/backend.jar"; rmdir -- "$runtime_dir"' EXIT
cp -- "${jars[0]}" "$runtime_dir/backend.jar"
java -jar "$runtime_dir/backend.jar" "$@"
