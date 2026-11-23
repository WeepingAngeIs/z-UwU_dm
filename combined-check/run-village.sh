#!/usr/bin/env bash
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
uwu_seed=${1:?Supply 152045 or 20261121}
case "$uwu_seed" in 152045|20261121) ;; *) exit 2 ;; esac
test -f "${UWU_JAR:?Supply sealed JAR}"
printf '%s  %s\n' "${UWU_EXPECTED_JAR_SHA256:?Supply sealed digest}" "$UWU_JAR" | sha256sum --check
uwu_repo_root=$PWD
uwu_out="$uwu_repo_root/build/combined-evidence/village-$uwu_seed"
mkdir -p "$uwu_out"
cd "$(mktemp -d "$RUNNER_TEMP/uwu-combined-village.XXXXXXXX")"
mkdir mods
cp "$UWU_JAR" mods/uwu.jar
cp "$uwu_repo_root/build/combined-profession-check/profession-probe.jar" mods/probe.jar
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
if [[ "$uwu_seed" == 20261121 ]]; then
    fetch 'https://mediafilez.forgecdn.net/files/9221/991/open-parties-and-claims-neoforge-1.21.1-0.33.0.jar' mods/openpac.jar 77eadbbb7cbd7298d9a99070ebde552b765dd91bdad5054c9f845a17d2931765
fi
sha256sum mods/*.jar > "$uwu_out/DEPENDENCIES-SHA256.txt"
timeout --kill-after=15s 6m java -Xmx1G -jar installer.jar --installServer
printf 'eula=true\n' > eula.txt
printf 'server-ip=127.0.0.1\nonline-mode=false\nlevel-type=minecraft:flat\nlevel-seed=%s\ndifficulty=peaceful\nspawn-protection=0\nview-distance=3\nsimulation-distance=3\nmax-tick-time=360000\n' "$uwu_seed" > server.properties
collect() { if [[ -f logs/latest.log ]]; then cp logs/latest.log "$uwu_out/server-latest.log"; fi; }
trap collect EXIT
timeout --kill-after=20s 25m java -Xms512m -Xmx4G -XX:+ExitOnOutOfMemoryError \
    "-Duwu.profession.out=$uwu_out" "-Duwu.check.candidateJar=$PWD/mods/uwu.jar" \
    "-Duwu.check.candidateSha256=$UWU_EXPECTED_JAR_SHA256" -Duwu.check.role=combined-village \
    @libraries/net/neoforged/neoforge/21.1.249/unix_args.txt nogui 2>&1 | tee "$uwu_out/native.log"
grep -F 'UWU WEAPONS PACKAGED CANDIDATE PASS' "$uwu_out/native.log"
grep -F "UWU PROFESSION AND ESCORT PASS $uwu_seed" "$uwu_out/native.log"
printf '%s  %s\n' "$UWU_EXPECTED_JAR_SHA256" mods/uwu.jar | sha256sum --check
