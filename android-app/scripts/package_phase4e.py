#!/usr/bin/env python3
import json,hashlib,zipfile,pathlib,sys
ROOT=pathlib.Path(__file__).resolve().parent.parent
OUT=pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else ROOT.parent/'StrideLab-Phase4E-Final-candidate.zip'
PREFIX='StrideLab-Phase4E/'
MANIFEST='SHA256-MANIFEST-Phase4E-Final-candidate.json'
files=[p for p in ROOT.rglob('*') if p.is_file() and '__pycache__' not in p.parts and not p.name.endswith('.pyc') and p.name!=MANIFEST and not p.name.startswith('.DS_Store')]
files.sort(key=lambda p:str(p.relative_to(ROOT)))
manifest={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
with zipfile.ZipFile(OUT,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9,strict_timestamps=False) as z:
 for p in files:
  z.write(p,PREFIX+p.relative_to(ROOT).as_posix())
 z.writestr(PREFIX+MANIFEST,json.dumps(manifest,sort_keys=True,indent=2)+'\n')
with zipfile.ZipFile(OUT) as z:
 if z.testzip():raise Exception('ZIP CRC failed')
 m=json.loads(z.read(PREFIX+MANIFEST))
 for name,h in m.items():
  if hashlib.sha256(z.read(PREFIX+name)).hexdigest()!=h:raise Exception('Bad manifest: '+name)
 print(json.dumps({'zip':str(OUT),'bytes':OUT.stat().st_size,'sha256':hashlib.sha256(OUT.read_bytes()).hexdigest(),'manifest':f'{len(m)}/{len(m)}','zip_crc':'passed'},indent=2))
