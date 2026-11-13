"""P2-F3 development audit. The ledger and source registry are reviewer inputs.
Use only synthetic fixtures until separate source and pilot admission. No data I/O
beyond the source file passed to the parser. Never emit a source path or GPS.
"""
from dataclasses import dataclass
from datetime import datetime
from hashlib import sha256
from math import isfinite
from pathlib import Path
import warnings
import xml.etree.ElementTree as ET


class Quarantine(ValueError):
    """Caller records reason and withholds the entire observation."""


def aware(value):
    return isinstance(value, datetime) and value.utcoffset() is not None


@dataclass(frozen=True)
class Source:
    account: str
    digest: str
    session_index: int
    file_format: str
    acquired_at: datetime


@dataclass(frozen=True)
class ReviewedEdge:
    source: Source
    activity: str
    opportunity: str
    revision: str
    reviewed_at: datetime
    completion: str


@dataclass(frozen=True)
class Prescription:
    account: str
    opportunity: str
    revision: str
    accepted_at: datetime
    effective_at: datetime


@dataclass(frozen=True)
class Parsed:
    source: Source
    observation: object


def check_fit_laps(data):
    """Reject incomplete, orphaned or multiply assigned lap indices in this scope."""
    import fitdecode
    try:
        with fitdecode.FitReader(data, check_crc=fitdecode.CrcCheck.RAISE) as reader:
            indices, expected = [], []
            for frame in reader:
                if not isinstance(frame, fitdecode.FitDataMessage):
                    continue
                fields = {f.name: f.value for f in frame.fields}
                if frame.name == "lap":
                    indices.append(fields.get("message_index"))
                elif frame.name == "session":
                    first, count = fields.get("first_lap_index"), fields.get("num_laps")
                    if not isinstance(first, int) or not isinstance(count, int) or count < 1:
                        raise Quarantine("missing session lap boundaries")
                    expected.extend(range(first, first + count))
            if (not indices or None in indices or len(expected) != len(set(expected))
                    or sorted(indices) != sorted(expected)):
                raise Quarantine("incomplete or ambiguous FIT lap index")
    except fitdecode.FitError as exc:
        raise Quarantine("invalid FIT structure") from exc


def parse_reviewed(path: Path, source: Source, parser):
    """Invoke a caller-pinned parser on an immutable fixture in private staging."""
    if not source.account or not aware(source.acquired_at) or source.session_index < 0:
        raise Quarantine("missing source identity")
    if path.stat().st_size > 50 * 1024 * 1024:
        raise Quarantine("source exceeds parser bound")
    data = path.read_bytes()
    if sha256(data).hexdigest() != source.digest or path.suffix.lower() != "." + source.file_format:
        raise Quarantine("source digest or format mismatch")
    if source.file_format not in {"fit", "gpx", "tcx"}:
        raise Quarantine("unsupported format")
    if source.file_format in {"gpx", "tcx"}:
        try:
            root = ET.fromstring(data)
        except ET.ParseError as exc:
            raise Quarantine("malformed XML") from exc
        if source.file_format == "gpx":
            segments = root.findall(".//{*}trkseg")
            points = root.findall(".//{*}trkpt")
            if len(segments) != 1 or len(points) < 2 or any(p.find("{*}time") is None for p in points):
                raise Quarantine("unsupported or incomplete GPX segment")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error")
            if source.file_format == "fit":
                check_fit_laps(data)
            observations = parser(path)
        obs = observations[source.session_index]
    except (ValueError, IndexError, TypeError, Warning) as exc:
        raise Quarantine("decoder rejected source or ordinal") from exc
    if obs.provenance.get("source_sha256") != source.digest or obs.source_kind != source.file_format + "_file":
        raise Quarantine("decoder provenance mismatch")
    if not aware(obs.activity_start_time) or source.acquired_at < obs.activity_start_time:
        raise Quarantine("invalid source clock")
    if source.file_format == "tcx" and (not obs.laps or any(
        lap.get("distance_m") is None or lap.get("duration_s") is None for lap in obs.laps
    )):
        raise Quarantine("incomplete TCX lap totals")
    elapsed, timer = obs.actual_duration_seconds, obs.actual_timer_duration_seconds
    if timer is not None and elapsed is not None and timer > elapsed:
        raise Quarantine("timer exceeds elapsed")
    hr = obs.average_heart_rate_bpm
    if hr is not None and (not isfinite(hr) or hr < 0 or hr > 255):
        raise Quarantine("invalid HR channel")
    return Parsed(source, obs)


