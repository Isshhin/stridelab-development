"""Private, development-only TCX trace probe. Never authorizes access or emits a B0 row."""
from dataclasses import dataclass
from datetime import datetime, timezone
from hashlib import sha256
from math import isfinite
import re
from xml.etree import ElementTree as ET

VERSION = "p2-trace/0.1"
TCX = "{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}"
GPX = "{http://www.topografix.com/GPX/1/1}"


class Quarantine(ValueError):
    """Retain the incoming opportunity and reason in the restricted audit ledger."""


def need(test, reason):
    if not test:
        raise Quarantine(reason)


def clock(s):
    need(isinstance(s, str) and re.fullmatch(
        r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?(?:Z|[+-]\d\d:\d\d)", s),
        "missing_or_ambiguous_clock")
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00")).astimezone(timezone.utc)
    except (ValueError, OverflowError) as err:
        raise Quarantine("invalid_clock") from err


def single(node, tag, optional=False):
    found = node.findall(TCX + tag)
    if optional and not found:
        return None
    need(len(found) == 1, "missing_or_ambiguous_" + tag)
    return found[0]


def num(s):
    try:
        n = float(s)
    except (ValueError, TypeError) as err:
        raise Quarantine("bad_number") from err
    need(isfinite(n), "bad_number")
    return n


@dataclass(frozen=True)
class Point:
    at: datetime
    hr: float | None
    meters: float


@dataclass(frozen=True)
class Trace:
    source_digest: str
    start: datetime
    points: tuple[Point, ...]


def decode(data, fmt):
    """One original, one running activity, one lap and one track; no stitching."""
    need(isinstance(data, bytes) and 0 < len(data) <= 16_000_000, "size_or_type")
    if fmt == "fit":
        raise Quarantine("unsupported_fit_trace")
    need(fmt in {"tcx", "gpx"}, "unsupported_format")
    try:
        xml = data.decode("utf-8-sig")
        need("<!" not in xml and "\x00" not in xml, "unsupported_xml")
        root = ET.fromstring(xml)
    except (UnicodeError, ET.ParseError) as err:
        raise Quarantine("corrupt_xml") from err
    if fmt == "gpx":
        need(root.tag == GPX + "gpx", "unsupported_gpx_dialect")
        need(len(list(root.iter(GPX + "trkseg"))) == 1, "ambiguous_gpx_segments")
        raise Quarantine("unsupported_gpx_distance_and_timer")
    need(root.tag == TCX + "TrainingCenterDatabase", "unsupported_tcx_dialect")
    need(not any(e.tag.rsplit("}", 1)[-1] == "Extensions" for e in root.iter()),
         "unreviewed_tcx_extension")
    activity = single(single(root, "Activities"), "Activity")
    need(activity.get("Sport") == "Running", "wrong_sport")
    start = clock(single(activity, "Id").text)
    lap = single(activity, "Lap")
    need(clock(lap.get("StartTime")) == start, "ambiguous_start")
    track = single(lap, "Track")
    points = []
    for p in track:
        need(p.tag == TCX + "Trackpoint", "unsupported_track_element")
        # Unknown extensions may encode status, sensor changes or alternate observations.
        need(all(c.tag in {TCX + t for t in ("Time", "HeartRateBpm", "DistanceMeters",
                 "Position", "AltitudeMeters", "Cadence")} for c in p), "unknown_sample_channel")
        at = clock(single(p, "Time").text)
        need(at >= start and (not points or at > points[-1].at), "timestamp_order")
        dist = num(single(p, "DistanceMeters").text)
        need(dist >= 0 and (not points or dist >= points[-1].meters), "distance_reset")
        hrnode = single(p, "HeartRateBpm", optional=True)
        hr = num(single(hrnode, "Value").text) if hrnode is not None else None
        need(hr is None or (hr.is_integer() and 0 <= hr <= 255), "invalid_hr_encoding")
        points.append(Point(at, hr if hr else None, dist))
    need(len(points) >= 2, "incomplete_track")
    return Trace(sha256(data).hexdigest(), start, tuple(points))


@dataclass(frozen=True)
class Reference:
    """Trusted, independently frozen private review inputs; never infer these from TCX."""
    source_digest: str
    device_epoch: str
    epochs_seen: tuple[str, ...]
    method: str
    calibration: str
    sensor: str
    marker_crossings: tuple[datetime, ...]
    timer_events: tuple[tuple[datetime, str], ...]
    segment_timer_seconds: float
    reference_id: str
    reference_complete: bool  # complete event history, marker and sensor inventory
    artifact_free: bool  # blinded review; wrong laps, sensor flags/jumps, stops/walks
    configured_interval_s: float
    route_card_version: str
    firmware: str


def acquisition_probe(trace, ref):
    """Return only private measurement QC, never a linked or eligible repeat."""
    need(trace.source_digest == ref.source_digest and ref.reference_id, "reference_source")
    need(ref.reference_complete is True and ref.artifact_free is True, "reference_incomplete")
    need(ref.route_card_version and ref.firmware and
         0 < num(ref.configured_interval_s) <= 5, "missing_configuration")
    need(bool(ref.device_epoch) and ref.epochs_seen == (ref.device_epoch,), "sensor_epoch")
    need(ref.sensor == "electrical_chest_strap" and ref.method == "calibrated_footpod"
         and bool(ref.calibration), "unsupported_instrument")
    def offset(t):
        need(isinstance(t, datetime) and t.utcoffset() is not None, "reference_clock")
        return (t - trace.start).total_seconds()
    markers = [offset(t) for t in ref.marker_crossings]
    need(markers == sorted(set(markers)), "marker_order")
    starts = [t for t in markers if 600 <= t <= 900]
    need(starts, "no_registered_marker")
    first = starts[0]
    last = first + 1200
    events = [(offset(t), s) for t, s in ref.timer_events]
    need(events and events[0] == (0, "start"), "missing_timer_start")
    running = False
    prior = -1
    for t, action in events:
        need(t > prior and action == ("stop" if running else "start"), "timer_history")
        running = not running
        prior = t
    state = [s for t, s in events if t <= first]
    need(state and state[-1] == "start" and not any(first < t < last for t, _ in events),
         "paused_segment")
    need(abs(num(ref.segment_timer_seconds) - 1200) <= 2, "timer_discrepancy")
    rel = {offset(p.at): p for p in trace.points}
    boundaries = [first + i * 300 for i in range(5)]
    need(all(t in rel for t in boundaries), "unobserved_exact_boundary")
    need(all(rel[a].meters < rel[b].meters for a,b in zip(boundaries, boundaries[1:])),
         "no_quarter_distance")
    ordered = [(t, p) for t, p in rel.items() if first <= t <= last]
    valid = [0.] * 4
    total = [0.] * 4
    previous_valid = first
    max_interval = 0.
    for (t, p), (end, _) in zip(ordered, ordered[1:]):
        dt = end - t
        need(0 < dt <= 5, "sample_gap")
        max_interval = max(max_interval, dt)
        q = int((t - first) // 300)
        if p.hr is None:
            need(end - previous_valid <= 5, "hr_gap")
        else:
            previous_valid = t
            valid[q] += dt
            total[q] += p.hr * dt
    need(all(w >= 285 for w in valid), "quarter_coverage")
    return {"status": "PRIVATE_TRACE_QC_ONLY", "parser": VERSION, "start_offset_s": first,
            "duration_s": 1200, "distance_m": rel[last].meters - rel[first].meters,
            "quarter_valid_s": tuple(valid), "quarter_mean_hr_bpm": tuple(s/w for s,w in zip(total,valid)),
            "quarter_distance_m": tuple(rel[b].meters-rel[a].meters for a,b in zip(boundaries,boundaries[1:])),
            "max_observed_interval_s": max_interval}
