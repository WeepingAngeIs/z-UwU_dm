#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
case "${POCKET_SIDE:?Supply client or server}" in client|server) ;; *) exit 2 ;; esac
test -z "${UWUZI_PRIVATE_PACK:-}"
test -f "${UWU_JAR:?Supply the sealed combined candidate JAR}"
export UWU_JAR=$(realpath "$UWU_JAR")
uwu_out="build/combined-evidence/pocket-$POCKET_SIDE"
uwu_game_dir="build/pocket-$POCKET_SIDE"
uwu_deps=build/combined-deps
mkdir -p "$uwu_out" "$uwu_game_dir/config/bettercombat" "$uwu_game_dir/config/spell_engine" "$uwu_deps"
collect() {
    local result=$?
    trap - EXIT
    set +e
    if [[ -f "$uwu_game_dir/logs/latest.log" ]]; then cp "$uwu_game_dir/logs/latest.log" "$uwu_out/latest.log"; fi
    if [[ -d "$uwu_game_dir/crash-reports" ]]; then cp -r "$uwu_game_dir/crash-reports" "$uwu_out/"; fi
    mkdir -p "$uwu_out/runtime-config"
    cp -r "$uwu_game_dir/config/." "$uwu_out/runtime-config/"
    if [[ -d "$uwu_game_dir/DisposableTimePocketServer/serverconfig" ]]; then
        cp -r "$uwu_game_dir/DisposableTimePocketServer/serverconfig" "$uwu_out/saved-server-config"
    fi
    printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check > "$uwu_out/JAR-HASH-AFTER.txt" 2>&1
    local integrity=$?
    if [[ "$integrity" != 0 ]]; then result=1; fi
    exit "$result"
}
trap collect EXIT
printf '%s  %s\n' "${UWU_EXPECTED_JAR_SHA256:?Supply candidate SHA-256}" "$UWU_JAR" | sha256sum --check | tee "$uwu_out/JAR-HASH-BEFORE.txt"
python3 - <<'VERIFY_EVENT'
import json, os
from pathlib import Path
repo=json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())['repository']
if repo['full_name'] != 'WeepingAngeIs/z-UwU_dm' or repo['private'] is not False:
    raise SystemExit('This harness is authorized only for the existing public UwU packaging repository')
print('UWUZI PUBLIC EMBEDDED CUE SCOPE PASS')
VERIFY_EVENT
for file in BUILD_PROVENANCE.json SOURCE_MANIFEST.json SHA256SUMS.txt; do
    if [[ -f "build/combined-candidate/$file" ]]; then cp "build/combined-candidate/$file" "$uwu_out/$file"; fi
