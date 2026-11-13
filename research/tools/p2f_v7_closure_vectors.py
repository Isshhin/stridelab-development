#!/usr/bin/env python3
"""Public invented v7 fixtures only. No operational keys, collector or hardware.

Uses Python stdlib and installed Node crypto; no package installation. The bounded
fixture oracle is not a production verifier, durable store or independent review.
"""
import argparse
import base64
from copy import deepcopy
from functools import lru_cache
from hashlib import sha256
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/research/phase2/p2-f-custody-authority-v7-closure-test-vectors-2026-11-07.md"
DATA = OUT.with_suffix(".json")
Z = bytes(32)
ORDER = 2**252 + 27742317777372353535851937790883648493
EVENTS = {1: "CONTINUITY_CHALLENGE", 2: "COORDINATOR_EPOCH_START", 3: "GATEWAY_EPOCH_START",
          4: "GATEWAY_EPOCH_REVOKE", 5: "POLICY_BRIDGE", 16: "AUTHORITY_ISSUED",
          17: "ESCAPE_COMMITTED", 18: "SEND_STARTED", 19: "SEND_COMPLETED", 20: "SEND_STOPPED",
          21: "SEND_UNKNOWN", 22: "WITHDRAWAL_FENCE", 23: "WITHDRAWAL_ACCOUNTED", 24: "DELETE"}
SIZES = {1: 4, 2: 3, 3: 3, 4: 2, 5: 5, 16: 3, 17: 7, 18: 3, 19: 4, 20: 4, 21: 4, 22: 2, 23: 3, 24: 2}
NUMERIC = {1: {3}, 4: {1}, 5: {2, 3, 4}, 17: {2, 4}, 20: {3}, 21: {3}, 22: {1}, 23: {2}, 24: {1}}


def need(ok, code):
    if not ok:
        raise ValueError(code)


def h(label, raw):
    return sha256(b"StrideLab/P2F/v1/" + label.encode("ascii") + b"\0" + raw).digest()


def ident(name):
    return sha256(("PUBLIC P2F v7 fixture ID/" + name).encode()).digest()


def head(major, n):
    need(type(n) is int and 0 <= n < 2**64, "INVALID_UINT64")
    if n < 24:
        return bytes([major * 32 + n])
    for size, ai in ((1, 24), (2, 25), (4, 26), (8, 27)):
        if n < 2**(size * 8):
            return bytes([major * 32 + ai]) + n.to_bytes(size, "big")


def encode(x):
    if type(x) is int:
        return head(0, x)
    if isinstance(x, bytes):
        return head(2, len(x)) + x
    if isinstance(x, list):
        return head(4, len(x)) + b"".join(map(encode, x))
    if isinstance(x, dict):
        need(all(type(k) is int and k >= 0 for k in x), "INVALID_MAP_KEY")
        return head(5, len(x)) + b"".join(encode(k) + encode(x[k]) for k in sorted(x))
    raise ValueError("PROHIBITED_CBOR")


def decode(raw):
    need(isinstance(raw, bytes) and len(raw) <= 32 * 1024 * 1024, "INVALID_CBOR_SIZE")
    pos = 0
    def read(depth=0):
        nonlocal pos
        need(depth < 64 and pos < len(raw), "TRUNCATED_CBOR")
        initial = raw[pos]; pos += 1
        major, ai = initial >> 5, initial & 31
        need(major in (0, 2, 4, 5) and ai <= 27, "PROHIBITED_CBOR")
        if ai < 24:
            n = ai
        else:
            size = (1, 2, 4, 8)[ai - 24]
            need(pos + size <= len(raw), "TRUNCATED_CBOR")
            n = int.from_bytes(raw[pos:pos + size], "big"); pos += size
            need(n >= (24, 256, 65536, 2**32)[ai - 24], "NON_MINIMAL_INTEGER")
        if major == 0:
            return n
        if major == 2:
            need(pos + n <= len(raw), "TRUNCATED_CBOR")
            value = raw[pos:pos + n]; pos += n
            return value
        need(n <= len(raw) - pos, "TRUNCATED_CBOR")
        if major == 4:
            return [read(depth + 1) for _ in range(n)]
        result, last = {}, -1
        for _ in range(n):
            key = read(depth + 1)
            need(type(key) is int and key > last, "DUPLICATE_OR_UNSORTED_KEY")
            last = key; result[key] = read(depth + 1)
        return result
    result = read()
    need(pos == len(raw) and encode(result) == raw, "NON_CANONICAL_CBOR")
    return result


def fields(x, n):
    need(type(x) is dict and set(x) == set(range(n)), "UNKNOWN_OR_MISSING_KEY")


def b32(x, zero=False):
    need(type(x) is bytes and len(x) == 32, "WRONG_LENGTH_BSTR")
    need(zero or x != Z, "WRONG_ZERO32")


def uint(x):
    need(type(x) is int and 0 <= x < 2**64, "INVALID_UINT64")


