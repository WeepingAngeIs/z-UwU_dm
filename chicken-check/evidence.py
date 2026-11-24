import csv
from pathlib import Path
import json

root = Path('build/chickens')
identities = []
for role in ('host', 'observer'):
    folder = root / 'evidence' / role
    with (folder / 'chickens.tsv').open() as f:
        rows = {key: (bird, skin, parts, float(speed)) for key,bird,skin,parts,speed in csv.reader(f,delimiter='\t')}
    assert len(rows) == 69 and len(list(folder.glob('*.png'))) == len(rows), (role,len(rows))
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
print('Accepted two clients, fresh and reopened world, 69 captures per client and native movement on both')
