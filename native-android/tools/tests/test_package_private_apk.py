import importlib.util
import hashlib
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parents[1]))
from check_apk_update import ApkInfo, APP_ID
from package_private_apk import package

class PrivateApkTest(unittest.TestCase):
    def test_digest_metadata_and_explicit_safety_limits(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            apk = root/'input.apk'; apk.write_bytes(b'test APK bytes')
            output = root/'artifact'
            metadata = package(apk, output, ApkInfo(APP_ID, 4, frozenset(['a'*64])), 'commit', 'run')
            self.assertEqual(metadata['apkSha256'], hashlib.sha256(apk.read_bytes()).hexdigest())
            self.assertEqual((output/'StrideLab-native-private-test.apk').read_bytes(), apk.read_bytes())
            self.assertIn(metadata['apkSha256'], (output/'SHA256SUMS').read_text())
            self.assertFalse(metadata['installedDeviceCertificateCompared'])
            self.assertFalse(metadata['physicalUpgradeValidated'])
            self.assertIn('Do not uninstall', (output/'READ-BEFORE-INSTALL.txt').read_text())
            with self.assertRaises(FileExistsError):
                package(apk, output, ApkInfo(APP_ID, 4, frozenset(['a'*64])), 'commit', 'run')
    def test_wrong_identity_creates_no_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            output = pathlib.Path(directory)/'artifact'
            with self.assertRaises(ValueError):
                package(pathlib.Path('absent.apk'), output, ApkInfo('wrong.app', 4, frozenset(['a'*64])), 'commit', 'run')
            self.assertFalse(output.exists())
    def test_durable_signer_pin_and_unknown_phone_compatibility(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory)
            apk=root/'input.apk'; apk.write_bytes(b'signed fixture')
            info=ApkInfo(APP_ID,8,frozenset(['a'*64]))
            with self.assertRaises(ValueError):
                package(apk,root/'rejected',info,'commit','run',durable_signer='b'*64)
            self.assertFalse((root/'rejected').exists())
            metadata=package(apk,root/'accepted',info,'commit','run',durable_signer='a'*64)
            self.assertEqual(metadata['buildType'],'release')
            self.assertFalse(metadata['physicalUpgradeValidated'])
            self.assertIn('UNKNOWN',metadata['updateCompatibility'])
            self.assertTrue((root/'accepted/StrideLab-native-private-signed.apk').is_file())
