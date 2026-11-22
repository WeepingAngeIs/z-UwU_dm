#!/usr/bin/env bash
set -euo pipefail
test "$GITHUB_ACTIONS" = true
mkdir -p build/village-input
products=(build/guardian-input/*-guardians.2.jar)
if [[ -f build/guardian-input/candidate.jar ]]; then products=(build/guardian-input/candidate.jar); fi
test -f "${products[0]}"
cp "${products[0]}" build/village-input/candidate.jar
cp build/guardian-input/village-size-probe.jar build/village-input/probe.jar
(cd build/village-input && sha256sum candidate.jar probe.jar > checksums.txt)
export JAVA_TOOL_OPTIONS="-Duwu.check.guardianFactions=true"
bash village-size-check/run.sh 8675309
python3 - <<'PY'
from pathlib import Path
import json
rows = json.loads(Path("build/village-evidence/layouts-8675309.json").read_text())
assert {(r["faction"], r["actualHouses"]) for r in rows} == {("Dusk",6),("Petal",8),("Dusk",11)}
assert all(r["guardianTranslationKey"] == ("entity.uwudimension.dusk_guardian" if r["faction"] == "Dusk" else "entity.uwudimension.blossom_warden") for r in rows)
print("Verified three naturally generated courts, their faction guardians, residents and actual house footprints.")
PY
