#!/usr/bin/env bash
# Reuse only the completed baseline controls from an earlier failed aggregate run.
set -euo pipefail
test "${GITHUB_ACTIONS:-false}" = true
test "${RUNNER_ENVIRONMENT:-}" = github-hosted
test "${GITHUB_REPOSITORY:-}" = WeepingAngeIs/z-UwU_dm
: "${GH_TOKEN:?Supply the public repository read token}"
uwu_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd -- "$uwu_root"
uwu_recovery=$(mktemp -d "${RUNNER_TEMP:?}/uwu-baseline-surveys.XXXXXXXX")
gh api repos/WeepingAngeIs/z-UwU_dm/actions/runs/44961077711 > "$uwu_recovery/run.json"
gh api repos/WeepingAngeIs/z-UwU_dm/actions/artifacts/14731811379 > "$uwu_recovery/artifact.json"
gh api repos/WeepingAngeIs/z-UwU_dm/actions/artifacts/14731811379/zip > "$uwu_recovery/evidence.zip"
printf '%s  %s\n' 11d27a020204a20f617ccffeeb5208369070f22658b4942f91200e45cc5da934 \
    "$uwu_recovery/evidence.zip" | sha256sum --check

python3 - "$uwu_recovery" <<'PY'
from hashlib import sha256
import json
import os
from pathlib import Path
import sys
from zipfile import ZipFile

RUN = 44961077711
ARTIFACT = 14731811379
ZIP_SHA = '11d27a020204a20f617ccffeeb5208369070f22658b4942f91200e45cc5da934'
FIXTURE_SHA = 'cc01a6c942353cbc570b786d9718a58c1438612a'
GENERATOR_SHA = '1f489c5308b686a30672ec19b671a64534eb28a4'
JAR_SHA = '0e865bbe4cf695144b70889138d82ad36ed8288a019bcb0e6b8b4851a6f1a04f'
SEEDS = (152045, 20261121, 8675309)
EXPECTED = {
    'dusk-frequency-evidence/survey-baseline-152045.json': 'bf0d530bf19cacf1b67ba04029ec206209bd71a383074ae9cb4ae46be483818f',
    'dusk-frequency-evidence/survey-baseline-20261121.json': 'fe9654f12bc32177a569f82bbc01f5cc228ff81032d3a797b165eff4aaca71de',
    'dusk-frequency-evidence/survey-baseline-8675309.json': 'd7aa16050aa7c64ad555bff9fdb564b132e968cd6229ff55f4a61e91a4a8c07f',
    'dusk-frequency-check/survey-baseline-152045.log': 'ab92185ca25d96561404fbc9fc400e8fc6189eea5dae4fe1d8e7121d4496fea1',
    'dusk-frequency-check/survey-baseline-20261121.log': '031bc0b11c0bc7f51902612c2e01905d904b2e137a2aabf9799c9c585d62d734',
    'dusk-frequency-check/survey-baseline-8675309.log': '9edfd7da177e234590741237cc13b20455e5f7d6c1b8befc3944b439fc2a0c6a',
}

def require(condition, message):
    if not condition:
        raise AssertionError(message)

temporary = Path(sys.argv[1])
event = json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
require(event.get('repository', {}).get('private') is False, 'Recovery requires the public repository event')
run = json.loads((temporary / 'run.json').read_text())
artifact = json.loads((temporary / 'artifact.json').read_text())
require(run['id'] == RUN and run['head_sha'] == FIXTURE_SHA, 'Wrong baseline-control run/source')
require(run['status'] == 'completed' and run['conclusion'] == 'failure',
        'The reused run must be identified as the completed failed aggregate run')
require(artifact['id'] == ARTIFACT and not artifact['expired'], 'Wrong or expired baseline-control artifact')
require(artifact['workflow_run']['id'] == RUN and artifact['workflow_run']['head_sha'] == FIXTURE_SHA,
        'Artifact belongs to another run/source')
require(not artifact.get('digest') or artifact['digest'] == 'sha256:' + ZIP_SHA,
        'GitHub artifact digest differs from the pinned ZIP')
archive = temporary / 'evidence.zip'
require(sha256(archive.read_bytes()).hexdigest() == ZIP_SHA, 'Pinned baseline archive changed')

