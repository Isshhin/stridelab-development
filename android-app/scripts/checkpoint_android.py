#!/usr/bin/env python3
"""Archive tracked Android source with SHA-256 payload and archive manifests; no APK/signing keys."""
from pathlib import Path
import hashlib,json,subprocess,zipfile
root=Path(__file__).resolve().parents[2]
names=subprocess.check_output(['git','ls-files','--','android-app','docs/ANDROID-FIRST-ARCHITECTURE.md'],cwd=root,text=True).splitlines()
assert names,'Stage or commit Android source before checkpointing'
manifest={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sorted(names)}
out=root/'android-app/checkpoints';out.mkdir(parents=True,exist_ok=True)
payload=json.dumps(manifest,sort_keys=True,indent=2)+'\n'
(out/'SHA256-MANIFEST.json').write_text(payload)
archive=out/'StrideLab-Android-Foundation-source.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name in sorted(names):
        info=zipfile.ZipInfo('StrideLab-Android-Foundation/'+name,(2026,10,26,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,(root/name).read_bytes())
    z.writestr('StrideLab-Android-Foundation/SHA256-MANIFEST.json',payload)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
digest=hashlib.sha256(archive.read_bytes()).hexdigest()
(out/(archive.name+'.sha256')).write_text(digest+'  '+archive.name+'\n')
print(json.dumps({'archive':str(archive),'payload_files':len(manifest),'sha256':digest}))
