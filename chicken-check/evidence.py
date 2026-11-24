import csv
from pathlib import Path
import json
import os

root = Path('build/chickens')
identities = []
styles = ('blossoms','crest','fringe')
shader_spot = os.getenv('UWU_QA_SHADER_SPOT') == 'true'
for role in ('host', 'observer'):
    folder = root / 'evidence' / role
    with (folder / 'chickens.tsv').open() as f:
        rows = {key: (bird, skin, parts, float(speed)) for key,bird,skin,parts,speed in csv.reader(f,delimiter='\t')}
    if shader_spot:
        expected = {f'fresh-{style}-{angle}-{frame}' for style in styles for angle in range(2) for frame in range(3)}
        assert set(rows) == expected, (role,expected ^ set(rows))
        pipelines = list(csv.reader((folder/'shader.tsv').read_text().splitlines(),delimiter='\t'))
        assert len(pipelines) == 6 and all((entry[2]=='true') == (role=='observer') for entry in pipelines), (role,pipelines)
    else:
        assert len(rows) == 69, (role,len(rows))
    assert len(list(folder.glob('*.png'))) == len(rows), (role,len(rows))
    state = {}
    moving = []
    for key, (bird, skin, parts, speed) in rows.items():
        label = key.split('-',1)[1]
        state[key] = (bird, skin, frozenset(parts.strip('[]').split(', ')))
        if speed > .01: moving.append(key)
        if label.startswith('crossed-'):
            assert skin=='minecraft:textures/entity/chicken.png' and parts=='[]'
    for style in ('blossoms','crest','fringe'):
        assert any(key.startswith('fresh-'+style+'-') for key in moving), ('no movement recorded',role,style)
    identities.append(state)
    (folder/'summary.json').write_text(json.dumps({'captures':len(rows),'movement_samples':moving,'client':role},indent=2))
assert identities[0] == identities[1], 'observers disagree on actual rendered entity, head or wings'
print(f'Accepted two clients, {len(identities[0])} captures per client and native movement for every look on both')
