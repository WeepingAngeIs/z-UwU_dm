#!/usr/bin/env bash
set -euo pipefail
case "${1:?Supply the focused runtime check}" in
  guardian) UWU_GUARDIAN_WARNING_CHECK=true bash lotus-client-check/run.sh ;;
  blossom) UWU_BLOSSOM_CLIENT_CHECK=true bash lotus-client-check/run.sh ;;
  cave) UWU_CAVE_CLIENT_CHECK=true bash lotus-client-check/run.sh ;;
  bow-and-book) UWU_CUPID_LANTERN_CHECK=true bash lotus-client-check/run.sh ;;
  shrine-client) UWU_SHRINE_CLIENT_CHECK=true bash lotus-client-check/run.sh ;;
  shrine-placement) UWU_SHRINE_ORDERS_ONLY=true bash shrine-check/run-server.sh "$UWU_JAR" ;;
  waterfalls) bash waterfall-check/run.sh "$UWU_JAR" ;;
  weapons)
    python3 weapon-audit/verify_geometry.py
    bash weapon-check/run.sh
    ;;
  dialogue-native) bash dialogue-check/run-client.sh without-reference ;;
  dialogue-compat) bash dialogue-check/run-client.sh with-reference ;;
  *) echo "Unknown check: $1" >&2; exit 2 ;;
esac
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check
