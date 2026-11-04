"""Private, development-only TCX trace probe. Never authorizes access or emits a B0 row."""
from dataclasses import dataclass
from datetime import datetime, timezone
from hashlib import sha256
from io import StringIO
from math import isfinite
import re
from xml.etree import ElementTree as ET

VERSION = "p2-trace/0.2"
TCX = "{http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2}"
GPX = "{http://www.topografix.com/GPX/1/1}"
XSI = "{http://www.w3.org/2001/XMLSchema-instance}"


class Quarantine(ValueError):
    """Retain the incoming opportunity and reason in the restricted audit ledger."""


def need(test, reason):
    if not test:
        raise Quarantine(reason)


def clock(s):
    need(isinstance(s, str) and re.fullmatch(
        r"\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,6})?(?:Z|[+-]\d\d:\d\d)", s),
        "missing_or_ambiguous_clock")
    if not s.endswith("Z"):
        hh, mm = int(s[-5:-3]), int(s[-2:])
        need(mm < 60 and (hh < 14 or (hh == 14 and mm == 0))
             and s[-6:] != "-00:00", "invalid_or_unknown_offset")
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
    need(not isinstance(s, bool), "bad_number")
    if isinstance(s, str):
        need(re.fullmatch(r"[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?",
                          s.strip()), "bad_number")
    try:
        n = float(s)
    except (ValueError, TypeError) as err:
        raise Quarantine("bad_number") from err
    need(isfinite(n), "bad_number")
    return n


def scalar(node):
    need(len(node) == 0 and not node.attrib and node.text is not None, "ambiguous_scalar")
    return node.text.strip()


def children(node, allowed):
    need(all(c.tag in {TCX + name for name in allowed} for c in node), "unknown_or_misplaced_element")


def channel(node, tag, *, byte=False, bpm=False):
    field = single(node, tag, optional=True)
    if field is None:
        return None
    if bpm:
        xsi_type = XSI + "type"
        need(all(k == xsi_type and v == "HeartRateInBeatsPerMinute_t"
                 for k, v in field.attrib.items()), "unknown_hr_type")
        children(field, ("Value",))
        field = single(field, "Value")
    n = num(scalar(field))
    need(n >= 0 and (not (byte or bpm) or (n.is_integer() and n <= 255)), "invalid_" + tag)
    return int(n) if byte or bpm else n


@dataclass(frozen=True)
class Point:
    at: datetime
    hr: float | None
    meters: float
    source_hr: int | None = None  # preserve reported zero separately from missing
    cadence: int | None = None  # native channel; never auto-convert to steps/min


@dataclass(frozen=True)
class Trace:
    source_digest: str
    start: datetime
    points: tuple[Point, ...]
    lap_reported_time_s: float | None
    lap_reported_m: float | None
    lap_mean_hr: int | None
    lap_max_hr: int | None
    lap_cadence: int | None