def reconcile(account, source, parsed, as_of, edges, prescriptions):
    """Return only identity accounting. Reviewed identities must be account-local.
    Duplicate formats may share an activity only via independent reviewed edges.
    """
    if account != source.account or not account:
        raise Quarantine("account mismatch")
    if parsed.source != source:
        raise Quarantine("source ordinal or registry mismatch")
    obs = parsed.observation
    if (obs.provenance.get("source_sha256") != source.digest
            or obs.source_kind != source.file_format + "_file"):
        raise Quarantine("observation does not match source")
    if not aware(as_of) or not aware(source.acquired_at) or not aware(obs.activity_start_time):
        raise Quarantine("unresolved clock")
    if source.acquired_at > as_of:
        return ("NOT_YET_AVAILABLE",)
    matches = set()
    for edge in edges:
        if edge.source != source:
            continue
        if not aware(edge.reviewed_at) or edge.reviewed_at < source.acquired_at:
            raise Quarantine("invalid review clock")
        if edge.reviewed_at <= as_of:
            matches.add(edge)
    if not matches:
        return ("UNLINKED",)
    if len(matches) != 1:
        raise Quarantine("ambiguous reviewed source")
    edge = matches.pop()
    if not all((edge.activity, edge.opportunity, edge.revision)) or edge.completion not in {"partial", "completed", "substituted", "unknown"}:
        raise Quarantine("incomplete reviewed edge")
    revisions = set()
    for row in prescriptions:
        if (row.account, row.opportunity) != (account, edge.opportunity):
            continue
        if not aware(row.effective_at) or not aware(row.accepted_at):
            raise Quarantine("unresolved revision clock")
        if max(row.accepted_at, row.effective_at) <= obs.activity_start_time:
            revisions.add(row)
    if not revisions:
        raise Quarantine("no effective prescription")
    latest = max(row.effective_at for row in revisions)
    active = [row for row in revisions if row.effective_at == latest]
    if len(active) != 1 or active[0].revision != edge.revision:
        raise Quarantine("stale or ambiguous accepted revision")
    return (account, edge.activity, edge.opportunity, edge.revision, edge.completion)

def coverage(account, opportunities, linked_rows, manual_claims=()):
    """Count unique reviewed actuals and retain all opportunities in the denominator.
    Manual claims supply self-report only and never add a measured dose.
    """
    claims = set(manual_claims)
    targets = set(opportunities)
    if not claims <= targets:
        raise Quarantine("unknown manual claim target")
    by_activity = {}
    for row in linked_rows:
        if row in {("NOT_YET_AVAILABLE",), ("UNLINKED",)}:
            continue
        owner, activity, target, revision, completion = row
        if owner != account or target not in targets:
            raise Quarantine("activity outside account or plan")
        identity = (target, revision, completion)
        if activity in by_activity and by_activity[activity] != identity:
            raise Quarantine("different purposes or conflicting completion require review")
        by_activity[activity] = identity
    observed = {target for target, _, _ in by_activity.values()}
    return {
        "opportunities": len(targets), "reviewed_actuals": len(by_activity),
        "observed": sorted(observed), "manual_only": sorted(claims - observed),
        "no_observed_evidence": sorted(targets - observed - claims),
    }
