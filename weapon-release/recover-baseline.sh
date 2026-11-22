#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
test "${GITHUB_REPOSITORY:-}" = WeepingAngeIs/z-UwU_dm
mkdir -p build/weapon-release
gh api repos/WeepingAngeIs/z-UwU_dm/actions/artifacts/14723910544 > build/weapon-release/baseline-artifact.json
python3 - <<'PY'
import json
from pathlib import Path
m = json.loads(Path('build/weapon-release/baseline-artifact.json').read_text())
assert m['id'] == 14723910544
assert m['workflow_run']['id'] == 44944742702
assert m['workflow_run']['head_sha'] == '1f489c5308b686a30672ec19b671a64534eb28a4'
assert m['expired'] is False
assert m['digest'] == 'sha256:0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f'
PY
gh api repos/WeepingAngeIs/z-UwU_dm/actions/artifacts/14723910544/zip > build/weapon-baseline.jar
printf '%s  %s\n' 0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f build/weapon-baseline.jar | sha256sum --check
echo 'UWU WEAPON BASELINE recovered exact integrated Playtest 9 artifact'
