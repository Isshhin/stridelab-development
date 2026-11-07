"""Fixture conformance checks only; never import or execute the preflight collector."""
import importlib.util
from copy import deepcopy
from hashlib import sha256
import json
from pathlib import Path
import unittest


class V7Vectors(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(__file__).parents[1] / "tools/p2f_v7_closure_vectors.py"
        spec = importlib.util.spec_from_file_location("v7_vectors", path)
        cls.v = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.v)

    def test_full_objects_and_rejections(self):
        result = self.v.self_check()
        self.assertEqual(set(result["event_types"]), {1, 2, 3, 4, 5, 16, 17, 18, 19, 20, 21, 22, 23, 24})
        self.assertGreaterEqual(result["rejections"], 31)
        self.assertGreaterEqual(result["accepted_finalities"], 20)

    def test_generated_artifacts_are_exact(self):
        self.v.check_artifacts()

    def test_parser_and_uint64_edges(self):
        for n in (0, 23, 24, 255, 256, 65535, 65536, 2**32, 2**64 - 1):
            self.assertEqual(self.v.decode(self.v.encode(n)), n)
        for raw in (b"\xa2\x00\x01\x00\x02", b"\x18\x01", b"\xf6", b"\xf5", b"\x9f\xff", b"\xc0\x01", b"\xfb" + bytes(8)):
            with self.assertRaises(ValueError):
                self.v.decode(raw)

    def test_catalogue_replays_from_exact_named_history(self):
        data = json.loads(self.v.DATA.read_text(encoding="utf-8"))
        objects = {item["name"]: bytes.fromhex(item["hex"]) for item in data["objects"]}
        for item in data["objects"]:
            self.assertEqual(sha256(objects[item["name"]]).hexdigest(), item["sha256"])
        with self.v.Crypto() as crypto:
            start = self.v.initial(objects["genesis"], bytes.fromhex(data["genesis_pin_hex"]),
                                   bytes.fromhex(data["root_pin_hex"]), crypto)
            prefixes = {(): start}
            for case in data["scenarios"]:
                with self.subTest(case=case["name"]):
                    if case.get("parser_only"):
                        with self.assertRaisesRegex(ValueError, case["expected"]):
                            self.v.decode(objects[case["input"]])
                        continue
                    prefix = ()
                    for name in case["history"]:
                        following = prefix + (name,)
                        if following not in prefixes:
                            prefixes[following] = self.v.verify(objects[name], prefixes[prefix], crypto)
                        prefix = following
                    before = deepcopy(prefixes[prefix])
                    if case["expected"] in ("VALID_SUCCESSOR", "RETURN_PRIOR_FINAL_RESULT"):
                        after = self.v.verify(objects[case["input"]], prefixes[prefix], crypto)
                        if case["expected"] == "RETURN_PRIOR_FINAL_RESULT":
                            self.assertEqual(after, before)
                    else:
                        with self.assertRaisesRegex(ValueError, case["expected"]):
                            self.v.verify(objects[case["input"]], prefixes[prefix], crypto)
                    self.assertEqual(before, prefixes[prefix], "verification mutated prior authority")


if __name__ == "__main__":
    unittest.main()
