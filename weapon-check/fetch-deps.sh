#!/usr/bin/env bash
# Public, immutable runtime dependencies for the disposable hosted weapon fixture.
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
test "${GITHUB_REPOSITORY:-}" = WeepingAngeIs/z-UwU_dm
mkdir -p build/weapon-deps
fetch() {
    local url=$1 name=$2 digest=$3
    curl --retry 3 --retry-all-errors --connect-timeout 20 --max-time 150 \
        --fail --silent --show-error --location "$url" --output "build/weapon-deps/$name.jar"
    printf '%s  %s\n' "$digest" "build/weapon-deps/$name.jar" | sha256sum --check
}
fetch 'https://cdn.modrinth.com/data/EltpO5cN/versions/ST5FHZnD/lootr-neoforge-1.21.1-1.11.38.125.jar' lootr 091878598fa5cb51cd304bf9324afe46420d328b6f77a9ba38acc15e26945e9e
fetch 'https://cdn.modrinth.com/data/5sy6g3kz/versions/VhIOvcXP/bettercombat-neoforge-2.4.0%2B1.21.1.jar' bettercombat 0446e9faa98b764ba010ac31e337d14ac96bbb2fbfeff9b599e1efd510dfde5a
fetch 'https://cdn.modrinth.com/data/gedNE4y2/versions/HJZB6bmA/player-animation-lib-forge-2.0.4%2B1.21.1.jar' playeranimator dbe5de45f5cd60c0e5e47af14e6d564534a98456e973cf670cb881f6938eee92
fetch 'https://cdn.modrinth.com/data/9s6osm5g/versions/izKINKFg/cloth-config-15.0.140-neoforge.jar' cloth-config 65e722e0d98431a07c45f8bdd8d529a217cc8c175fde1740248bd5c1b4f3c0d4
fetch 'https://cdn.modrinth.com/data/bK3Ubu9p/versions/nS0Yahr5/simplyswords-neoforge-1.70.2-1.21.1.jar' simplyswords 5e75bd66ea489079d60c9c1ed98762550fbc0bee88cd7af399f17bbeb56608c3
fetch 'https://cdn.modrinth.com/data/lhGA9TYQ/versions/1IiqEQGl/architectury-13.0.11-neoforge.jar' architectury 9cc92f2c09533fc5482c60f993bd891c655a0e32b637037cdcb7c2b23adedeeb
fetch 'https://cdn.modrinth.com/data/hYykXjDp/versions/jRuOkBAG/fzzy_config-0.7.7%2Bfix1%2B1.21%2Bneoforge.jar' fzzy-config 414782aba00502349da192c1affcd4cbaf653119ec26605453f1b0690b3f8cc5
fetch 'https://cdn.modrinth.com/data/6avVoBVB/versions/Zb6dytvm/SimplyTooltips-neoforge-0.1.5.jar' simplytooltips fdac367da1c5243ac5225f00f831b786817452ee9fa6a7f4acd99ec819b0f2b7
fetch 'https://cdn.modrinth.com/data/ordsPcFz/versions/9ZlVvei8/kotlinforforge-5.13.0-all.jar' kotlinforforge 1aa52c3e992d8ee4d1b9e9bdb0c62bf7ad03845195136d63f312ade6f30809de
sha256sum build/weapon-deps/*.jar > build/weapon-deps/dependencies.sha256
