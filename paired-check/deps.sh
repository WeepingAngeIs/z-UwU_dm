#!/usr/bin/env bash
set -euo pipefail
uwu_deps=build/combined-deps
mkdir -p "$uwu_deps"
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
