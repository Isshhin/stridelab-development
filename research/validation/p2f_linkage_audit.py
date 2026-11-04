"""Private single-source audit pinning. Does not acquire records or certify tool independence."""
from copy import deepcopy
from hashlib import sha256

from p2f_linkage import SCHEMA, Quarantine, digest, ident, require


class AuditSeries:
    """One audit series per bounded grant. Only a trusted steward may select pins.

    runners maps immutable parser/implementation pins to offline callables accepting
    original bytes and frozen worksheet bytes. A runner returns JSON-compatible
    QC evidence, or raises Quarantine for a material mismatch/unsupported input.
    Actual runnable code, worksheet and original are private, external artifacts.
    """
    def __init__(self, ledger, scope, runners):
        ledger._access(scope)
        self.ledger, self.scope, self.runners = ledger, scope, dict(runners)

    def _series(self):
        self.ledger._access(self.scope)
        return self.ledger._authority._audits.setdefault(self.scope, [])

    def pin(self, source_event, edge_id, parser_pin, worksheet_bytes):
        series = self._series()
        require(ident(parser_pin) in self.runners and isinstance(worksheet_bytes, bytes)
                and worksheet_bytes, "unknown parser or missing frozen reference")
        now = self.ledger._access(self.scope).isoformat()
        state = self.ledger.replay(self.scope, now)
        src = next((v for v in state["sources"].values() if v["id"] == source_event), None)
        edge = state["links"].get(edge_id)
        require(src and edge and src["activity"] == edge["activity"], "source/edge unavailable")
        spec = dict(source=source_event, edge=edge_id, checksum=src["digest"], parser=parser_pin,
                    worksheet=sha256(worksheet_bytes).hexdigest(), head=self.ledger._head,
                    cutoff=now, snapshot=state["snapshot"], event_ids=state["event_ids"])
        if series and all(series[-1]["spec"][k] == spec[k] for k in spec if k not in {"cutoff","snapshot"}):
            return len(series)
        series.append({"spec":spec, "result":None, "disputed":False})
        return len(series)

    def _verify_version(self, entry, raw, worksheet):
        spec = entry["spec"]
        require(isinstance(raw, bytes) and isinstance(worksheet, bytes) and
                sha256(raw).hexdigest() == spec["checksum"] and
                sha256(worksheet).hexdigest() == spec["worksheet"] and
                spec["parser"] in self.runners, "pinned inputs unavailable")
        # Verify the exact journal batch originally observed. Events discovered
        # later cannot rewrite that historical bundle, even if time-stamped earlier.
        rows = [self.ledger._rows.get(i) for i in spec["event_ids"]]
        require(all(r is not None for r in rows) and digest([SCHEMA,self.scope,spec["cutoff"],
                None,rows]) == spec["snapshot"], "historical evidence changed")
        try:
            result = self.runners[spec["parser"]](raw, worksheet)
            require(isinstance(result,dict) and result.get("status") == "PRIVATE_TRACE_QC_ONLY",
                    "runner returned no eligible comparison")
            record = {"disposition":"COMPARED", "output_digest":digest(result)}
        except Quarantine as error:
            record = {"disposition":"QUARANTINED", "output_digest":digest(str(error))}
        self.ledger._access(self.scope)
        return record

    def compare(self, version, raw, worksheet):
        series = self._series()
        require(type(version) is int and 1 <= version <= len(series), "unknown audit version")
        entry = series[version-1]
        if entry["result"] is None:
            require(version == len(series) and entry["spec"]["head"] == self.ledger._head,
                    "superseded audit cannot be completed")
        record = self._verify_version(entry, raw, worksheet)
        if entry["result"] not in (None,record):
            entry["disputed"] = True  # preserve original; no longer eligible
            raise Quarantine("reproduction mismatch")
        if entry["result"] is None:
            require(version == len(self._series()) and entry["spec"]["head"] == self.ledger._head,
                    "audit changed during comparison")
            entry["result"] = record
        return dict(record, version=version, active=(version == len(series) and
                    not entry["disputed"] and entry["spec"]["head"] == self.ledger._head
                    and record["disposition"] == "COMPARED"))

    def current(self):
        series = self._series()
        require(series and not series[-1]["disputed"] and series[-1]["spec"]["head"] == self.ledger._head and
                series[-1]["result"] and series[-1]["result"]["disposition"] == "COMPARED",
                "current independent comparison required")
        return dict(deepcopy(series[-1]),version=len(series))