def decode(data, fmt):
    """One original, one running activity, one lap and one track; no stitching."""
    need(isinstance(data, bytes) and 0 < len(data) <= 16_000_000, "size_or_type")
    if fmt == "fit":
        raise Quarantine("unsupported_fit_trace")
    need(fmt in {"tcx", "gpx"}, "unsupported_format")
    try:
        xml = data.decode("utf-8-sig")
        need("<!" not in xml and "\x00" not in xml, "unsupported_xml")
        # ElementTree expands element names, but not unprefixed QName *values*
        # of xsi:type. Resolve the default namespace in scope for those values.
        parser = ET.iterparse(StringIO(xml), events=("start-ns", "start", "end"))
        default_ns, pending_default = [""], None
        for event, item in parser:
            if event == "start-ns":
                if item[0] == "":
                    pending_default = item[1]
            elif event == "start":
                default_ns.append(default_ns[-1] if pending_default is None else pending_default)
                pending_default = None
                if item.tag in {TCX+"HeartRateBpm", TCX+"AverageHeartRateBpm", TCX+"MaximumHeartRateBpm"}:
                    if XSI+"type" in item.attrib:
                        need(default_ns[-1] == TCX[1:-1], "unknown_hr_type_namespace")
            else:
                default_ns.pop()
        root = parser.root
    except (UnicodeError, ET.ParseError) as err:
        raise Quarantine("corrupt_xml") from err
    if fmt == "gpx":
        need(root.tag == GPX + "gpx", "unsupported_gpx_dialect")
        need(len(list(root.iter(GPX + "trkseg"))) == 1, "ambiguous_gpx_segments")
        raise Quarantine("unsupported_gpx_distance_and_timer")
    need(root.tag == TCX + "TrainingCenterDatabase", "unsupported_tcx_dialect")
    need(set(root.attrib) <= {XSI+"schemaLocation"}, "unsupported_root_attributes")
    need(not any(e.tag.rsplit("}", 1)[-1] == "Extensions" for e in root.iter()),
         "unreviewed_tcx_extension")
    children(root, ("Activities", "Author"))
    activities = single(root, "Activities")
    need(not activities.attrib, "unsupported_activities_attributes")
    children(activities, ("Activity",))
    activity = single(activities, "Activity")
    children(activity, ("Id", "Lap", "Notes", "Creator"))
    need(activity.get("Sport") == "Running", "wrong_sport")
    need(set(activity.attrib) == {"Sport"}, "unsupported_attribute_identity")
    start = clock(scalar(single(activity, "Id")))
    lap = single(activity, "Lap")
    need(set(lap.attrib) == {"StartTime"}, "unsupported_lap_identity")
    children(lap, ("TotalTimeSeconds", "DistanceMeters", "MaximumSpeed", "Calories",
                   "AverageHeartRateBpm", "MaximumHeartRateBpm", "Intensity", "Cadence",
                   "TriggerMethod", "Track", "Notes"))
    for name in ("Activity", "Lap", "Track"):
        need(sum(1 for _ in root.iter(TCX + name)) == 1, "ambiguous_" + name)
    need(clock(lap.get("StartTime")) == start, "ambiguous_start")
    track = single(lap, "Track")
    need(not track.attrib, "unsupported_track_attributes")
    points = []
    for p in track:
        need(p.tag == TCX + "Trackpoint", "unsupported_track_element")
        need(not p.attrib, "unsupported_trackpoint_attributes")
        # Unknown extensions may encode status, sensor changes or alternate observations.
        children(p, ("Time", "HeartRateBpm", "DistanceMeters", "Position", "AltitudeMeters",
                     "Cadence", "SensorState"))
        sensor_state = single(p, "SensorState", optional=True)
        if sensor_state is not None:
            need(scalar(sensor_state) == "Present", "absent_or_unknown_sensor_state")
        at = clock(scalar(single(p, "Time")))
        need(at >= start and (not points or at > points[-1].at), "timestamp_order")
        dist = num(scalar(single(p, "DistanceMeters")))
        need(dist >= 0 and (not points or dist >= points[-1].meters), "distance_reset")
        hr = channel(p, "HeartRateBpm", bpm=True)
        points.append(Point(at, hr or None, dist, hr, channel(p, "Cadence", byte=True)))
    need(len(points) >= 2, "incomplete_track")
    return Trace(sha256(data).hexdigest(), start, tuple(points),
                 channel(lap, "TotalTimeSeconds"), channel(lap, "DistanceMeters"),
                 channel(lap, "AverageHeartRateBpm", bpm=True),
                 channel(lap, "MaximumHeartRateBpm", bpm=True), channel(lap, "Cadence", byte=True))


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
            "source_session_ordinal": 0, "source_lap_ordinal": 0,
            "first_to_last_sample_elapsed_s": (trace.points[-1].at-trace.points[0].at).total_seconds(),
            "lap_reported_time_s": trace.lap_reported_time_s,
            "lap_reported_m": trace.lap_reported_m,
            "lap_reported_mean_hr_bpm": trace.lap_mean_hr,
            "lap_reported_max_hr_bpm": trace.lap_max_hr, "lap_native_cadence": trace.lap_cadence,
            "duration_s": 1200, "distance_m": rel[last].meters - rel[first].meters,
            "quarter_valid_s": tuple(valid), "quarter_mean_hr_bpm": tuple(s/w for s,w in zip(total,valid)),
            "quarter_distance_m": tuple(rel[b].meters-rel[a].meters for a,b in zip(boundaries,boundaries[1:])),
            "max_observed_interval_s": max_interval}