contents = {}
with ZipFile(archive) as zipped:
    names = zipped.namelist()
    for name, expected in EXPECTED.items():
        require(names.count(name) == 1, 'Missing or duplicate approved archive entry: ' + name)
        data = zipped.read(name)
        require(sha256(data).hexdigest() == expected, 'Baseline-control file differs: ' + name)
        contents[name] = data

for seed in SEEDS:
    report = json.loads(contents[f'dusk-frequency-evidence/survey-baseline-{seed}.json'])
    require(report['sourceSha'] == FIXTURE_SHA and report['loadedUwuJarSha256'] == JAR_SHA,
            f'Wrong original fixture/generator identity for seed {seed}')
    require(report['seed'] == seed and report['stage'] == 'survey-baseline'
            and report['dimension'] == 'uwudimension:sakura_reach', f'Wrong baseline sample {seed}')
    require(report['latticeCells'] == 441 and len(report['cells']) == 441
            and report['windowRadiusInLatticeCells'] == 10
            and report['minimumSpacingBlocks'] == 384 and report['latticeSpacingBlocks'] == 1536,
            f'Incomplete or different baseline window {seed}')
    require(report['previewGeneratedChunks'] == 0 and report['actualNativePlacementsInThisSurvey'] == 0,
            f'The baseline control is not a pure survey: {seed}')
    positions = {(cell['gx'], cell['gz']) for cell in report['cells']}
    window = {(report['originGx'] + x * 2, report['originGz'] + z * 2)
              for x in range(-10, 11) for z in range(-10, 11)}
    require(positions == window and all(cell['lattice'] for cell in report['cells']),
            f'Baseline cells do not cover the declared fixed window: {seed}')
    log = contents[f'dusk-frequency-check/survey-baseline-{seed}.log'].decode('utf-8')
    marker = f'DUSK FREQUENCY SURVEY PASS stage=survey-baseline seed={seed} cells=441 '
    require(marker in log and f'UWU PACKAGED CANDIDATE PASS sha256={JAR_SHA} ' in log,
            f'Missing completed packaged-baseline markers for seed {seed}')
    require('CHECK FAILED' not in log and 'OutOfMemoryError' not in log,
            f'Failure in individual baseline-control log {seed}')

files = []
for name, data in sorted(contents.items()):
    target = Path('build') / name
    if target.exists():
        require(target.read_bytes() == data, 'Refusing to overwrite different baseline input: ' + str(target))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    files.append({'archive_path': name, 'path': str(target), 'sha256': sha256(data).hexdigest(),
                  'bytes': len(data), 'kind': 'baseline_report' if name.endswith('.json') else 'baseline_log'})

record = {
    'schema_version': 1,
    'kind': 'reused_completed_baseline_controls',
    'source_workflow_run_id': RUN,
    'source_workflow_run_url': f'https://github.com/WeepingAngeIs/z-UwU_dm/actions/runs/{RUN}',
    'source_workflow_run_status': run['status'],
    'source_workflow_run_conclusion': run['conclusion'],
    'source_fixture_sha': FIXTURE_SHA,
    'baseline_generator_source_sha': GENERATOR_SHA,
    'baseline_jar_sha256': JAR_SHA,
    'artifact': {'id': ARTIFACT, 'zip_sha256': ZIP_SHA, 'name': artifact['name']},
    'fixed_seeds': list(SEEDS),
    'lattice_cells_per_seed': 441,
    'files': files,
    'recovered_in_run': os.environ['GITHUB_RUN_ID'],
    'recovered_for_source': os.environ['GITHUB_SHA'],
    'candidate_evidence_reused': False,
    'source_run_pass_claimed': False,
    'scope': 'Only three completed baseline surveys and their individual passing logs are reused. '
             'The source run failed the aggregate candidate density gate; that run is not claimed to have passed. '
             'Original report bytes, sourceSha, generator JAR identity and old schema are preserved.',
}
folder = Path('build/dusk-frequency-ci')
folder.mkdir(parents=True, exist_ok=True)
(folder / 'baseline-survey-provenance.json').write_text(json.dumps(record, indent=2) + '\n')
print(f'DUSK BASELINE CONTROLS RECOVERED PASS run={RUN} files={len(files)} cellsPerSeed=441')
PY
