import importlib.util
import pathlib
import sys
import unittest

path=pathlib.Path(__file__).parents[1]/'check_apk_update.py'
spec=importlib.util.spec_from_file_location('apk_gate',path); gate=importlib.util.module_from_spec(spec);sys.modules[spec.name]=gate;spec.loader.exec_module(gate)

class ApkGateTest(unittest.TestCase):
    def info(self,version=3,key='a',package=gate.APP_ID):
        return gate.ApkInfo(package,version,frozenset([key*64]))
    def test_same_key_newer_version_only_passes_metadata_gate(self):
        report=gate.assess(self.info(),self.info(4))
        self.assertTrue(report['metadataChecksPassed']);self.assertFalse(report['physicalUpgradeValidated'])
    def test_signer_mismatch_blocks_even_with_newer_version(self):
        self.assertFalse(gate.assess(self.info(),self.info(4,'b'))['metadataChecksPassed'])
    def test_equal_or_lower_version_is_blocked(self):
        for version in [2,3]:self.assertFalse(gate.assess(self.info(),self.info(version))['metadataChecksPassed'])
    def test_other_app_identity_is_blocked(self):
        self.assertFalse(gate.assess(self.info(),self.info(4,package='another.package'))['metadataChecksPassed'])
    def test_all_signers_must_match_not_just_one(self):
        additional=gate.ApkInfo(gate.APP_ID,4,frozenset(['a'*64,'b'*64]))
        self.assertFalse(gate.assess(self.info(),additional)['metadataChecksPassed'])
    def test_incomplete_or_unverified_signing_output_is_rejected(self):
        with self.assertRaises(ValueError):gate.parse_apk("package: name='io.stridelab.privatealpha' versionCode='4'",'no verified certificate')
    def test_reads_real_apksigner_sha256_format(self):
        apk=gate.parse_apk("package: name='io.stridelab.privatealpha' versionCode='4' versionName='0.3.1'",'Signer #1 certificate SHA-256 digest: '+':'.join(['AB']*32))
        self.assertEqual('ab'*32,next(iter(apk.certificates)));self.assertEqual(4,apk.version)

if __name__=='__main__':unittest.main()
