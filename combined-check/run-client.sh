#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
export UWU_COMBINED_MODE=${1:?Supply gameplay or visuals}
case "$UWU_COMBINED_MODE" in gameplay|visuals) ;; *) exit 2 ;; esac
test -f "${UWU_JAR:?Supply the sealed candidate JAR}"
printf '%s  %s\n' "${UWU_EXPECTED_JAR_SHA256:?Supply candidate SHA-256}" "$UWU_JAR" | sha256sum --check
uwu_game_dir="build/combined-client-$UWU_COMBINED_MODE"
uwu_deps=build/combined-deps
uwu_out="build/combined-evidence/$UWU_COMBINED_MODE"
mkdir -p "$uwu_deps" "$uwu_game_dir/config/bettercombat" "$uwu_game_dir/config/spell_engine" "$uwu_out"
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
printf 'version:3955\nonboardAccessibility:false\ntutorialStep:none\nrenderDistance:4\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\npauseOnLostFocus:false\nguiScale:2\n' > "$uwu_game_dir/options.txt"
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
collect() {
    if [[ -f "$uwu_game_dir/logs/latest.log" ]]; then cp "$uwu_game_dir/logs/latest.log" "$uwu_out/latest.log"; fi
    if [[ -d "$uwu_game_dir/crash-reports" ]]; then cp -r "$uwu_game_dir/crash-reports" "$uwu_out/"; fi
    cp -r "$uwu_game_dir/config" "$uwu_out/runtime-config"
}
trap collect EXIT
export LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
timeout --kill-after=20s 25m xvfb-run -a -s '-screen 0 1280x720x24' \
    bash gradlew --no-daemon --max-workers=2 -I combined-check/client.gradle runCombinedClient \
    2>&1 | tee "$uwu_out/client.log"
grep -F 'UWU WEAPONS PACKAGED CANDIDATE PASS' "$uwu_out/client.log"
case "$UWU_COMBINED_MODE" in
    gameplay) grep -F 'COMBINED GAMEPLAY PASS' "$uwu_out/client.log" ;;
    visuals) grep -F 'COMBINED VISUAL CAPTURES PASS' "$uwu_out/client.log" ;;
esac
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" "$UWU_JAR" | sha256sum --check
