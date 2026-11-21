#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
test "${GITHUB_REPOSITORY:-}" = WeepingAngeIs/z-UwU_dm
uwu_baseline="$RUNNER_TEMP/uwu-integrated-playtest9.jar"
# The exact passing integrated candidate from run 44944742702, source 1f489c5.
# This artifact was uploaded without an outer ZIP archive.
gh api repos/WeepingAngeIs/z-UwU_dm/actions/artifacts/14723910544/zip > "$uwu_baseline"
printf '%s  %s\n' 0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f "$uwu_baseline" | sha256sum --check
