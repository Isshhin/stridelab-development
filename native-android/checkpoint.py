"""Recoverable tracked-source archive; includes legacy code/assets reused by Compose."""
import hashlib, pathlib, subprocess, zipfile
root = pathlib.Path(__file__).resolve().parents[1]
paths = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
paths = sorted(p for p in paths if p and (p.startswith(('native-android/', 'android-app/', 'docs/')) or p == '.github/workflows/native-android.yml') and '/checkpoints/' not in p)
out = root / 'native-android/checkpoints'; out.mkdir(parents=True, exist_ok=True)
manifest = []
archive = out / 'StrideLab-native-source.zip'
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as target:
    for name in paths:
        data = (root / name).read_bytes()
        manifest.append(f'{hashlib.sha256(data).hexdigest()}  {name}')
        target.writestr(zipfile.ZipInfo(name, (2026, 10, 26, 0, 0, 0)), data)
    target.writestr(zipfile.ZipInfo('SHA256SUMS', (2026, 10, 26, 0, 0, 0)), '\n'.join(manifest) + '\n')
sha = hashlib.sha256(archive.read_bytes()).hexdigest()
(out / 'StrideLab-native-source.zip.sha256').write_text(f'{sha}  {archive.name}\n')
print(f'{len(paths)} tracked files; SHA-256 {sha}')
