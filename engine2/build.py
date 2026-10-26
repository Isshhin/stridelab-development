"""Add Engine 2 to the immutable accepted Phase 4D source. Never patches script#eng.
Usage: python3 engine2/build.py path/to/StrideLab-Phase4D-Final-candidate.zip
"""
from pathlib import Path
import zipfile, hashlib, re, sys, json
ROOT=Path(__file__).resolve().parent
archive=Path(sys.argv[1])
assert hashlib.sha256(archive.read_bytes()).hexdigest()=='915c9c37e3816291bfc9ab3ead1f37b400711886056a43fd1af101b602cdaa6b','Wrong accepted source'
out=ROOT/'dist';out.mkdir(exist_ok=True)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    prefix='StrideLab-WS5/'
    manifest=json.loads(z.read(prefix+'SHA256-MANIFEST-Phase4D-Final-candidate.json'))
    assert len(manifest)==630
    for p,h in manifest.items():
        assert hashlib.sha256(z.read(prefix+p)).hexdigest()==h,p
    html=z.read(prefix+'stridelab-afterglow-phase4d2-wip.html').decode()
    for name in z.namelist():
        if name.startswith(prefix+'assets/') and not name.endswith('/'):
            p=out/name[len(prefix):];p.parent.mkdir(exist_ok=True,parents=True);p.write_bytes(z.read(name))
protected=lambda s:hashlib.sha256(re.search(r'<script id="eng">(.*?)</script>',s,re.S).group(1).encode()).hexdigest()
assert protected(html)=='3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2'
anchor='window.StrideLabPhase4D=Phase4D;'
assert html.count(anchor)==1
html=html.replace(anchor,anchor+'\n'+(ROOT/'bridge.js').read_text())
# Same offline font policy as Phase 4E: use the existing system-font fallback.
html=re.sub(r'<link href="https://fonts.googleapis.com/[^>]+>\n?', '', html)
html=re.sub(r'<title>.*?</title>', '<title>StrideLab · Unified Coaching Engine 2.0</title>', html, count=1)
# Existing label helpers must reflect the accepted unified plan.
html=html.replace("const gpN=()=>S.gp?S.gp.name:'Sample plan'", "const gpN=()=>S.engine2?'Unified fitness plan':S.gp?S.gp.name:'Sample plan'")
html=html.replace("const gpWk=()=>!S.gp?'Not generated yet'", "const gpWk=()=>S.engine2?'Engine 2.0 · revision '+S.engine2.revision:!S.gp?'Not generated yet'")
# A legacy plan approval explicitly switches back to legacy projection.
html=html.replace('function savePlanState(next,pl){','function savePlanState(next,pl){\n delete next.engine2;')
html=html.replace('</body>', '<script>\n'+(ROOT/'engine.cjs').read_text()+'\n</script>\n<script>\n'+(ROOT/'ui.js').read_text()+'\n</script>\n</body>')
assert protected(html)=='3ae9226a19718925d5a0c59771d13ed85860b4f4cd694490f0cbf668cfb3a3f2'
(out/'index.html').write_text(html,encoding='utf-8',newline='\n')
print('Built',out/'index.html',hashlib.sha256((out/'index.html').read_bytes()).hexdigest())