done
python3 time-pocket-check/check_package.py "$UWU_JAR" "$UWU_EXPECTED_JAR_SHA256" "$uwu_out/package-check.json" | tee "$uwu_out/package-check.log"
bash weapon-release/bootstrap.sh
fetch() {
    local url=$1 name=$2 expected=$3
    local dest="$uwu_deps/$name.jar"
    if [[ ! -f "$dest" ]] || ! printf '%s  %s\n' "$expected" "$dest" | sha256sum --check --status; then
        curl --retry 3 --retry-all-errors --fail --silent --show-error --location --max-time 120 "$url" --output "$dest"
    fi
    printf '%s  %s\n' "$expected" "$dest" | sha256sum --check
}
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' lootr 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://cdn.modrinth.com/data/5sy6g3kz/versions/VhIOvcXP/bettercombat-neoforge-2.4.0%2B1.21.1.jar' bettercombat 0446e9faa98b764ba010ac31e337d14ac96bbb2fbfeff9b599e1efd510dfde5a
fetch 'https://cdn.modrinth.com/data/gedNE4y2/versions/HJZB6bmA/player-animation-lib-forge-2.0.4%2B1.21.1.jar' playeranimator dbe5de45f5cd60c0e5e47af14e6d564534a98456e973cf670cb881f6938eee92
fetch 'https://cdn.modrinth.com/data/9s6osm5g/versions/izKINKFg/cloth-config-15.0.140-neoforge.jar' cloth-config 65e722e0d98431a07c45f8bdd8d529a217cc8c175fde1740248bd5c1b4f3c0d4
fetch 'https://cdn.modrinth.com/data/XvoWJaA2/versions/O0t93Aau/spell_engine-neoforge-1.10.6%2B1.21.1.jar' spell-engine f4880f5ff445b139db3a74127fc74f341a88ae522844c64e9840032ad84e7cdd
fetch 'https://cdn.modrinth.com/data/8ooWzSQP/versions/RFCD8Aio/spell_power-neoforge-1.6.0%2B1.21.1.jar' spell-power a57cd041be1bee86640861588ad9b4a3a032d2dedff8d44abf5305136e2f5854
fetch 'https://cdn.modrinth.com/data/vvuO3ImH/versions/yohfFbgD/curios-neoforge-9.5.1%2B1.21.1.jar' curios a45df2125c26219974aba7507ffc9afe7b83acc941a386af3faacb1cc0056fde
sha256sum "$uwu_deps"/*.jar > "$uwu_out/DEPENDENCIES-SHA256.txt"
printf 'earlyWindowControl = false\n' > "$uwu_game_dir/config/fml.toml"
printf '{"firstPersonAnimations":"AUTO","isHoldToAttackEnabled":true,"isMiningWithWeaponsEnabled":true}\n' > "$uwu_game_dir/config/bettercombat/client.json5"
cat > "$uwu_game_dir/config/spell_engine/client.json5" <<'CONFIG'
{
    "spellHotbarHidesOffhand": true,
    "spellHotbarUseKey": true,
    "spell_hotbar_1_defer": "HOTBAR_KEY_1",
    "spell_hotbar_2_defer": "HOTBAR_KEY_2",
    "spell_hotbar_3_defer": "HOTBAR_KEY_3",
    "spell_hotbar_4_defer": "HOTBAR_KEY_4",
    "spell_hotbar_5_defer": "HOTBAR_KEY_5",
    "spell_hotbar_6_defer": "HOTBAR_KEY_6",
    "spell_hotbar_7_defer": "HOTBAR_KEY_7",
    "spell_hotbar_8_defer": "HOTBAR_KEY_8",
    "spell_hotbar_9_defer": "HOTBAR_KEY_9",
    "sneakingByPassSpellHotbar": false,
    "useKeyHighPriority": false
}
CONFIG
case "$POCKET_SIDE" in
    client)
        # This branch is reachable only after the GitHub Actions/public-repository guards above.
        if ! command -v xvfb-run >/dev/null || ! command -v xauth >/dev/null || \
                ! /usr/bin/python3 -c 'import ctypes; import numpy, PIL; ctypes.CDLL("libGL.so.1"); ctypes.CDLL("libopenal.so.1")'; then
            sudo apt-get update -qq
            sudo apt-get install -y xvfb xauth libgl1 libgl1-mesa-dri libopenal1 python3-numpy python3-pil
        fi
        command -v xvfb-run >/dev/null
        command -v xauth >/dev/null
        /usr/bin/python3 -c 'import ctypes; import numpy, PIL; ctypes.CDLL("libGL.so.1"); ctypes.CDLL("libopenal.so.1")'
        printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:4\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nbobView:false\nguiScale:2\nresourcePacks:["vanilla"]\n' > "$uwu_game_dir/options.txt"
        export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
        timeout --kill-after=20s 22m xvfb-run -a -s '-screen 0 1024x768x24' \
            bash gradlew --no-daemon --max-workers=2 -I time-pocket-check/probe.gradle runPocketCheck \
            2>&1 | tee "$uwu_out/game.log"
        grep -F 'TIME POCKET CLIENT PASS' "$uwu_out/game.log"
        grep -F 'UWUZI EMBEDDED CUE RESOURCES PASS' "$uwu_out/game.log"
        grep -F 'UWUZI BOTH GUI ICONS PASS' "$uwu_out/game.log"
        for cast in 1 2 3 4; do grep -F "UWUZI ITEM USE PASS cast=$cast " "$uwu_out/game.log"; done
        /usr/bin/python3 time-pocket-check/review.py "$uwu_out" | tee "$uwu_out/pixel-review.log"
        ;;
    server)
        printf 'eula=true\n' > "$uwu_game_dir/eula.txt"
        printf 'server-ip=127.0.0.1\nserver-port=25598\nonline-mode=false\nlevel-name=DisposableTimePocketServer\nlevel-seed=152045\nview-distance=3\nsimulation-distance=3\n' > "$uwu_game_dir/server.properties"
        for POCKET_RUN_STAGE in write restart; do
            export POCKET_RUN_STAGE
            if [[ "$POCKET_RUN_STAGE" == write ]]; then uwu_limit=12m; else uwu_limit=9m; fi
            timeout --kill-after=20s "$uwu_limit" bash gradlew --no-daemon --max-workers=2 \
                -I time-pocket-check/probe.gradle runPocketCheck 2>&1 | tee -a "$uwu_out/game.log"
            if [[ -f "$uwu_game_dir/logs/latest.log" ]]; then cp "$uwu_game_dir/logs/latest.log" "$uwu_out/$POCKET_RUN_STAGE-latest.log"; fi
        done
        grep -F 'UWUZI DEDICATED WRITE PASS' "$uwu_out/game.log"
        grep -F 'UWUZI DEDICATED RESTART PASS' "$uwu_out/game.log"
        python3 - <<'VERIFY_SAVED' | tee "$uwu_out/saved-settings.log"
from pathlib import Path
import tomllib
for path in Path('build/pocket-server').rglob('uwudimension-server.toml'):
    with path.open('rb') as source:
        data=tomllib.load(source)
    if data.get('uwuziFreezeSeconds') == 7 and data.get('cupidBowCharmSeconds') == 9:
        print('UWUZI DISK SETTINGS PASS', path)
        break
else:
    raise AssertionError('Missing saved UwUzi and Cupid Bow durations')
VERIFY_SAVED
        ;;
esac
if grep -E 'TIME POCKET CHECK FAILED|UWU PACKAGED CANDIDATE FAILED' "$uwu_out/game.log"; then exit 1; fi
grep -F "UWU PACKAGED CANDIDATE PASS sha256=$UWU_EXPECTED_JAR_SHA256" "$uwu_out/game.log"
python3 time-pocket-check/check_package.py "$UWU_JAR" "$UWU_EXPECTED_JAR_SHA256" "$uwu_out/package-check-after.json" | tee "$uwu_out/package-check-after.log"
