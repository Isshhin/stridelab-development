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
        self.assertGreaterEqual(result["rejections"], 53)
        self.assertGreaterEqual(result["accepted_finalities"], 34)

    def test_generated_artifacts_are_exact(self):
        self.v.check_artifacts()

    def test_parser_and_uint64_edges(self):
        for n in (0, 23, 24, 255, 256, 65535, 65536, 2**32, 2**64 - 1):
            self.assertEqual(self.v.decode(self.v.encode(n)), n)
        for raw in (b"\xa2\x00\x01\x00\x02", b"\x18\x01", b"\xf6", b"\xf5", b"\x9f\xff", b"\xc0\x01", b"\xfb" + bytes(8)):
            with self.assertRaises(ValueError):
                self.v.decode(raw)

    def replay(self, data, names, crypto):
        objects = {item["name"]: bytes.fromhex(item["hex"]) for item in data["objects"]}
        state = self.v.initial(objects["genesis"], bytes.fromhex(data["genesis_pin_hex"]),
                               bytes.fromhex(data["root_pin_hex"]), crypto)
        for name in names:
            state = self.v.verify(objects[name], state, crypto)
        return state

    def test_signed_historical_cross_role_counterexamples(self):
        v = self.v
        with v.Crypto() as crypto:
            data = v.suite(crypto)
            bridge_case = next(c for c in data["scenarios"] if c["name"] == "policy-bridge")
            prior = self.replay(data, bridge_case["history"], crypto)
            old = v.decode(prior["policy"])
            proposed = v.decode(v.policy_fixture(1, prior["genesis"], crypto, old[6], old[7]))
            proposed[7] = prior["coordinator"][1]
            newraw = v.encode(proposed)
            payload = {0: v.h("policy", prior["policy"]), 1: v.h("policy", newraw),
                       2: 0, 3: 1, 4: prior["cs"] + 1}
            raw = v.make(prior, 5, payload, "independent/cross-role-bridge", crypto, newpolicy=newraw)
            self.assertEqual(sha256(raw).hexdigest(), "5cda3294b4c2888e6d4c39e3a2e2fa33d0ec0e96ba7caabbe5d5e1ba2777ae87")
            before = deepcopy(prior)
            with self.subTest(counterexample="F1a"):
                with self.assertRaisesRegex(ValueError, "ROLE_KEY_COLLISION"):
                    v.verify(raw, prior, crypto)
            self.assertEqual(prior, before)
            after = self.replay(data, bridge_case["history"] + ["policy-bridge"], crypto)
            raw = v.make(after, 3, {0: v.ident("independent/epoch"), 1: after["control"],
                                   2: old[4][0]}, "independent/retired-validator-to-gateway", crypto)
            self.assertEqual(sha256(raw).hexdigest(), "227c845f4f95e23e3a3b2ea195a21ca55e0753b71904c56a47d8752b7a83f059")
            before = deepcopy(after)
            with self.subTest(counterexample="F1b"):
                with self.assertRaisesRegex(ValueError, "EPOCH_KEY_REUSE"):
                    v.verify(raw, after, crypto)
            self.assertEqual(after, before)

    def test_role_history_survives_replay_retirement_and_replacement(self):
        v = self.v
        with v.Crypto() as crypto:
            data = v.suite(crypto)
            case = next(c for c in data["scenarios"] if c["name"] == "continuity-new-policy")
            names = case["history"] + [case["input"]]
            first, second = (self.replay(data, names, crypto) for _ in range(2))
            self.assertEqual(first["keyroles"], second["keyroles"])
            initial = self.replay(data, [], crypto)
            old = v.decode(initial["policy"])
            for key in old[4]:
                self.assertEqual(first["keyroles"][key], "validator")
            for key in v.decode(old[12])[3]:
                self.assertEqual(first["keyroles"][key], "witness")
            for key, role in ((old[6], "coordinator-root"), (old[7], "gateway-root"),
                              (first["genesis"][3], "log"), (first["genesis"][6], "genesis-root")):
                self.assertEqual(first["keyroles"][key], role)
            for name, role in (("coordinator-1", "coordinator-epoch"), ("coordinator-2", "coordinator-epoch"),
                               ("gateway-1", "gateway-epoch"), ("gateway-2", "gateway-epoch")):
                key = crypto.key(name)
                self.assertEqual(first["keyroles"][key], role)
                for t in (2, 3):
                    before = deepcopy(first)
                    raw = v.make(first, t, {0: v.ident(f"reuse/{name}/{t}"), 1: first["control"],
                                            2: key}, f"reuse/{name}/{t}", crypto)
                    with self.assertRaisesRegex(ValueError, "EPOCH_KEY_REUSE"):
                        v.verify(raw, first, crypto)
                    self.assertEqual(first, before)

    def test_bridge_retains_same_roles_but_cannot_restore_retired_keys(self):
        v = self.v
        with v.Crypto() as crypto:
            data = v.suite(crypto)
            case = next(c for c in data["scenarios"] if c["name"] == "policy-bridge")
            prior = self.replay(data, case["history"], crypto)
            old = v.decode(prior["policy"])
            retained = {**old, 2: 1}
            raw = v.encode(retained)
            payload = {0: v.h("policy", prior["policy"]), 1: v.h("policy", raw),
                       2: 0, 3: 1, 4: prior["cs"] + 1}
            after = v.verify(v.make(prior, 5, payload, "retain-all-roles", crypto, newpolicy=raw), prior, crypto)
            self.assertEqual(after["keyroles"], prior["keyroles"])
            # Rotate every policy role, then forbid resurrection even in its old role.
            replacement = v.policy_fixture(2, prior["genesis"], crypto,
                                            crypto.key("new-coordinator-root"), crypto.key("new-gateway-root"))
            payload = {0: v.h("policy", raw), 1: v.h("policy", replacement),
                       2: 1, 3: 2, 4: after["cs"] + 1}
            after = v.verify(v.make(after, 5, payload, "replace-all-roles", crypto,
                                    newpolicy=replacement), after, crypto)
            for key, role in prior["keyroles"].items():
                self.assertEqual(after["keyroles"][key], role)
            restored = v.encode({**old, 2: 3})
            payload = {0: v.h("policy", replacement), 1: v.h("policy", restored),
                       2: 2, 3: 3, 4: after["cs"] + 1}
            before = deepcopy(after)
            with self.assertRaisesRegex(ValueError, "ROLE_KEY_COLLISION"):
                v.verify(v.make(after, 5, payload, "restore-retired", crypto, newpolicy=restored), after, crypto)
            self.assertEqual(after, before)

    def test_new_fixtures_fail_only_historical_role_admission(self):
        v = self.v
        data = json.loads(v.DATA.read_text(encoding="utf-8"))
        objects = {item["name"]: bytes.fromhex(item["hex"]) for item in data["objects"]}
        with v.Crypto() as crypto:
            for name, expected in (
                ("reject-policy-cross-role-active-coordinator-as-gateway-root", "ROLE_KEY_COLLISION"),
                ("reject-gateway-epoch-cross-role-retired-validator", "EPOCH_KEY_REUSE")):
                with self.subTest(fixture=name):
                    case = next(c for c in data["scenarios"] if c["name"] == name)
                    prior = self.replay(data, case["history"], crypto)
                    before = deepcopy(prior)
                    raw = objects[name]
                    with self.assertRaisesRegex(ValueError, expected):
                        v.verify(raw, prior, crypto)
                    self.assertEqual(prior, before)
                    f = v.decode(raw)
                    key = v.decode(f[7][1])[7] if name.startswith("reject-policy") else v.decode(f[2])[15][2]
                    # Negative control: remove ONLY the historical role admission entry.
                    admitted = deepcopy(prior)
                    del admitted["keyroles"][key]
                    after = v.verify(raw, admitted, crypto)
                    self.assertEqual(len(after["leaves"]), len(prior["leaves"]) + 1)

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
