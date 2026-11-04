"""Private development-only reconstruction; steward-supplied evidence, never raw uploads.

PilotLedger is one account/athlete/grant/deciding view. The trusted adapter, not
an upload, supplies journal order, receipt, reviewer mapping and authorization.
No database, network, raw signal, or production dependencies.
"""
from copy import deepcopy
from datetime import datetime, timezone
import hashlib
import json
import re


SCHEMA = "F2.1-pilot.1"


class Quarantine(ValueError):
    """No projection or partial match may be exported for this replay."""


def require(ok, message):
    if not ok:
        raise Quarantine(message)


def ident(x):
    require(isinstance(x, str) and bool(x.strip()), "missing identity")
    return x


def instant(x):
    try:
        d = datetime.fromisoformat(x.replace("Z", "+00:00"))
        require(d.utcoffset() is not None, "timestamp needs offset")
        return d.astimezone(timezone.utc)
    except (TypeError, AttributeError, ValueError, OverflowError) as error:
        raise Quarantine("invalid timestamp") from error


def encode(x):
    return json.dumps(x, sort_keys=True, separators=(",", ":"), allow_nan=False)


def digest(x):
    return hashlib.sha256(encode(x).encode()).hexdigest()


class PilotLedger:
    def __init__(self, scope, grant_start, sources, links, corrections=(), followups=()):
        """Open only after private authorization. No grant discovery or signing.

        scope=(account, study pseudonym, grant, 'development-pilot', view).
        Trusted sources: (connection, namespace, external, format) -> bout.
        Trusted links: evidence ID -> (bout, opportunity, prestart revision).
        Both maps must come from independent review in this SAME scope.
        """
        require(isinstance(scope, tuple) and len(scope) == 5, "invalid scope")
        self.scope = tuple(ident(x) for x in scope)
        require(scope[3] == "development-pilot", "invalid partition")
        self.start = instant(grant_start)
        self._sources, self._links = deepcopy(sources), deepcopy(links)
        self._corrections = set(corrections)  # (source key, previous digest, new digest)
        self._followups = set(followups)  # (observation, opportunity, protocol, observed_at)
        self._rows = {}
        self._revoked = False

    def _access(self, scope):
        require(not self._revoked and self.scope == tuple(scope), "access denied")

    def withdraw(self, scope):
        self._access(scope)
        self._revoked = True
        self._rows.clear()
        self._sources.clear()
        self._links.clear()
        self._corrections.clear()
        self._followups.clear()

    def append(self, scope, rows):
        """Atomic discovery/ingest; byte-identical event retries are idempotent."""
        self._access(scope)
        candidate = self._rows.copy()
        for row in deepcopy(rows):
            try:
                self._access(row["scope"])
                eid = ident(row["id"])
                a, r, e = (instant(row[k]) for k in ("available", "recorded", "effective"))
                require(self.start <= r <= a and e <= a, "invalid chronology")
                seq = row.get("sequence")
                require(seq is None or type(seq) is int and seq >= 0, "invalid order")
                require(eid not in candidate or candidate[eid] == row, "event ID collision")
                candidate[eid] = row
            except (KeyError, TypeError) as error:
                raise Quarantine("malformed envelope") from error
        # Preserve ambiguity rather than silently reverting to an older projection.
        # Semantic quarantine happens at replay, scoped to the affected cutoff.
        self._rows = candidate

    def replay(self, scope, cutoff, watermark=None):
        """Return a fresh later or historical projection. Unordered cutoff ties unavailable."""
        self._access(scope)
        stop = instant(cutoff)
        require(stop >= self.start, "outside grant")
        require(watermark is None or type(watermark) is int and watermark >= 0,
                "invalid watermark")
        stream = []
        for row in self._rows.values():
            a, seq = instant(row["available"]), row.get("sequence")
            if a < stop or (a == stop and watermark is not None and seq is not None and seq <= watermark):
                stream.append((a, seq, row))
        stream.sort(key=lambda t: (t[0], -1 if t[1] is None else t[1], t[2]["id"]))
        previous = None
        state = {k: {} for k in ("plans", "opportunities", "revisions", "decisions",
                                  "choices", "activities", "sources", "links", "contexts", "followups")}
        seen, receipts, used_seq = {}, {}, set()
        last_seq = -1
        for when, seq, row in stream:
            # A tie with unknown order is indeterminate, not an arbitrary ID sort.
            if previous and when == previous[0]:
                require(seq is not None and previous[1] is not None, "unordered events")
            if seq is not None:
                require(seq not in used_seq and seq > last_seq, "invalid view sequence")
                used_seq.add(seq)
                last_seq = seq
            try:
                self._apply(state, seen, receipts, row)
            except (KeyError, TypeError, IndexError) as error:
                raise Quarantine("unresolved reference or missing field") from error
            seen[row["id"]] = row
            previous = when, seq
        state["event_ids"] = list(seen)
        state["schema"] = SCHEMA
        state["snapshot"] = digest([SCHEMA, scope, cutoff, watermark, list(seen.values())])
        return state

    def _apply(self, s, seen, receipts, r):
        eid, kind, p = r["id"], r["kind"], r["data"]
        at, eff = instant(r["available"]), instant(r["effective"])
        opps, revs, plans = s["opportunities"], s["revisions"], s["plans"]

        def fresh(table, key):
            ident(key)
            require(key not in table, "identity reused")

        def active(o, rev):
            require(opps[o]["active"] == rev, "stale revision")

        if kind in {"initial", "manual_edit", "apply_choice"}:
            o, plan, version, rev = (ident(p[k]) for k in ("opportunity","plan","version","revision"))
            fresh(revs, rev)
            require(type(p["locked"]) is bool, "missing lock state")
            require(version not in {v["version"] for v in revs.values()}, "version reused")
            if kind == "initial":
                fresh(opps, o)
                fresh(plans, plan)  # bounded one-opportunity plan
                require(p["parent"] is None and p["parent_version"] is None, "invalid root")
                opps[o] = {"plan":plan, "initial":rev, "links":[], "performance":"unknown"}
            else:
                active(o, p["parent"])
                require(opps[o]["plan"] == plan and plans[plan] == p["parent_version"],
                        "invalid version ancestry")
                require(not revs[p["parent"]]["locked"], "session locked")
                require(eff >= instant(revs[p["parent"]]["effective"]), "invalid revision chronology")
                if kind == "apply_choice":
                    d = s["decisions"][p["decision"]]
                    require(d["revision"] == p["parent"] and d["opportunity"] == o and
                            d["offered"] and p["intent"] == d["offered_intent"] and
                            p["decision"] not in s["choices"], "ineligible choice")
                    s["choices"][p["decision"]] = {"id":eid,"state":"accepted","revision":rev}
            ident(p["intent"])
            revs[rev] = {"plan":plan,"version":version,"parent":p["parent"],
                         "parent_version":p["parent_version"],"effective":r["effective"],
                         "available":r["available"],
                         "intent":p["intent"],"locked":p["locked"],"event":eid}
            plans[plan], opps[o]["active"] = version, rev
        elif kind == "receipt":
            o, rev = p["opportunity"], p["revision"]
            active(o, rev)
            require(eff == at, "unverifiable prestart receipt")
            receipts.setdefault(o, []).append((at, rev))
        elif kind == "decision":
            o, rev = p["opportunity"], p["revision"]
            active(o, rev)
            require(eff == at and instant(r["recorded"]) == at and r.get("sequence") is not None,
                    "decision needs contemporaneous view watermark")
            inputs = p["inputs"]
            require(isinstance(inputs, list) and len(inputs) == len(set(inputs))
                    and revs[rev]["event"] in inputs and all(k in seen for k in inputs),
                    "invalid decision inputs")
            require(type(p["offered"]) is bool, "unknown display state")
            require(not p["offered"] or ident(p.get("offered_intent")), "missing option")
            s["decisions"][eid] = {"opportunity":o,"revision":rev,"offered":p["offered"],
                    "offered_intent":p.get("offered_intent"),
                    "inputs":list(inputs),"watermark":r["sequence"],
                    "input_digest":digest([seen[k] for k in inputs])}
        elif kind == "choice":
            d = p["decision"]
            require(s["decisions"][d]["offered"] and d not in s["choices"] and
                    p["state"] in {"rejected","dismissed"}, "invalid choice")
            s["choices"][d] = {"id":eid,"state":p["state"]}
        elif kind in {"source", "manual_occurrence"}:
            bout = ident(p["activity"])
            start, end = instant(p["performed_at"]), instant(p["ended_at"])
            require(self.start <= start <= end <= instant(r["recorded"]) and end <= eff, "invalid performed interval")
            activity = s["activities"].setdefault(bout, {"performed_at":p["performed_at"],
                    "ended_at":p["ended_at"],"basis":[],"sources":[],"links":[],"dose":None,
                    "dose_status":"not_collected"})
            require(start == instant(activity["performed_at"]) and end == instant(activity["ended_at"]),
                    "conflicting bout bounds")
            if kind == "source":
                key = tuple(ident(p[k]) for k in ("connection","namespace","external","format"))
                require(self._sources.get(key) == bout, "unresolved source identity")
                require(end <= instant(p["imported_at"]) <= at, "invalid import chronology")
                require(isinstance(p["digest"], str) and re.fullmatch("[0-9a-f]{64}",p["digest"]),
                        "invalid payload digest")
                require(not any(x["kind"] == "source" and x["data"]["digest"] == p["digest"]
                                and x["data"]["activity"] != bout for x in seen.values()),
                        "conflicting canonical identity for identical bytes")
                skey = encode(key)
                old = s["sources"].get(skey)
                if old and old["digest"] == p["digest"]:
                    require(p.get("supersedes") is None, "invalid correction")
                    return
                if old:
                    require(p.get("supersedes") == old["id"] and p.get("previous_digest") == old["digest"]
                            and (key, old["digest"], p["digest"]) in self._corrections
                            and ident(p.get("reason")), "unattested changed bytes")
                    for edge_id in list(activity["links"]):
                        edge = s["links"].pop(edge_id)
                        opp = opps[edge["opportunity"]]
                        opp["links"].remove(edge_id)
                        opp["performance"] = "unknown"
                        activity["links"].remove(edge_id)
                else:
                    require(p.get("supersedes") is None, "orphan correction")
                    activity["sources"].append(skey)
                s["sources"][skey] = {"id":eid,"digest":p["digest"],"activity":bout,
                                      "imported_at":p["imported_at"],"format":key[-1]}
            if kind not in activity["basis"]:
                activity["basis"].append(kind)
        elif kind == "link":
            b, o, rev = p["activity"], p["opportunity"], p["revision"]
            require(self._links.get(ident(p["evidence"])) == (b,o,rev), "unverified edge")
            require(not any(x["kind"] == "link" and x["data"]["evidence"] == p["evidence"]
                            for x in seen.values()), "edge evidence reused")
            act = s["activities"][b]
            start = instant(act["performed_at"])
            eligible = [x for x in receipts.get(o, []) if x[0] < start]
            known_at_start = [key for key, item in revs.items()
                              if item["plan"] == opps[o]["plan"] and instant(item["available"]) < start]
            require(known_at_start and known_at_start[-1] == rev and not any(
                item["plan"] == opps[o]["plan"] and instant(item["available"]) == start
                for item in revs.values()), "prestart revision indeterminate")
            require(eligible and eligible[-1][1] == rev and
                    not any(t == start for t, _ in receipts.get(o, [])), "prestart revision unresolved")
            # This adapter cannot safely allocate a split or a shared bout.
            require(not act["links"] and not opps[o]["links"], "ambiguous duplicate or split link")
            s["links"][eid] = {"activity":b,"opportunity":o,"revision":rev,"evidence":p["evidence"]}
            act["links"].append(eid)
            opps[o]["links"].append(eid)
            opps[o]["performance"] = "confirmed_occurrence"
        elif kind == "revoke_link":
            edge = s["links"].pop(p["link"])
            ident(p["reason"])
            s["activities"][edge["activity"]]["links"].remove(p["link"])
            opp = opps[edge["opportunity"]]
            opp["links"].remove(p["link"])
            opp["performance"] = "unknown"
        elif kind == "context":
            require(p["target"] in seen and p["status"] in {"reported","unknown","withheld","not_collected"},
                    "invalid context")
            require((p["status"] == "reported" and isinstance(p["reason"], str) and bool(p["reason"].strip()))
                    or (p["status"] != "reported" and p["reason"] is None), "invalid reason")
            s["contexts"][eid] = {"target":p["target"],"status":p["status"],"reason":p["reason"]}
        elif kind == "followup_window":
            o = p["opportunity"]
            require(o in opps and o not in s["followups"] and instant(p["opens"]) <= instant(p["closes"]),
                    "invalid followup window")
            s["followups"][o] = {"opens":p["opens"],"closes":p["closes"],"observation":None,
                                 "protocol":ident(p["protocol"])}
        elif kind == "followup_observation":
            o, obs = p["opportunity"], ident(p["observation"])
            window = s["followups"][o]
            observed = instant(p["observed_at"])
            require((obs,o,window["protocol"],p["observed_at"]) in self._followups and
                    instant(window["opens"]) <= observed <= instant(window["closes"]) and
                    observed <= instant(r["recorded"]) and observed <= eff and
                    "censor" not in window and not any(w["observation"] == obs
                    for w in s["followups"].values()) and window["observation"] is None,
                    "invalid independent followup evidence")
            window.update(observation=obs, observed_at=p["observed_at"], observation_event=eid)
        elif kind == "followup_censor":
            window = s["followups"][p["opportunity"]]
            require("censor" not in window and window["observation"] is None
                    and ident(p["reason"]), "invalid censor")
            window["censor"] = {"event":eid, "reason":p["reason"]}
        else:
            raise Quarantine("unsupported event")

    def followup_state(self, scope, opportunity, cutoff):
        window = self.replay(scope, cutoff)["followups"].get(opportunity)
        if not window:
            return "unknown"
        if "censor" in window:
            return "censored"
        if window["observation"] is not None:
            return "observed"
        at = instant(cutoff)
        if at < instant(window["opens"]):
            return "not_due"
        return "awaiting" if at <= instant(window["closes"]) else "overdue_missing"
