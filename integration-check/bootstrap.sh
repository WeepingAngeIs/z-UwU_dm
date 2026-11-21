#!/usr/bin/env bash
# CI transport fallback only: keep the committed wrapper and build inputs intact.
set -euo pipefail
if [[ "${GITHUB_ACTIONS:-}" != true || "${GITHUB_REPOSITORY:-}" != WeepingAngeIs/z-UwU_dm ]]; then
    echo 'Run the integration bootstrap only in the public repository on GitHub Actions.' >&2
    exit 1
fi

uwu_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
uwu_gradle_home=${GRADLE_USER_HOME:-${HOME:?}/.gradle}
uwu_gradle_sha=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f
# Official binary checksum: https://gradle.org/release-checksums/#9.2.1
uwu_cache_dir=$(python3 - "$uwu_root" "$uwu_gradle_home" "$uwu_gradle_sha" <<'PY'
import hashlib
import json
import os
from pathlib import Path
import sys

root, gradle_home, expected_sha = sys.argv[1:]
event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
if event.get('repository', {}).get('private') is not False:
    raise SystemExit('The integration bootstrap requires the public repository event.')
properties = {}
for line in (Path(root) / 'gradle/wrapper/gradle-wrapper.properties').read_text().splitlines():
    if line.strip() and not line.lstrip().startswith(('#', '!')):
        key, value = line.split('=', 1)
        properties[key.strip()] = value.strip().replace(r'\:', ':')
url = 'https://services.gradle.org/distributions/gradle-9.2.1-bin.zip'
expected = {
    'distributionUrl': url,
    'distributionSha256Sum': expected_sha,
    'distributionBase': 'GRADLE_USER_HOME',
    'distributionPath': 'wrapper/dists',
    'zipStoreBase': 'GRADLE_USER_HOME',
    'zipStorePath': 'wrapper/dists',
}
for key, value in expected.items():
    if properties.get(key) != value:
        raise SystemExit(f'Unexpected wrapper input {key}: {properties.get(key)!r}')
# Gradle 9.2.1 PathAssembler: unsigned MD5(original URL), represented in base 36.
# MD5 is only the wrapper cache directory key; SHA-256 verifies the download.
number = int.from_bytes(hashlib.md5(url.encode('utf-8'), usedforsecurity=False).digest(), 'big')
digits = '0123456789abcdefghijklmnopqrstuvwxyz'
key = ''
while number:
    number, remainder = divmod(number, 36)
    key = digits[remainder] + key
print(Path(gradle_home).resolve() / 'wrapper/dists/gradle-9.2.1-bin' / (key or '0'))
PY
)

mkdir -p -- "$uwu_cache_dir" "$uwu_gradle_home/init.d"
uwu_zip="$uwu_cache_dir/gradle-9.2.1-bin.zip"
uwu_download=$(mktemp "$uwu_cache_dir/uwu-gradle-download.XXXXXXXX")
trap 'rm -f -- "$uwu_download"' EXIT
# The official endpoint may redirect to Gradle's official GitHub release assets.
# curl retries obtain a fresh redirect; no downloaded executable is run here.
curl --fail --location --silent --show-error --proto '=https' --proto-redir '=https' \
    --connect-timeout 20 --max-time 180 --retry 4 --retry-all-errors --retry-delay 3 --retry-max-time 240 \
    'https://downloads.gradle.org/distributions/gradle-9.2.1-bin.zip' --output "$uwu_download"
printf '%s  %s\n' "$uwu_gradle_sha" "$uwu_download" | sha256sum --check
mv -- "$uwu_download" "$uwu_zip"
# Let the unmodified wrapper verify and extract the seeded ZIP itself. Removing
# only its success marker prevents reuse of an unverified, previously extracted copy.
rm -f -- "$uwu_zip.ok"
cp -- "$uwu_root/integration-check/official-central.init.gradle" \
    "$uwu_gradle_home/init.d/uwu-official-central.init.gradle"
printf 'UWU CI BOOTSTRAP PASS gradle=9.2.1 sha256=%s cache=%s central=https://repo1.maven.org/maven2/\n' \
    "$uwu_gradle_sha" "$uwu_cache_dir"
