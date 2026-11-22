#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
case "${1:?Supply player or observer}" in player|observer) ;; *) exit 2 ;; esac
export UWU_GUARDIAN_VIEW="$1" UWU_GUARDIAN_WARNING_CHECK=true
export UWU_GUARDIAN_EVIDENCE="$PWD/build/guardian-evidence/$1"
mkdir -p "$UWU_GUARDIAN_EVIDENCE"
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum -c
bash lotus-client-check/run.sh
cp build/lotus-client.log "$UWU_GUARDIAN_EVIDENCE/run.log"
/usr/bin/python3 guardian-encounter-check/film.py "$UWU_GUARDIAN_EVIDENCE"
printf '%s\n' "$GITHUB_SHA" > "$UWU_GUARDIAN_EVIDENCE/source.txt"
printf '%s\n' "$UWU_EXPECTED_JAR_SHA256" > "$UWU_GUARDIAN_EVIDENCE/candidate.sha256"
