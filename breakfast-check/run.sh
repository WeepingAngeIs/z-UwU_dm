#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
test -f "${UWU_JAR:?Supply the sealed candidate JAR}"
uwu_breakfast_candidate=$(realpath "$UWU_JAR")
printf '%s  %s\n' "${UWU_EXPECTED_JAR_SHA256:?Supply the sealed candidate digest}" "$uwu_breakfast_candidate" | sha256sum --check
uwu_breakfast_repo=$PWD
uwu_breakfast_out="$uwu_breakfast_repo/build/combined-evidence/breakfast"
mkdir -p "$uwu_breakfast_out"
test -f "$uwu_breakfast_repo/build/breakfast-check/breakfast-probe.jar"
cd "$(mktemp -d "$RUNNER_TEMP/uwu-breakfast.XXXXXXXX")"
mkdir mods
cp "$uwu_breakfast_candidate" mods/uwu.jar
cp "$uwu_breakfast_repo/build/breakfast-check/breakfast-probe.jar" mods/probe.jar
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" mods/uwu.jar | sha256sum --check
fetch() {
    curl --retry 3 --retry-all-errors -fsSL --max-time 150 "$1" -o "$2"
    printf '%s  %s\n' "$3" "$2" | sha256sum --check
}
fetch https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.249/neoforge-21.1.249-installer.jar installer.jar d88b448eab73cd65bdf1720844a4828262de30a15fc71bd04dd81acc61c5399a
fetch https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar mods/lootr.jar 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch https://cdn.modrinth.com/data/ulloLmqG/versions/Q29JlZfU/another_furniture-neoforge-4.0.2.jar mods/furniture.jar c61a66c73bcc5147d9983dc72c485493a612d2dbc078fcb86e2024155e34b7dd
fetch 'https://mediafilez.forgecdn.net/files/7675/823/Spice%20of%20Life%20Onion_NEOFORGE_v1.5.6_mc1.21.1.jar' mods/onion.jar 11605cfec2ac2e6385fc119d01bb6633b8a9ce8fdf0614cd272254630d56c0a7
fetch 'https://mediafilez.forgecdn.net/files/8975/261/CreativeCore_NEOFORGE_v2.13.49_mc1.21.1.jar' mods/creativecore.jar 39506208ad0c7efb2c9ac8128766425faebf50c408ec869d5f5043c0742c231b
sha256sum mods/*.jar > "$uwu_breakfast_out/DEPENDENCIES-SHA256.txt"
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=152045\ndifficulty=peaceful\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=360000\n' > server.properties
collect() {
    if [[ -f logs/latest.log ]]; then cp logs/latest.log "$uwu_breakfast_out/server-latest.log"; fi
    if [[ -d world/advancements ]]; then cp -r world/advancements "$uwu_breakfast_out/player-advancements"; fi
    if [[ -d world/data/uwudimension_baker_toasters ]]; then cp -r world/data/uwudimension_baker_toasters "$uwu_breakfast_out/toaster-receipts"; fi
}
trap collect EXIT
timeout --kill-after=20s 25m java -Xms512m -Xmx4G -XX:+ExitOnOutOfMemoryError \
    "-Duwu.breakfast.out=$uwu_breakfast_out" "-Duwu.check.candidateJar=$PWD/mods/uwu.jar" \
    "-Duwu.check.candidateSha256=$UWU_EXPECTED_JAR_SHA256" -Duwu.check.role=combined-breakfast \
    @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$uwu_breakfast_out/native.log"
grep -F 'UWU WEAPONS PACKAGED CANDIDATE PASS' "$uwu_breakfast_out/native.log"
grep -F 'UWU BREAKFAST PASS' "$uwu_breakfast_out/native.log"
test -f "$uwu_breakfast_out/summary.json"
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" mods/uwu.jar | sha256sum --check
