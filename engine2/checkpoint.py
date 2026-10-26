"""Create a recoverable private source archive, with SHA-256 payload and archive manifests."""
from pathlib import Path
import argparse,hashlib,json,zipfile
root=Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser();p.add_argument('--regression');args=p.parse_args()
if args.regression:
    stats=json.loads(Path(args.regression).read_text())['stats']
    assert stats['expected']==273 and all(stats[k]==0 for k in ['unexpected','skipped','flaky']),stats
files=sorted(f for folder in ['engine2','docs'] for f in (root/folder).rglob('*') if f.is_file() and not any(x in f.parts for x in ['node_modules','dist','results','test-results','checkpoints','__pycache__']))
out=root/'engine2/checkpoints';out.mkdir(exist_ok=True)
manifest={str(f.relative_to(root)):hashlib.sha256(f.read_bytes()).hexdigest() for f in files}
payload=json.dumps(manifest,sort_keys=True,indent=2)+'\n'
(out/'SHA256-MANIFEST.json').write_text(payload)
archive=out/'StrideLab-Engine2-paused-source.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for f in files:z.write(f,'StrideLab-Engine2/'+str(f.relative_to(root)))
    z.writestr('StrideLab-Engine2/SHA256-MANIFEST.json',payload)
assert zipfile.ZipFile(archive).testzip() is None
(out/(archive.name+'.sha256')).write_text(hashlib.sha256(archive.read_bytes()).hexdigest()+'  '+archive.name+'\n')
print(archive,hashlib.sha256(archive.read_bytes()).hexdigest())
