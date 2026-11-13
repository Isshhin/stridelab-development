"""Operational regressions only; all originals are generated in a disposable tree."""
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from p2_operator_rehearsal import create_register, current_register, run


class FilesystemRehearsal(unittest.TestCase):
    def test_full_rehearsal_with_stale_restore_fresh_process_and_sibling_survival(self):
        with tempfile.TemporaryDirectory(dir=".") as parent:
            root = Path(parent).resolve()
            sentinel = root/"unrelated.txt"
            sentinel.write_text("synthetic sentinel",encoding="utf-8")
            run(root)
            self.assertEqual(list(root.iterdir()),[sentinel])
            self.assertEqual(sentinel.read_text(encoding="utf-8"),"synthetic sentinel")

    def test_missing_independent_register_cannot_bootstrap_from_restored_backup(self):
        with tempfile.TemporaryDirectory(dir=".") as parent:
            root = Path(parent)
            stale = root/"backup-control.sqlite"
            create_register(stale)
            current = root/"current-control.sqlite"
            with self.assertRaises(sqlite3.OperationalError):
                with closing(current_register(current)):
                    self.fail("Recovery opened an unregistered authority")
            self.assertFalse(current.exists())

    def test_bad_arguments_do_not_echo_private_values(self):
        script = Path(__file__).with_name("p2_operator_rehearsal.py")
        result = subprocess.run([sys.executable,str(script),"--workspace-parent",".",
                                 "--unexpected-private-sentinel"],capture_output=True,text=True)
        self.assertEqual(result.returncode,2)
        self.assertEqual(result.stdout,"")
        self.assertEqual(result.stderr.strip(),"SYNTHETIC_REHEARSAL=FAIL INVALID_ARGUMENTS")
