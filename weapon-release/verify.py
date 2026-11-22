"""Pin the unmodified integrated baseline used for the first hosted weapon audit."""
from hashlib import sha256
import json
import os
from pathlib import Path
import subprocess

BASE = '1f489c5308b686a30672ec19b671a64534eb28a4'
BASE_JAR_SHA = '0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f'


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def git(*args):
    return subprocess.check_output(['git', *args])


def main():
    require(os.environ.get('GITHUB_ACTIONS') == 'true', 'Run focused validation only on GitHub Actions')
    require(os.environ.get('GITHUB_REPOSITORY') == 'WeepingAngeIs/z-UwU_dm', 'Wrong repository')
    event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    require(event.get('repository', {}).get('private') is False, 'A public repository is required')
    head = git('rev-parse', 'HEAD').decode().strip()
    require(head == os.environ['GITHUB_SHA'], 'Wrong source commit')
    subprocess.run(['git', 'merge-base', '--is-ancestor', BASE, head], check=True)
    subprocess.run(['git', 'diff', '--exit-code', 'HEAD', '--'], check=True)
    changed = git('diff', '--name-only', BASE, 'HEAD').decode().splitlines()
    allowed = ('weapon-check/', 'weapon-release/', 'weapon-audit/')
    require(all(p.startswith(allowed) or p == '.github/workflows/uwu-combined-integration.yml'
                for p in changed), 'The baseline capture must not alter any production or prior validation files')
    require(not git('ls-files', '--others', '--exclude-standard', '--', 'src/main'),
            'Untracked production source is not allowed')
    jar = Path(os.environ['UWU_JAR']).resolve()
    digest = sha256(jar.read_bytes()).hexdigest()
    require(digest == BASE_JAR_SHA == os.environ['UWU_EXPECTED_JAR_SHA256'], 'Wrong baseline JAR bytes')
    report = {'source_sha': head, 'baseline_source_sha': BASE, 'jar': str(jar),
              'jar_sha256': digest, 'production_changes': [], 'phase': 'baseline-observation'}
    Path('build/weapon-release').mkdir(parents=True, exist_ok=True)
    Path('build/weapon-release/scope.json').write_text(json.dumps(report, indent=2) + '\n')
    print('UWU WEAPON BASELINE SOURCE PASS unchanged integrated production and exact packaged artifact')


if __name__ == '__main__':
    main()
