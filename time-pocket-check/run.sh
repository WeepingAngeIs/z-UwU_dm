#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
(cd build/pocket-input && sha256sum -c SHA256SUMS.txt && grep -Fx "$GITHUB_SHA" source.txt)
export UWU_JAR=$(realpath build/pocket-input/*-uwuzi-prototype.2.jar)
mkdir -p build/pocket-evidence build/pocket-deps "build/pocket-$POCKET_SIDE/config"
cp build/pocket-input/{source.txt,SHA256SUMS.txt} build/pocket-evidence/
bash weapon-release/bootstrap.sh
curl --retry 3 --retry-all-errors -fsSL 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' -o build/pocket-deps/lootr.jar
printf '%s  %s\n' 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e build/pocket-deps/lootr.jar | sha256sum --check
case "$POCKET_SIDE" in
  client)
    if ! command -v xvfb-run >/dev/null || ! command -v xauth >/dev/null || ! /usr/bin/python3 -c 'import numpy, PIL'; then
      sudo apt-get update -qq
      sudo apt-get install -y xvfb xauth libgl1-mesa-dri libopenal1 python3-numpy python3-pil
    fi
    printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:4\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nbobView:false\n' > build/pocket-client/options.txt
    printf 'earlyWindowControl = false\n' > build/pocket-client/config/fml.toml
    export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
    timeout 14m xvfb-run -a -s '-screen 0 1024x768x24' bash gradlew --no-daemon --max-workers=2 -I time-pocket-check/probe.gradle runPocketCheck 2>&1 | tee build/pocket-evidence/game.log
    grep -F 'TIME POCKET CLIENT PASS' build/pocket-evidence/game.log
    /usr/bin/python3 time-pocket-check/review.py build/pocket-evidence
    ;;
  server)
    printf 'eula=true\n' > build/pocket-server/eula.txt
    printf 'server-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nlevel-name=DisposableTimePocketServer\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\n' > build/pocket-server/server.properties
    for POCKET_RUN_STAGE in write restart; do
      export POCKET_RUN_STAGE
      timeout 13m bash gradlew --no-daemon --max-workers=2 -I time-pocket-check/probe.gradle runPocketCheck 2>&1 | tee -a build/pocket-evidence/game.log
    done
    grep -F 'UWUZI DEDICATED WRITE PASS' build/pocket-evidence/game.log
    grep -F 'UWUZI DEDICATED RESTART PASS' build/pocket-evidence/game.log
    python3 - <<'PY'
from pathlib import Path
import tomllib
for path in Path("build/pocket-server").rglob("uwudimension-server.toml"):
    with path.open("rb") as source:
        data = tomllib.load(source)
    if data.get("uwuziFreezeSeconds") == 7 and data.get("cupidBowCharmSeconds") == 9:
        print("UWUZI DISK SETTINGS PASS", path)
        break
else:
    raise AssertionError("Missing saved UwUzi and Cupid Bow durations")
PY
    ;;
  *) exit 2 ;;
esac
grep -F "UWU PACKAGED CANDIDATE PASS sha256=$UWU_EXPECTED_JAR_SHA256" build/pocket-evidence/game.log
(cd build/pocket-input && sha256sum -c SHA256SUMS.txt)
