import base64
import os
import pathlib
import unittest
from unittest.mock import patch
import sys
sys.path.insert(0,str(pathlib.Path(__file__).parents[1]))
from build_durable_private import build
from check_apk_update import ApkInfo,APP_ID

class DurableSigningTest(unittest.TestCase):
    def test_missing_secrets_and_invalid_version_fail_before_build(self):
        with patch.dict(os.environ,{},clear=True),patch('build_durable_private.subprocess.run') as run:
            with self.assertRaises(ValueError): build(pathlib.Path('.'),'15','commit','run','a'*64)
            with self.assertRaises(ValueError): build(pathlib.Path('.'),'14','commit','run','a'*64)
            run.assert_not_called()
    def test_temporary_key_is_removed_and_signer_pin_is_passed_to_packager(self):
        env=dict(STRIDELAB_KEYSTORE_BASE64=base64.b64encode(b'synthetic-key-fixture').decode(),STRIDELAB_STORE_PASSWORD='fixture',STRIDELAB_KEY_ALIAS='fixture',STRIDELAB_KEY_PASSWORD='fixture',ANDROID_HOME='/synthetic-sdk')
        key_paths=[]
        def process(args,env,**kwargs):
            key=pathlib.Path(env['STRIDELAB_KEYSTORE_FILE']); key_paths.append(key)
            self.assertEqual(key.read_bytes(),b'synthetic-key-fixture')
            self.assertFalse(any('fixture' in item for item in args))
            return type('Result',(),dict(returncode=0))()
        with patch.dict(os.environ,env,clear=True),patch('build_durable_private.subprocess.run',side_effect=process),patch('build_durable_private.inspect',return_value=ApkInfo(APP_ID,15,frozenset(['a'*64]))),patch('build_durable_private.package',return_value={}) as package:
            build(pathlib.Path('/synthetic-root'),'15','commit','run','a'*64)
            self.assertEqual(package.call_args.kwargs['durable_signer'],'a'*64)
        self.assertTrue(key_paths); self.assertTrue(all(not key.exists() for key in key_paths))