@lru_cache(maxsize=4096)
def prime_point(raw):
    """Fixture-only strict point admission; signatures use the platform library."""
    b32(raw)
    prime = 2**255 - 19
    d = -121665 * pow(121666, -1, prime) % prime
    encoded = int.from_bytes(raw, "little")
    y, sign = encoded % 2**255, encoded >> 255
    need(y < prime, "INVALID_ED25519_POINT")
    xx = (y*y - 1) * pow(d*y*y + 1, -1, prime) % prime
    x = pow(xx, (prime + 3)//8, prime)
    if x*x % prime != xx:
        x = x * pow(2, (prime - 1)//4, prime) % prime
    need(x*x % prime == xx and not (x == 0 and sign), "INVALID_ED25519_POINT")
    if x % 2 != sign:
        x = prime - x
    point = (x, y, 1, x*y % prime)
    def add(p, q):
        a = (p[1]-p[0])*(q[1]-q[0]) % prime
        b = (p[1]+p[0])*(q[1]+q[0]) % prime
        c = 2*d*p[3]*q[3] % prime
        dd = 2*p[2]*q[2] % prime
        e, f, g, hh = b-a, dd-c, dd+c, b+a
        return e*f % prime, g*hh % prime, f*g % prime, e*hh % prime
    total, multiple, scalar = (0, 1, 1, 0), point, ORDER
    while scalar:
        if scalar & 1:
            total = add(total, multiple)
        multiple = add(multiple, multiple); scalar >>= 1
    need(not (x == 0 and y == 1) and total[0] == 0 and (total[1]-total[2]) % prime == 0,
         "NON_PRIME_ED25519_POINT")


class Crypto:
    """Platform Ed25519; all private seeds are published test derivations."""
    SCRIPT = r'''
const c=require('node:crypto');
const rl=require('node:readline').createInterface({input:process.stdin});
const priv=s=>c.createPrivateKey({key:Buffer.concat([Buffer.from('302e020100300506032b657004220420','hex'),Buffer.from(s,'hex')]),format:'der',type:'pkcs8'});
rl.on('line',line=>{try{const q=JSON.parse(line);let out;
if(q.op==='key')out=c.createPublicKey(priv(q.seed)).export({format:'der',type:'spki'}).subarray(-32).toString('hex');
if(q.op==='sign')out=c.sign(null,Buffer.from(q.msg,'hex'),priv(q.seed)).toString('hex');
if(q.op==='verify')out=c.verify(null,Buffer.from(q.msg,'hex'),c.createPublicKey({key:Buffer.concat([Buffer.from('302a300506032b6570032100','hex'),Buffer.from(q.key,'hex')]),format:'der',type:'spki'}),Buffer.from(q.sig,'hex'));
process.stdout.write(JSON.stringify(out)+'\n');}catch(e){process.stdout.write(JSON.stringify({error:String(e)})+'\n');}});
'''

    def __enter__(self):
        self.p = subprocess.Popen(["node", "-e", self.SCRIPT], stdin=subprocess.PIPE,
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        self.seeds = {}
        return self

    def __exit__(self, *args):
        self.p.stdin.close(); self.p.wait(timeout=10)
        self.p.stdout.close(); self.p.stderr.close()

    def call(self, **q):
        self.p.stdin.write(json.dumps(q) + "\n"); self.p.stdin.flush()
        value = json.loads(self.p.stdout.readline())
        need(not isinstance(value, dict), "CRYPTO_LIBRARY_ERROR")
        return value

    def key(self, name):
        seed = sha256(("PUBLIC P2F v7 TEST SEED/" + name).encode()).digest()
        public = bytes.fromhex(self.call(op="key", seed=seed.hex()))
        self.seeds[public] = seed
        return public

    def sign(self, key, domain, raw):
        message = b"StrideLab/P2F/v1/" + domain.encode() + b"\0" + raw
        return bytes.fromhex(self.call(op="sign", seed=self.seeds[key].hex(), msg=message.hex()))

    def verify(self, key, domain, raw, sig):
        b32(key)
        need(type(sig) is bytes and len(sig) == 64, "WRONG_LENGTH_SIGNATURE")
        need(int.from_bytes(sig[32:], "little") < ORDER, "NON_CANONICAL_SIGNATURE")
        prime_point(key); prime_point(sig[:32])
        message = b"StrideLab/P2F/v1/" + domain.encode() + b"\0" + raw
        need(self.call(op="verify", key=key.hex(), msg=message.hex(), sig=sig.hex()), "INVALID_SIGNATURE")

    def known_answer(self):
        # RFC8032 section 7.1, test 1, public empty-message test material.
        seed = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        key = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        sig = ("e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555f"
               "b8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b")
        need(self.call(op="key", seed=seed) == key, "RFC8032_KEY_KAT")
        need(self.call(op="sign", seed=seed, msg="") == sig, "RFC8032_SIGNATURE_KAT")
        need(self.call(op="verify", key=key, sig=sig, msg=""), "RFC8032_VERIFY_KAT")
        need(not self.call(op="verify", key=key, sig=sig, msg="00"), "RFC8032_WRONG_MESSAGE")


def root(leaves):
    if not leaves:
        return sha256(b"").digest()
    if len(leaves) == 1:
        return leaves[0]
    k = 1 << ((len(leaves) - 1).bit_length() - 1)
    return sha256(b"\x01" + root(leaves[:k]) + root(leaves[k:])).digest()


def inclusion(leaves, index):
    if len(leaves) == 1:
        return []
    k = 1 << ((len(leaves) - 1).bit_length() - 1)
    if index < k:
        return inclusion(leaves[:k], index) + [root(leaves[k:])]
    return inclusion(leaves[k:], index - k) + [root(leaves[:k])]


def inclusion_root(leaf, index, size, path):
    need(0 <= index < size, "INVALID_INCLUSION_INDEX")
    def rebuild(i, n):
        if n == 1:
            return leaf
        k = 1 << ((n - 1).bit_length() - 1)
        value = rebuild(i, k) if i < k else rebuild(i - k, n - k)
        need(path, "INVALID_INCLUSION_PATH")
        sibling = path.pop(0); b32(sibling)
        return sha256(b"\x01" + (value + sibling if i < k else sibling + value)).digest()
    value = rebuild(index, size)
    need(not path, "INVALID_INCLUSION_PATH")
    return value


def artifact(origin, logkey, witnesses):
    def vkey(name, key, kind):
        typed = bytes([kind]) + key
        keyid = sha256(name + b"\n" + typed).digest()[:4].hex().encode()
        return name + b"+" + keyid + b"+" + base64.b64encode(typed)
    lines = [b"log " + vkey(origin, logkey, 1)]
    for i, key in enumerate(witnesses):
        name = f"w{i}".encode()
        lines.append(b"witness " + name + b" " + vkey(name, key, 4))
    return b"\n".join(lines + [b"group witnesses 3 w0 w1 w2 w3", b"quorum witnesses", b""])


def keyset(keys):
    need(type(keys) is list and len(keys) == 4, "INVALID_KEY_SET")
    for key in keys:
        prime_point(key)
    need(keys == sorted(set(keys)), "INVALID_KEY_SET")


def policy_roles(p):
    return {**dict.fromkeys(p[4], "validator"), **dict.fromkeys(decode(p[12])[3], "witness"),
            p[6]: "coordinator-root", p[7]: "gateway-root"}


def policy_check(raw, g, keyroles=None, retained=None):
    p = decode(raw); fields(p, 16)
    need(p[0] == p[8] == p[9] == p[10] == 1 and p[5] == 3, "INVALID_POLICY_ALGORITHM")
    uint(p[2]); prime_point(p[6]); prime_point(p[7]); keyset(p[4])
    need((p[1], p[13], p[14]) == (g[1], g[4], g[2]), "WRONG_POLICY_IDENTITY")
    need(p[11] == h("validator-set", encode(p[4])), "WRONG_VALIDATOR_SET")
    wp = decode(p[12]); fields(wp, 6)
    need((wp[0], wp[1], wp[2], wp[4], wp[5]) == (1, p[1], p[13], 3, 1), "INVALID_WITNESS_POLICY")
    keyset(wp[3])
    keys = p[4] + wp[3] + [p[6], p[7], g[3], g[6]]
    need(len(set(keys)) == len(keys), "ROLE_KEY_COLLISION")
    if keyroles is not None:
        for key, role in policy_roles(p).items():
            need(key not in keyroles or (keyroles[key] == role and retained is not None and retained.get(key) == role),
                 "ROLE_KEY_COLLISION")
    need(p[15] == artifact(p[14], g[3], wp[3]) and p[3] == sha256(p[15]).digest(), "C2SP_ARTIFACT_MISMATCH")
    return p


def payload_check(t, p):
    need(t in EVENTS, "UNKNOWN_ENUM"); fields(p, SIZES[t])
    for k, v in p.items():
        if k in NUMERIC.get(t, set()):
            uint(v)
        else:
            b32(v, zero=t in (1, 2, 3) and k == 1)
    enums = {4: (1, 3), 17: (4, 6), 20: (3, 3), 21: (3, 3), 22: (1, 3), 24: (1, 3)}
    if t in enums:
        k, maximum = enums[t]; need(1 <= p[k] <= maximum, "UNKNOWN_ENUM")


def initial(genesis, pin, rootpin, crypto):
    signed = decode(genesis); fields(signed, 2)
    g = decode(signed[0]); fields(g, 10)
    need(pin == h("genesis", genesis) and g[6] == rootpin, "UNPINNED_GENESIS")
    need(g[0] == g[7] == g[8] == g[9] == 1, "INVALID_GENESIS_ALGORITHM")
    for k in (1, 3, 4, 6):
        b32(g[k])
    prime_point(g[3]); prime_point(g[6])
    need(type(g[2]) is bytes and 1 <= len(g[2]) <= 255 and all(33 <= b <= 126 and b != 43 for b in g[2]), "INVALID_ORIGIN")
    need(g[4] == h("log-id", encode([g[2], g[3]])), "WRONG_LOG_ID")
    crypto.verify(rootpin, "sig/genesis", signed[0], signed[1])
    p = policy_check(g[5], g)
    need(p[2] == 0, "INVALID_GENESIS_EPOCH")
    return {"genesis": g, "pin": pin, "policy": g[5], "leaves": [], "cp": Z, "control": Z, "cs": 0,
            "coordinator": None, "gateway": None, "namespaces": {}, "operations": {},
            "ids": set(), "epochs": set(), "operation_proofs": {},
            "keyroles": {g[3]: "log", g[6]: "genesis-root", **policy_roles(p)}}


def auth_expected(s, e, p):
    t, v = e[1], e[15]
    rows = [(1, *s["coordinator"])] if s["coordinator"] else []
    if t == 2:
        rows += [(2, Z, p[6]), (1, v[0], v[2])]
    if t == 3:
        rows += [(4, Z, p[7]), (3, v[0], v[2])]
    if t == 4:
        rows += [(4, Z, p[7])]
    if t == 18:
        # A malformed proposal can carry old credentials; validation rejects it.
        need(s["gateway"] is not None, "STALE_GATEWAY_EPOCH")
        rows += [(3, *s["gateway"][:2])]
    return sorted(rows)


def accounted(ns):
    rows = []
    for (output, release), send in sorted(ns["releases"].items()):
        need(not send["started"] or send["terminal"] != Z, "INCOMPLETE_ACCOUNTING")
        rows.append([output, release, send["escape"], send["terminal"]])
    return h("accounted-outputs", encode(rows)), len(rows)


def transition(s, e):
    """Changes only a copy; verify commits it after cryptographic checks."""
    t, v, nsid = e[1], e[15], e[7]
    ns = s["namespaces"].get(nsid)
    if t <= 5:
        need(nsid == Z and e[8] == e[9] == 0, "WRONG_ZERO32")
        need(e[5] == s["cs"] + 1 and e[6] == e[12] == s["control"], "STALE_CONTROL_HEAD")
    else:
        b32(nsid)
        need(e[5] == s["cs"] and e[6] == s["control"], "STALE_CONTROL_HEAD")
        need(e[12] == (ns["head"] if ns else Z), "INVALID_PREDECESSOR")
        need(e[8] == (ns["seq"] + 1 if ns else 1) and e[9] == (ns["gen"] + 1 if ns else 1), "INVALID_SEQUENCE_GENERATION")
    need(e[10] == (s["coordinator"][0] if s["coordinator"] else Z), "STALE_COORDINATOR_EPOCH")
    need(s["coordinator"] is not None or t == 2, "COORDINATOR_NOT_BOOTSTRAPPED")
    if t in (17, 18):
        need(s["gateway"] is not None and not s["gateway"][2] and e[11] == s["gateway"][0], "STALE_GATEWAY_EPOCH")
    elif t not in (19, 20, 21):
        need(e[11] == Z, "WRONG_ZERO32")
    if t == 1:
        need(v[1] == s["cp"] and v[3] == s["cs"], "STALE_CHALLENGE")
    if t in (2, 3):
        need(v[1] == s["control"], "STALE_CONTROL_HEAD")
        need(v[0] not in s["epochs"] and v[2] not in s["keyroles"], "EPOCH_KEY_REUSE")
        s["epochs"].add(v[0])
        s["keyroles"][v[2]] = "coordinator-epoch" if t == 2 else "gateway-epoch"
        if t == 2:
            s["coordinator"] = (v[0], v[2])
        else:
            s["gateway"] = (v[0], v[2], False)
    if t == 4:
        need(s["gateway"] is not None and not s["gateway"][2] and v[0] == s["gateway"][0], "STALE_GATEWAY_EPOCH")
        s["gateway"] = (*s["gateway"][:2], True)
    if t == 16:
        need(ns is None, "NAMESPACE_REUSE")
        ns = {"state": 1, "head": Z, "seq": 0, "gen": 0, "releases": {}, "outputs": {}, "withdrawal": Z}
        s["namespaces"][nsid] = ns
    if t >= 17:
        need(ns is not None, "NAMESPACE_UNISSUED")
        if t in (17, 18):
            need(ns["state"] == 1, "WITHDRAWAL_FENCE_VIOLATION")
        if t == 17:
            key, descriptor = (v[0], v[5]), (v[1], v[2], v[3], v[4])
            need(key not in ns["releases"], "RELEASE_REUSE")
            need(v[0] not in ns["outputs"] or ns["outputs"][v[0]] == descriptor, "OUTPUT_BINDING_CONFLICT")
            ns["outputs"][v[0]] = descriptor
            ns["releases"][key] = {"escape": h("event", encode(e)), "gateway": e[11], "started": False, "send": Z, "terminal": Z}
        if t in (18, 19, 20, 21):
            send = ns["releases"].get((v[0], v[1])); need(send is not None, "MISSING_ESCAPE")
            if t == 18:
                need(not send["started"], "SEND_ALREADY_CONSUMED")
                need(e[11] == send["gateway"], "STALE_GATEWAY_EPOCH")
                send["started"], send["send"] = True, v[2]
            else:
                need(ns["state"] in (1, 2), "INVALID_STATE_TRANSITION")
                need(send["started"] and send["terminal"] == Z, "SEND_ALREADY_TERMINAL")
                need(v[2] == send["send"] and e[11] == send["gateway"], "WRONG_SEND_BINDING")
                send["terminal"] = h("event", encode(e))
        if t == 22:
            need(ns["state"] == 1, "INVALID_STATE_TRANSITION"); ns["state"], ns["withdrawal"] = 2, v[0]
        if t == 23:
            need(ns["state"] == 2 and v[0] == ns["withdrawal"], "INVALID_STATE_TRANSITION")
            need((v[1], v[2]) == accounted(ns), "ACCOUNTING_MISMATCH"); ns["state"] = 3
        if t == 24:
            need(ns["state"] == 3, "INVALID_STATE_TRANSITION"); ns["state"] = 4
    for k in {1: [0, 2], 16: [0], 17: [5, 6], 18: [2], 22: [0], 24: [0]}.get(t, []):
        need((t, k, v[k]) not in s["ids"], "IDENTIFIER_REUSE"); s["ids"].add((t, k, v[k]))
    if t <= 5:
        s["cs"], s["control"] = e[5], h("event", encode(e))
    else:
        ns["seq"], ns["gen"], ns["head"] = e[8], e[9], h("event", encode(e))


def certificate(e, bundle, auth, pin):
    return {0: 1, 1: e[2], 2: e[3], 3: e[4], 4: h("checkpoint", decode(bundle)[0]), 5: h("event", encode(e)),
            6: e[12], 7: e[6], 8: e[7], 9: e[5], 10: e[8], 11: e[9], 12: e[10], 13: e[11], 14: e[1], 15: 1,
            16: h("transparency", bundle), 17: sha256(encode(auth)).digest(), 18: pin}


def approval_check(aset, digest, keys, domain, message, crypto):
    fields(aset, 2); need(aset[0] == digest, "WRONG_APPROVAL_POLICY")
    rows = aset[1]; need(type(rows) is list and len(rows) in (3, 4), "INVALID_QUORUM")
    last = -1
    for row in rows:
        need(type(row) is list and len(row) == 2 and type(row[0]) is int and last < row[0] < 4, "INVALID_APPROVAL_INDEX")
        last = row[0]; crypto.verify(keys[last], domain, message, row[1])


def verify(raw, prior, crypto):
    f = decode(raw); fields(f, 9)
    need(f[0] == 1 and f[8] == prior["pin"], "WRONG_FINALITY_IDENTITY")
    r, e = decode(f[1]), decode(f[2]); fields(r, 6); fields(e, 16)
    need(r[0] == e[0] == 1, "UNKNOWN_ENUM")
    payload_check(e[1], e[15]); payload_check(r[1], r[5])
    for k in (2, 4, 6, 7, 10, 11, 12, 13, 14):
        b32(e[k], zero=k in (6, 7, 10, 11, 12))
    for k in (3, 5, 8, 9):
        uint(e[k])
    for k in (2, 3, 4):
        b32(r[k], zero=k == 4)
    need(e[14] == h("request", f[1]), "WRONG_REQUEST_DIGEST")
    need((r[1], r[2], r[3], r[4], r[5]) == (e[1], e[13], e[2], e[7], e[15]), "REQUEST_ENVELOPE_MISMATCH")
    if e[13] in prior["operations"]:
        need(prior["operations"][e[13]] == f[1] and prior["operation_proofs"][e[13]] == raw,
             "DUPLICATE_OPERATION_CONFLICT")
        return deepcopy(prior)
    p = policy_check(prior["policy"], prior["genesis"])
    need((e[2], e[3], e[4]) == (p[1], p[2], h("policy", prior["policy"])), "STALE_POLICY")
    need(type(f[7]) is list and len(f[7]) == (2 if e[1] == 5 else 1) and f[7][0] == prior["policy"], "WRONG_POLICY_OBJECTS")
    policies = [p]
    if e[1] == 5:
        new = policy_check(f[7][1], prior["genesis"], prior["keyroles"], policy_roles(p))
        need(new[2] == p[2] + 1, "INVALID_BRIDGE_EPOCH")
        need(e[15] == {0: h("policy", f[7][0]), 1: h("policy", f[7][1]), 2: p[2], 3: new[2], 4: prior["cs"] + 1}, "BRIDGE_BINDING_MISMATCH")
        policies.append(new)
    expected_auth = auth_expected(prior, e, p)
    s = deepcopy(prior); transition(s, e)
    auth = f[6]; need(type(auth) is list, "INVALID_AUTHENTICATIONS")
    for item in auth:
        fields(item, 4); b32(item[1], zero=item[0] in (2, 4)); b32(item[2])
    need([(a[0], a[1], a[2]) for a in auth] == expected_auth, "WRONG_AUTHENTICATION_SET")
    domains = {1: "sig/coordinator", 2: "sig/coordinator-root", 3: "sig/gateway", 4: "sig/gateway-root"}
    for item in auth:
        crypto.verify(item[2], domains[item[0]], encode({0: f[1], 1: f[2]}), item[3])
    bundle = decode(f[3]); fields(bundle, 5)
    cp = decode(bundle[0]); fields(cp, 2); cb = decode(cp[0]); fields(cb, 9)
    size, leaf = len(prior["leaves"]) + 1, sha256(b"\0" + f[2]).digest()
    need(cb == {0: 1, 1: p[1], 2: p[13], 3: p[14], 4: size, 5: root(prior["leaves"] + [leaf]),
                6: p[2], 7: h("policy", prior["policy"]), 8: prior["cp"]}, "WRONG_CHECKPOINT")
    crypto.verify(prior["genesis"][3], "sig/checkpoint", cp[0], cp[1])
    cpd = h("checkpoint", bundle[0])
    ip = decode(bundle[1]); fields(ip, 4)
    need((ip[0], ip[1], ip[2]) == (cpd, size - 1, size), "WRONG_INCLUSION_CHECKPOINT")
    need(type(ip[3]) is list and inclusion_root(leaf, ip[1], ip[2], list(ip[3])) == cb[5], "WRONG_INCLUSION_ROOT")
    xp = decode(bundle[2]); fields(xp, 7)
    need(xp == {0: prior["cp"], 1: size - 1, 2: root(prior["leaves"]), 3: cpd, 4: size, 5: cb[5], 6: prior["leaves"]}, "WRONG_CONSISTENCY_CHECKPOINT")
    wps = list(dict.fromkeys(pp[12] for pp in policies))
    need(bundle[3] == wps and type(bundle[4]) is list and len(bundle[4]) == len(wps), "WRONG_WITNESS_POLICIES")
    for wpraw, aset in zip(wps, bundle[4]):
        wp, wd = decode(wpraw), h("witness-policy", wpraw)
        approval_check(aset, wd, wp[3], "sig/witness", encode({0: bundle[0], 1: wd}), crypto)
    need(f[4] == encode(certificate(e, f[3], auth, prior["pin"])), "CERTIFICATE_BINDING_MISMATCH")
    need(type(f[5]) is list and len(f[5]) == len(policies), "INVALID_AUTHORITY_QUORUMS")
    for i, (pp, aset) in enumerate(zip(policies, f[5])):
        domain = ("sig/bridge-old" if i == 0 else "sig/bridge-new") if e[1] == 5 else "sig/validator"
        approval_check(aset, h("policy", f[7][i]), pp[4], domain, f[4], crypto)
    if e[1] == 5:
        s["policy"] = f[7][1]
        s["keyroles"].update(policy_roles(new))
    s["leaves"].append(leaf); s["cp"] = cpd; s["operations"][e[13]] = f[1]
    s["operation_proofs"][e[13]] = raw
    return s


def make(s, t, payload, name, crypto, newpolicy=None, mutate_event=None, mutate_request=None,
         mutate_bundle=None, auth_domain=None, approval_domain=None, alternate_new_body=False):
    p, nsid = decode(s["policy"]), Z if t <= 5 else ident("namespace")
    ns = s["namespaces"].get(nsid)
    r = {0: 1, 1: t, 2: ident("operation/" + name), 3: p[1], 4: nsid, 5: deepcopy(payload)}
    e = {0: 1, 1: t, 2: p[1], 3: p[2], 4: h("policy", s["policy"]), 5: s["cs"] + int(t <= 5), 6: s["control"],
         7: nsid, 8: 0 if t <= 5 else (ns["seq"] + 1 if ns else 1), 9: 0 if t <= 5 else (ns["gen"] + 1 if ns else 1),
         10: s["coordinator"][0] if s["coordinator"] else Z, 11: Z,
         12: s["control"] if t <= 5 else (ns["head"] if ns else Z), 13: r[2], 14: Z, 15: deepcopy(payload)}
    if t in (17, 18):
        e[11] = s["gateway"][0] if s["gateway"] else ident("missing-gateway")
    if t in (19, 20, 21):
        e[11] = ns["releases"][(payload[0], payload[1])]["gateway"]
    if mutate_request:
        mutate_request(r)
    e[14] = h("request", encode(r))
    required = auth_expected(s, e, p)
    if mutate_event:
        mutate_event(e)
    rb, eb = encode(r), encode(e)
    domains = {1: "sig/coordinator", 2: "sig/coordinator-root", 3: "sig/gateway", 4: "sig/gateway-root"}
    auth = [{0: role, 1: epoch, 2: key, 3: crypto.sign(key, auth_domain or domains[role], encode({0: rb, 1: eb}))}
            for role, epoch, key in required]
    leaves = s["leaves"] + [sha256(b"\0" + eb).digest()]
    cb = encode({0: 1, 1: p[1], 2: p[13], 3: p[14], 4: len(leaves), 5: root(leaves), 6: p[2], 7: h("policy", s["policy"]), 8: s["cp"]})
    cp = encode({0: cb, 1: crypto.sign(s["genesis"][3], "sig/checkpoint", cb)})
    cpd = h("checkpoint", cp)
    policies = [s["policy"]] + ([newpolicy] if newpolicy is not None else [])
    wps = list(dict.fromkeys(decode(pp)[12] for pp in policies))
    was = []
    for wpraw in wps:
        wd = h("witness-policy", wpraw)
        was.append({0: wd, 1: [[i, crypto.sign(key, "sig/witness", encode({0: cp, 1: wd}))] for i, key in enumerate(decode(wpraw)[3][:3])]})
    bundle = {0: cp, 1: encode({0: cpd, 1: len(leaves) - 1, 2: len(leaves), 3: inclusion(leaves, len(leaves) - 1)}),
              2: encode({0: s["cp"], 1: len(leaves) - 1, 2: root(s["leaves"]), 3: cpd, 4: len(leaves), 5: root(leaves), 6: s["leaves"]}),
              3: wps, 4: was}
    if mutate_bundle:
        mutate_bundle(bundle)
    bb = encode(bundle); body = encode(certificate(e, bb, auth, s["pin"]))
    sets = []
    for index, policy in enumerate(policies):
        domain = ("sig/bridge-old" if index == 0 else "sig/bridge-new") if t == 5 else "sig/validator"
        signbody = body
        if index == 1 and alternate_new_body:
            alternate = decode(body); alternate[4] = ident("unrelated-checkpoint"); signbody = encode(alternate)
        sets.append({0: h("policy", policy), 1: [[i, crypto.sign(key, approval_domain or domain, signbody)] for i, key in enumerate(decode(policy)[4][:3])]})
    return encode({0: 1, 1: rb, 2: eb, 3: bb, 4: body, 5: sets, 6: auth, 7: policies, 8: s["pin"]})


def policy_fixture(epoch, g, crypto, cr, gr):
    validators = sorted(crypto.key(f"validator/{epoch}/{i}") for i in range(4))
    witnesses = sorted(crypto.key(f"witness/{epoch}/{i}") for i in range(4))
    wp = encode({0: 1, 1: g[1], 2: g[4], 3: witnesses, 4: 3, 5: 1})
    art = artifact(g[2], g[3], witnesses)
    return encode({0: 1, 1: g[1], 2: epoch, 3: sha256(art).digest(), 4: validators, 5: 3, 6: cr, 7: gr,
                   8: 1, 9: 1, 10: 1, 11: h("validator-set", encode(validators)), 12: wp, 13: g[4], 14: g[2], 15: art})


def suite(crypto):
    crypto.known_answer()
    g = {0: 1, 1: ident("trust-domain"), 2: b"p2f.example/log", 3: crypto.key("log"), 4: Z, 5: b"", 6: crypto.key("genesis-root"), 7: 1, 8: 1, 9: 1}
    g[4] = h("log-id", encode([g[2], g[3]]))
    cr, gr = crypto.key("coordinator-root"), crypto.key("gateway-root")
    old, new = policy_fixture(0, g, crypto, cr, gr), policy_fixture(1, g, crypto, cr, gr)
    g[5] = old
    genesis = encode({0: encode(g), 1: crypto.sign(g[6], "sig/genesis", encode(g))})
    pin = h("genesis", genesis); s = initial(genesis, pin, g[6], crypto)
    records, scenarios, states, history = [], [], {}, []
    def record(name, kind, raw, label=None):
        item = {"name": name, "type": kind, "hex": raw.hex(), "sha256": sha256(raw).hexdigest()}
        if label:
            item["domain_digest"] = h(label, raw).hex()
        records.append(item)
    record("genesis", "GenesisTrustManifestV1", genesis, "genesis")
    record("genesis-body", "GenesisBodyV1", encode(g))
    for name, p in (("old", old), ("new", new)):
        record(name + "-policy", "PolicyV1", p, "policy")
        record(name + "-witness-policy", "WitnessPolicyV1", decode(p)[12], "witness-policy")
        record(name + "-c2sp-artifact", "C2SPPolicyArtifactBytes", decode(p)[15])

    def add(name, t, payload, **kw):
        nonlocal s
        states[name] = (deepcopy(s), list(history))
        raw = make(s, t, payload, name, crypto, **kw); s = verify(raw, s, crypto)
        record(name, "FinalityProofV1", raw)
        f = decode(raw); bundle = decode(f[3])
        for suffix, kind, value, label in (
            ("request", "RequestV1", f[1], "request"), ("event", "EventEnvelopeV1", f[2], "event"),
            ("checkpoint", "CheckpointV1", bundle[0], "checkpoint"), ("inclusion", "InclusionProofV1", bundle[1], None),
            ("consistency", "ConsistencyProofV1", bundle[2], None), ("bundle", "TransparencyBundleV1", f[3], "transparency"),
            ("body", "CertificateBodyV1", f[4], "cert"), ("authority-approvals", "ApprovalSetsV1", encode(f[5]), None),
            ("witness-approvals", "WitnessApprovalSetsV1", encode(bundle[4]), None),
            ("authentication", "DetachedAuthenticationsV1", encode(f[6]), None)):
            record(name + "/" + suffix, kind, value, label)
        scenarios.append({"name": name, "history": list(history), "input": name, "expected": "VALID_SUCCESSOR", "event_type": t})
        history.append(name)

    add("coordinator-start", 2, {0: ident("coord-epoch-1"), 1: s["control"], 2: crypto.key("coordinator-1")})
    add("continuity", 1, {0: ident("challenge-1"), 1: s["cp"], 2: ident("nonce-1"), 3: s["cs"]})
    add("gateway-start", 3, {0: ident("gateway-epoch-1"), 1: s["control"], 2: crypto.key("gateway-1")})
    grant = {0: ident("grant"), 1: ident("subject"), 2: ident("scope")}; add("authority-issued", 16, grant)
    escapes, starts = {}, {}
    def escape(n, output=None):
        out = output or n
        payload = {0: ident("output-" + out), 1: ident("content-" + out), 2: 4096, 3: ident("recipient"), 4: 1, 5: ident("release-" + n), 6: ident("authorization-" + n)}
        escapes[n] = payload; add("escape-" + n, 17, payload)
        starts[n] = {0: payload[0], 1: payload[5], 2: ident("send-" + n)}; add("start-" + n, 18, starts[n])
    escape("a"); add("completed", 19, {**starts["a"], 3: ident("completion")})
    escape("b"); add("stopped", 20, {**starts["b"], 3: 3})
    escape("c"); add("gateway-revoke", 4, {0: s["gateway"][0], 1: 2})
    add("unknown-after-revoke", 21, {**starts["c"], 3: 1})
    add("gateway-rotation", 3, {0: ident("gateway-epoch-2"), 1: s["control"], 2: crypto.key("gateway-2")})
    escape("c-new-explicit", output="c"); add("new-exposure-completed", 19, {**starts["c-new-explicit"], 3: ident("new-completion")})
    escape("d"); add("withdrawal-fence", 22, {0: ident("withdrawal"), 1: 1})
    add("unknown-after-fence", 21, {**starts["d"], 3: 2})
    ad, count = accounted(s["namespaces"][ident("namespace")])
    add("withdrawal-accounted", 23, {0: ident("withdrawal"), 1: ad, 2: count})
    add("delete", 24, {0: ident("tombstone"), 1: 1})
    bridge = {0: h("policy", old), 1: h("policy", new), 2: 0, 3: 1, 4: s["cs"] + 1}
    add("policy-bridge", 5, bridge, newpolicy=new)
    add("coordinator-rotation", 2, {0: ident("coord-epoch-2"), 1: s["control"], 2: crypto.key("coordinator-2")})
    add("continuity-new-policy", 1, {0: ident("challenge-2"), 1: s["cp"], 2: ident("nonce-2"), 3: s["cs"]})
    original_start = next(bytes.fromhex(item["hex"]) for item in records if item["name"] == "start-a")
    need(verify(original_start, s, crypto) == s, "EXACT_OPERATION_REPLAY_MUTATED_STATE")
    scenarios.append({"name": "exact-final-replay-after-policy-change", "history": list(history),
                      "input": "start-a", "expected": "RETURN_PRIOR_FINAL_RESULT"})

    def rejection(name, state_name, t, payload, expected, **kw):
        prior, hist = states[state_name]
        raw = make(prior, t, payload, "reject/" + name, crypto, **kw)
        try:
            verify(raw, prior, crypto)
        except ValueError as error:
            need(str(error) == expected, name + ": expected " + expected + ", got " + str(error))
        else:
            raise AssertionError("accepted negative fixture " + name)
        record(name, "RejectedFinalityProofV1", raw)
        scenarios.append({"name": name, "history": hist, "input": name, "expected": expected})
    def evfield(key, value):
        return lambda e: e.__setitem__(key, value)
    for name, key, value, code in (
        ("wrong-request-digest", 14, ident("wrong-request"), "WRONG_REQUEST_DIGEST"),
        ("request-envelope-mismatch", 13, ident("other-operation"), "REQUEST_ENVELOPE_MISMATCH"),
        ("wrong-predecessor", 12, Z, "INVALID_PREDECESSOR"),
        ("stale-control-head", 6, ident("old-control"), "STALE_CONTROL_HEAD"),
        ("stale-coordinator-epoch", 10, ident("old-coordinator"), "STALE_COORDINATOR_EPOCH"),
        ("stale-gateway-epoch", 11, ident("old-gateway"), "STALE_GATEWAY_EPOCH"),
        ("wrong-policy-digest", 4, ident("other-policy"), "STALE_POLICY"),
        ("wrong-sequence", 8, 99, "INVALID_SEQUENCE_GENERATION"),
        ("wrong-generation", 9, 0, "INVALID_SEQUENCE_GENERATION")):
        rejection(name, "start-a", 18, starts["a"], code, mutate_event=evfield(key, value))
    rejection("wrong-zero32-placement", "start-a", 18, starts["a"], "WRONG_ZERO32",
              mutate_request=lambda r: r.__setitem__(4, Z), mutate_event=evfield(7, Z))
    rejection("wrong-signature-domain", "start-a", 18, starts["a"], "INVALID_SIGNATURE", auth_domain="sig/gateway-root")
    rejection("wrong-validator-domain", "start-a", 18, starts["a"], "INVALID_SIGNATURE", approval_domain="sig/bridge-old")
    def change_nested(key, field, value):
        def change(bundle):
            obj = decode(bundle[key]); obj[field] = value; bundle[key] = encode(obj)
        return change
    def wrong_cp(bundle):
        cp = decode(bundle[0]); body = decode(cp[0]); body[5] = ident("unrelated-root"); cp[0] = encode(body); bundle[0] = encode(cp)
    for name, fn, expected in (
        ("wrong-checkpoint", wrong_cp, "WRONG_CHECKPOINT"),
        ("inclusion-other-checkpoint", change_nested(1, 0, ident("other-checkpoint")), "WRONG_INCLUSION_CHECKPOINT"),
        ("inclusion-wrong-path", change_nested(1, 3, []), "INVALID_INCLUSION_PATH"),
        ("consistency-unrelated-checkpoint", change_nested(2, 0, ident("other-checkpoint")), "WRONG_CONSISTENCY_CHECKPOINT"),
        ("consistency-wrong-prefix", change_nested(2, 6, []), "WRONG_CONSISTENCY_CHECKPOINT"),
        ("wrong-witness-policy", lambda b: b.__setitem__(3, [decode(new)[12]]), "WRONG_WITNESS_POLICIES"),
        ("bad-witness-signature", lambda b: b[4][0][1][0].__setitem__(1, bytes(64)), "WRONG_ZERO32")):
        rejection(name, "start-a", 18, starts["a"], expected, mutate_bundle=fn)
    rejection("bridge-mismatched-old-new-body", "policy-bridge", 5, bridge, "INVALID_SIGNATURE", newpolicy=new, alternate_new_body=True)
    rejection("bridge-wrong-old-epoch", "policy-bridge", 5, {**bridge, 2: 1}, "BRIDGE_BINDING_MISMATCH", newpolicy=new)
    colliding = decode(new); colliding[7] = states["policy-bridge"][0]["coordinator"][1]
    colliding = encode(colliding)
    rejection("reject-policy-cross-role-active-coordinator-as-gateway-root", "policy-bridge", 5,
              {**bridge, 1: h("policy", colliding)}, "ROLE_KEY_COLLISION", newpolicy=colliding)
    rejection("reject-gateway-epoch-cross-role-retired-validator", "coordinator-rotation", 3,
              {0: ident("retired-validator-gateway-epoch"), 1: states["coordinator-rotation"][0]["control"],
               2: decode(old)[4][0]}, "EPOCH_KEY_REUSE")
    rejection("duplicate-send-started", "completed", 18, starts["a"], "SEND_ALREADY_CONSUMED")
    rejection("send-unknown-then-same-release", "escape-c-new-explicit", 18, starts["c"], "SEND_ALREADY_CONSUMED")
    rejection("output-after-fence", "unknown-after-fence", 17, escapes["d"], "WITHDRAWAL_FENCE_VIOLATION")
    rejection("send-after-fence", "unknown-after-fence", 18, starts["d"], "WITHDRAWAL_FENCE_VIOLATION")
    rejection("namespace-reuse-after-delete", "policy-bridge", 16, grant, "NAMESPACE_REUSE")
    rejection("incomplete-accounting", "unknown-after-fence", 23, {0: ident("withdrawal"), 1: ident("bogus-accounting"), 2: 4}, "INCOMPLETE_ACCOUNTING")
    rejection("revoked-gateway-start", "unknown-after-revoke", 17, escapes["c"], "STALE_GATEWAY_EPOCH")
    rejection("revoked-gateway-send-start", "unknown-after-revoke", 18, starts["c"], "STALE_GATEWAY_EPOCH")
    rejection("unknown-event-enum", "start-a", 18, starts["a"], "UNKNOWN_ENUM", mutate_event=evfield(1, 99))
    rejection("unknown-event-key", "start-a", 18, starts["a"], "UNKNOWN_OR_MISSING_KEY", mutate_event=evfield(99, 1))
    rejection("wrong-length-bstr", "start-a", 18, starts["a"], "WRONG_LENGTH_BSTR", mutate_event=evfield(14, bytes(31)))
    def reject_raw(name, source, state_name, expected, change):
        prior, hist = states[state_name]
        original = next(bytes.fromhex(item["hex"]) for item in records if item["name"] == source)
        f = decode(original); change(f); raw = encode(f)
        try:
            verify(raw, prior, crypto)
        except ValueError as error:
            need(str(error) == expected, name + "/" + str(error))
        else:
            raise AssertionError("accepted " + name)
        record(name, "RejectedFinalityProofV1", raw)
        scenarios.append({"name": name, "history": hist, "input": name, "expected": expected})
    reject_raw("bridge-missing-old-quorum", "policy-bridge", "policy-bridge", "INVALID_AUTHORITY_QUORUMS",
               lambda f: f.__setitem__(5, [f[5][1]]))
    reject_raw("duplicate-validator-index", "start-a", "start-a", "INVALID_APPROVAL_INDEX",
               lambda f: f[5][0][1][1].__setitem__(0, 0))
    reject_raw("authority-quorum-too-small", "start-a", "start-a", "INVALID_QUORUM",
               lambda f: f[5][0].__setitem__(1, f[5][0][1][:2]))
    reject_raw("certificate-checkpoint-substitution", "start-a", "start-a", "CERTIFICATE_BINDING_MISMATCH",
               lambda f: f.__setitem__(4, encode({**decode(f[4]), 4: ident("checkpoint-X")})))
    reject_raw("history-gap-missing-send-start", "completed", "start-a", "INVALID_PREDECESSOR", lambda f: None)
    malformed = {"duplicate-map-key": "a200010002", "non-minimal-integer": "1801", "tag-prohibited": "c001",
                 "float-prohibited": "fb3ff0000000000000", "null-prohibited": "f6", "bool-prohibited": "f5",
                 "indefinite-map": "bf0001ff", "indefinite-array": "9f01ff", "indefinite-bstr": "5f4100ff",
                 "text-prohibited": "6161", "negative-prohibited": "20", "trailing-data": "0102"}
    for name, rawhex in malformed.items():
        raw = bytes.fromhex(rawhex)
        try:
            decode(raw)
        except ValueError as error:
            expected = str(error)
        else:
            raise AssertionError("parser accepted " + name)
        record(name, "MalformedCBOR", raw)
        scenarios.append({"name": name, "history": [], "input": name, "expected": expected, "parser_only": True})

    for terminal, why in ((19, ident("late-completion")), (20, 1)):
        prior, hist = states["unknown-after-fence"]; name = "terminal-after-fence-" + str(terminal)
        raw = make(prior, terminal, {**starts["d"], 3: why}, name, crypto); verify(raw, prior, crypto)
        record(name, "FinalityProofV1", raw)
        scenarios.append({"name": name, "history": hist, "input": name, "expected": "VALID_SUCCESSOR", "event_type": terminal})
    for stage in ("escape-a", "start-a"):
        prior, hist = states[stage]; name = "fence-before-" + stage
        raw = make(prior, 22, {0: ident(name), 1: 1}, name, crypto); fenced = verify(raw, prior, crypto)
        record(name, "FinalityProofV1", raw)
        scenarios.append({"name": name, "history": hist, "input": name, "expected": "VALID_SUCCESSOR", "event_type": 22})
        t, payload = (17, escapes["a"]) if stage == "escape-a" else (18, starts["a"])
        failed = make(fenced, t, payload, "late-" + name, crypto)
        try:
            verify(failed, fenced, crypto)
        except ValueError as error:
            need(str(error) == "WITHDRAWAL_FENCE_VIOLATION", "RACE_RESULT")
        else:
            raise AssertionError("late operation passed fence")
        record("late-" + name, "RejectedFinalityProofV1", failed)
        scenarios.append({"name": "late-" + name, "history": hist + [name], "input": "late-" + name, "expected": "WITHDRAWAL_FENCE_VIOLATION"})
        ad, count = accounted(fenced["namespaces"][ident("namespace")]); aname = "accounted-" + name
        ar = make(fenced, 23, {0: ident(name), 1: ad, 2: count}, aname, crypto); verify(ar, fenced, crypto)
        record(aname, "FinalityProofV1", ar)
        scenarios.append({"name": aname, "history": hist + [name], "input": aname, "expected": "VALID_SUCCESSOR", "event_type": 23})

    # Two full legal proposals at one predecessor; locks must retain one only.
    prior, hist = states["start-a"]
    other = make(prior, 18, {**starts["a"], 2: ident("alternate-send")}, "competing-start-a", crypto)
    verify(other, prior, crypto)
    record("competing-start-a", "FinalityProofV1", other)
    scenarios.append({"name": "competing-start-a", "history": hist, "input": "competing-start-a",
                      "expected": "VALID_SUCCESSOR", "event_type": 18})
    aenv, benv = decode(original_start)[2], decode(other)[2]
    pred, ca, cb = decode(aenv)[12], h("event", aenv), h("event", benv)
    durable = {pred: ca}; restarted = dict(durable)
    need(restarted[pred] == ca and restarted[pred] != cb, "LOCK_RESTART_CHECK")
    schedules = [
        {"schedule": "crash-before-durable-lock", "locks": [], "expected": "RETRY_ALLOWED"},
        {"schedule": "durable-response-lost", "locks": [[pred.hex(), ca.hex()]], "retry": ca.hex(), "expected": "SAME_CANDIDATE_ONLY"},
        {"schedule": "approval-then-crash", "locks": [[pred.hex(), ca.hex()]], "retry": cb.hex(), "expected": "CLOSED_PENDING_RECONCILIATION"},
        {"schedule": "two-vs-two", "locks": [ca.hex(), ca.hex(), cb.hex(), cb.hex()], "expected": "PERMANENT_UNAVAILABILITY"},
        {"schedule": "pending-no-quorum", "locks": [ca.hex()], "expected": "CLOSED_PENDING_RECONCILIATION"},
        {"schedule": "one-malicious-withholding-one-unavailable", "honest_available": 2, "expected": "CLOSED"},
        {"schedule": "two-unavailable", "honest_available": 2, "expected": "CLOSED"},
        {"schedule": "two-compromised", "byzantine": 2, "expected": "NO_SECURITY_CLAIM"}]
    for case in schedules:
        name = case["schedule"]
        if name == "crash-before-durable-lock":
            actual = "RETRY_ALLOWED" if not case["locks"] else "CLOSED_PENDING_RECONCILIATION"
        elif name in ("durable-response-lost", "approval-then-crash"):
            recovered = dict(case["locks"])
            actual = "SAME_CANDIDATE_ONLY" if recovered[pred.hex()] == case["retry"] else "CLOSED_PENDING_RECONCILIATION"
        elif name == "two-vs-two":
            actual = "PERMANENT_UNAVAILABILITY" if max(case["locks"].count(v) for v in case["locks"]) < 3 else "QUORUM"
        elif name == "pending-no-quorum":
            actual = "CLOSED_PENDING_RECONCILIATION" if len(case["locks"]) < 3 else "QUORUM"
        elif "honest_available" in case:
            actual = "CLOSED" if case["honest_available"] < 3 else "QUORUM"
        else:
            actual = "NO_SECURITY_CLAIM" if case["byzantine"] > 1 else "WITHIN_ASSUMPTION"
        need(actual == case["expected"], "LOCK_LIFECYCLE_SCHEDULE_FAILED/" + name)
    accepted = [x for x in scenarios if x["expected"] == "VALID_SUCCESSOR"]
    replays = sum(x["expected"] == "RETURN_PRIOR_FINAL_RESULT" for x in scenarios)
    summary = {"accepted_finalities": len(accepted), "rejections": len(scenarios) - len(accepted) - replays, "exact_replays": replays,
               "event_types": sorted(set(x["event_type"] for x in accepted)), "objects": len(records),
               "lock_lifecycle_schedules": len(schedules), "rfc8032_known_answer": "PASS"}
    return {"profile": "StrideLab/P2F/v1 bounded v7 correction", "test_only": True,
            "genesis_pin_hex": pin.hex(), "root_pin_hex": g[6].hex(),
            "seed_derivation": "SHA256(UTF8('PUBLIC P2F v7 TEST SEED/' + role_name)); never operational",
            "summary": summary, "objects": records, "scenarios": scenarios, "lock_lifecycle_schedules": schedules}


@lru_cache(maxsize=1)
def generated():
    with Crypto() as crypto:
        data = suite(crypto)
    raw = json.dumps(data, indent=2, sort_keys=True) + "\n"; summary = data["summary"]
    lines = ["# P2-F v7 closure canonical test vectors", "",
             "Normative fixtures subordinate to [the v7 correction closure](p2-f-custody-authority-v7-correction-closure-2026-11-07.md). Earlier payload-only vectors and six-event output are historical/non-authoritative.", "",
             "Complete bytes, digests, signatures and starting-history references: [JSON fixture](p2-f-custody-authority-v7-closure-test-vectors-2026-11-07.json). PUBLIC invented test material only; never deploy these keys/IDs.", "",
             "Regenerate: `python research/tools/p2f_v7_closure_vectors.py`; check drift: add `--check`. Python stdlib + installed Node crypto. No package installation, operational key provisioning, collector or hardware.", "",
             "## Exact self-check results", "", "```json", json.dumps(summary, indent=2, sort_keys=True), "```", "",
             "PASS: every accepted finality is parsed, request/state/proof-bound and signature-verified; each negative actually rejects at its named check. RFC8032 empty-message known-answer sign/verify and wrong-message rejection PASS. Synthetic lock/lifecycle records specify obligations, not real durability/independence tests.", "",
             f"JSON raw UTF-8 SHA256: `{sha256(raw.encode()).hexdigest()}`.", "",
             "## Scenario catalogue", "", "| Scenario | Event | Expected result |", "|---|---|---|"]
    for item in data["scenarios"]:
        lines.append(f"| {item['name']} | {EVENTS.get(item.get('event_type'), 'negative fixture')} | `{item['expected']}` |")
    lines += ["", "## Reproduction and limits", "",
              "Object `hex` contains complete bytes; `sha256` hashes those bytes. `domain_digest` includes the specified P2-F prefix and ONE NUL. Requests, envelopes, signed checkpoints, inclusion/extension proofs, bodies, witness approvals, authority approvals and authentication are indexed separately as well as embedded in finalities. Bridge includes both independent old/new ordered validator/witness sets on one identical body.", "",
              "Initialize each scenario from pinned signed genesis, replay its `history` object names, then validate `input`. Parser-only fixtures bypass history. No historical proof authorizes current output. A negative candidate is fully re-signed except when its particular check deliberately corrupts a signature or encoding.", "",
              "Wrapper documents use Git LF bytes for the stated file hash; Windows CRLF checkout translation is normalized only for wrapper drift checking. Embedded canonical objects are always compared as exact bytes.", "",
              "This bounded fixture oracle is implementation-side evidence, not a production verifier or independent review. Strict point admission is checked with fixture-only field arithmetic; signatures use platform crypto. Neither establishes deployed parser/crypto handling, disk durability, operational independence, genuine human consent or target qualification. Preflight execution/runtime gates remain blocked. Collector NEVER EXECUTED."]
    return raw, "\n".join(lines) + "\n", summary


def self_check():
    return generated()[2]


def check_artifacts():
    raw, md, _ = generated()
    # Git's Windows checkout can translate wrapper newlines; embedded hex is exact.
    need(DATA.read_text(encoding="utf-8") == raw and OUT.read_text(encoding="utf-8") == md, "GENERATED_ARTIFACT_DRIFT")


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument("--check", action="store_true")
    args = parser.parse_args(); raw, md, summary = generated()
    if args.check:
        check_artifacts()
    else:
        DATA.write_bytes(raw.encode()); OUT.write_bytes(md.encode())
    print(json.dumps({"self_check": "PASS", **summary}, sort_keys=True))


if __name__ == "__main__":
    main()
