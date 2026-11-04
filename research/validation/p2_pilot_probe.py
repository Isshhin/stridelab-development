"""Private development-only TCX probe. All upstream admissions are steward decisions."""
from hashlib import sha256

import p2_trace
from p2f_linkage import Quarantine, instant


def inspect_linked_tcx(ledger, scope, cutoff, edge_id, source_event_id, raw, reference):
    """Recheck current access on every use. Never cache a projection or emit a B0 row."""
    state = ledger.replay(scope, cutoff)
    edge = state["links"].get(edge_id)
    sources = [source for source in state["sources"].values() if source["id"] == source_event_id]
    if edge is None or len(sources) != 1:
        raise Quarantine("source or verified edge unavailable at cutoff")
    source = sources[0]
    if source["activity"] != edge["activity"] or source["format"].lower() != "tcx":
        raise Quarantine("source does not identify this linked TCX activity")
    if not isinstance(raw, bytes) or not 0 < len(raw) <= 16_000_000:
        raise Quarantine("invalid original bytes")
    if source["digest"] != sha256(raw).hexdigest() or reference.source_digest != source["digest"]:
        raise Quarantine("source version or independent reference mismatch")
    trace = p2_trace.decode(raw, "tcx")
    activity = state["activities"][edge["activity"]]
    if trace.start != instant(activity["performed_at"]) or trace.points[-1].at > instant(activity["ended_at"]):
        raise Quarantine("trace outside independently adjudicated activity interval")
    # Recompute in restricted staging. The caller must discard the returned
    # values on closure/withdrawal under the admission and deletion procedure.
    return p2_trace.acquisition_probe(trace, reference)
