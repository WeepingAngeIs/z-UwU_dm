#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
test -f "${UWU_JAR:?Supply the sealed combined JAR}"
printf '%s  %s\n' "${UWU_EXPECTED_JAR_SHA256:?Supply the sealed digest}" "$UWU_JAR" | sha256sum --check
bash weapon-release/bootstrap.sh
bash gradlew --no-daemon --max-workers=2 -I breakfast-check/probe.gradle breakfastProbeJar
bash breakfast-check/run.sh
python3 combined-check/candidate.py check
