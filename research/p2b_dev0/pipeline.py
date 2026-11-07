"""Small deterministic P2-B DEV-0 reference pipeline; research-only."""
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta, timezone
from enum import Enum
from hashlib import sha256
from math import hypot, isfinite, sqrt
import json
import xml.etree.ElementTree as ET

FOUR_TU_DOI = "10.4121/efa64223-ba51-48fa-91be-53f0e48460b4.v1"
FOUR_TU_ADMISSION = "RESEARCH_ONLY_ISOLATED"
WINDOWS = (1200, 900)
ROUTE_TOLERANCES = {"STRICT": (0.0008, 0.0008, 0.03), "MODERATE": (0.002, 0.002, 0.08), "LOOSE": (0.005, 0.005, 0.15)}

class AthleteState(Enum):
    ESTIMABLE = "ESTIMABLE"
    INSUFFICIENT_REPEATS = "INSUFFICIENT_REPEATS"
    INSUFFICIENT_ROUTE_OVERLAP = "INSUFFICIENT_ROUTE_OVERLAP"
    INSUFFICIENT_HR_MATCHES = "INSUFFICIENT_HR_MATCHES"
    INSUFFICIENT_TRACE_QUALITY = "INSUFFICIENT_TRACE_QUALITY"
    UNSUPPORTED_MEASUREMENT = "UNSUPPORTED_MEASUREMENT"

@dataclass(frozen=True)
class Activity:
    athlete_id: str
    activity_id: str
    date: str
    samples: tuple
    source_dataset: str = "synthetic-dev0"
    route_direction: str = "OUT"
    temperature_c: float | None = None
    relative_humidity_pct: float | None = None

def _distance(a, b): return hypot(b["lat"] - a["lat"], b["lon"] - a["lon"])

def _window(a, duration):
    start = 720
    return tuple(dict(s, t=s["t"] - start) for s in a.samples if start <= s["t"] <= start + duration)

def _qc(samples, duration):
    if not samples or samples[0]["t"] > 0 or samples[-1]["t"] < duration:
        return False, ("WINDOW_COVERAGE",)
    if any(b["t"] <= a["t"] for a, b in zip(samples, samples[1:])):
        return False, ("NON_MONOTONIC_TIMESTAMP",)
    gaps = [b["t"] - a["t"] for a, b in zip(samples, samples[1:])]
    if max(gaps, default=0) > 5: return False, ("UNRECOVERABLE_GAP",)
    hr = [s["hr"] for s in samples if s.get("hr") is not None]
    speed = [s["speed"] for s in samples if s.get("speed") is not None]
    if len(hr) / len(samples) < .95: return False, ("HR_COVERAGE",)
    if len(speed) / len(samples) < .95: return False, ("SPEED_COVERAGE",)
    if any(not 30 <= x <= 240 for x in hr): return False, ("HR_PHYSIOLOGICAL_BOUNDS",)
    if any(x < 0 or x > 12 for x in speed): return False, ("UNPHYSICAL_SPEED",)
    if any(s.get("pause", False) for s in samples): return False, ("LONG_PAUSE",)
    if any(s.get("lat") is None or s.get("lon") is None for s in samples): return False, ("MISSING_GPS",)
    return True, ()

def _weighted(samples, key):
    total = value = 0.0
    for i, s in enumerate(samples):
        carry = min(5, samples[i + 1]["t"] - s["t"]) if i + 1 < len(samples) else 0
        if s.get(key) is not None: value += s[key] * carry; total += carry
    return value / total if total else None

def _segment(a, duration=1200):
    s = _window(a, duration); ok, reasons = _qc(s, duration)
    if not ok: return {"athlete_id": a.athlete_id, "activity_id": a.activity_id, "qc_status": "REJECT", "exclusion_reasons": reasons}
    gps = tuple((round(x["lat"], 5), round(x["lon"], 5)) for x in s)
    return {"athlete_id": a.athlete_id, "activity_id": a.activity_id, "source_dataset": a.source_dataset,
            "start_time": a.date, "segment_start": 720, "segment_end": 720 + duration, "duration_s": duration,
            "mean_hr_bpm": round(_weighted(s, "hr"), 6), "mean_speed_mps": round(_weighted(s, "speed"), 6),
            "valid_hr_fraction": round(sum(x.get("hr") is not None for x in s) / len(s), 6),
            "valid_speed_fraction": round(sum(x.get("speed") is not None for x in s) / len(s), 6),
            "max_sample_gap_s": max((b["t"] - c["t"] for c, b in zip(s, s[1:])), default=0),
            "route_signature": gps, "route_direction": a.route_direction,
            "temperature_c": a.temperature_c, "relative_humidity_pct": a.relative_humidity_pct,
            "speed_method": "TIME_WEIGHTED_DEVICE_SPEED", "qc_status": "PASS", "exclusion_reasons": ()}

def _route_id(s):
    if s["qc_status"] != "PASS": return None
    # Direction is intentionally part of the frozen primary signature.
    key = (s["route_signature"][0], s["route_signature"][-1], s["route_direction"], len(s["route_signature"]))
    return sha256(repr(key).encode()).hexdigest()[:12]

def _pairs(rows, hr_caliper=3):
    rows = sorted(rows, key=lambda x: (x["start_time"], x["activity_id"]))
    out = []
    for left, right in zip(rows[::2], rows[1::2]):
        days = (datetime.fromisoformat(right["start_time"]) - datetime.fromisoformat(left["start_time"])).days
        if days < 1 or days > 7 or abs(right["mean_hr_bpm"] - left["mean_hr_bpm"]) > hr_caliper: continue
        out.append({"earlier": left["activity_id"], "later": right["activity_id"], "hr_diff": round(abs(right["mean_hr_bpm"] - left["mean_hr_bpm"]), 6), "d": round(right["mean_speed_mps"] - left["mean_speed_mps"], 6)})
    return out

def run(activities):
    unique = {a.activity_id: a for a in activities}
    segs = {k: _segment(unique[k]) for k in sorted(unique)}
    valid = [dict(v, route_group_id=_route_id(v)) for v in segs.values() if v["qc_status"] == "PASS"]
    pairs = []
    for athlete in sorted({a.athlete_id for a in activities}):
        rows = [x for x in valid if x["athlete_id"] == athlete]
        groups = {}
        for row in rows: groups.setdefault(row["route_group_id"], []).append(row)
        for route in sorted(groups): pairs += [{"athlete_id": athlete, "route_group_id": route, **p} for p in _pairs(groups[route])]
    states = {}
    for athlete in sorted({a.athlete_id for a in activities}):
        rows = [x for x in valid if x["athlete_id"] == athlete]; ps = [p for p in pairs if p["athlete_id"] == athlete]
        rejected = [x for x in segs.values() if x.get("athlete_id") == athlete and x["qc_status"] == "REJECT"]
        total = [x for x in segs.values() if x.get("athlete_id") == athlete]
        if len(ps) >= 3: state = AthleteState.ESTIMABLE
        elif any("MISSING_GPS" in x.get("exclusion_reasons", ()) for x in rejected): state = AthleteState.UNSUPPORTED_MEASUREMENT
        elif rejected and len(rejected) == len(total): state = AthleteState.INSUFFICIENT_TRACE_QUALITY
        elif not rows: state = AthleteState.UNSUPPORTED_MEASUREMENT
        elif len({x["route_group_id"] for x in rows}) > 1: state = AthleteState.INSUFFICIENT_ROUTE_OVERLAP
        else: state = AthleteState.INSUFFICIENT_HR_MATCHES if len(rows) >= 2 else AthleteState.INSUFFICIENT_REPEATS
        ds = [p["d"] for p in ps]; bias = sum(ds) / len(ds) if ds else None; sd = sqrt(sum((x - bias) ** 2 for x in ds) / (len(ds) - 1)) if len(ds) > 1 else None
        states[athlete] = {"state": state.value, "final_pair_count": len(ps), "candidate_count": len([a for a in activities if a.athlete_id == athlete]), "fixed_window_count": len(rows), "qc_count": len(rows), "route_compatible_count": len(rows), "hr_compatible_count": len(ps), "mean_paired_hr_difference": round(sum(p["hr_diff"] for p in ps)/len(ps), 6) if ps else None, "mean_speed": round(sum(x["mean_speed_mps"] for x in rows)/len(rows), 6) if rows else None, "signed_bias": round(bias, 6) if bias is not None else None, "sd_differences": round(sd, 6) if sd is not None else None, "TE": round(sd/sqrt(2), 6) if sd is not None else None, "agreement_limits": [round(bias-1.96*sd, 6), round(bias+1.96*sd, 6)] if sd is not None else None}
    return {"schema": "p2b-dev0-v1", "segments": {k: v for k, v in sorted(segs.items())}, "pairs": sorted(pairs, key=lambda x: (x["athlete_id"], x["earlier"])), "athletes": states, "route_tolerances": ROUTE_TOLERANCES}

def report(activities): return json.dumps(run(activities), sort_keys=True, separators=(",", ":"), default=str)
def speed_blinded(activities): return tuple(Activity(a.athlete_id, a.activity_id, a.date, tuple(dict(s, speed=9.0 - s.get("speed", 0)) for s in a.samples), a.source_dataset, a.route_direction, a.temperature_c, a.relative_humidity_pct) for a in activities)

def parse_tcx(data):
    root = ET.fromstring(data); rows = []
    for activity in root.findall(".//{*}Activity"):
        points = []
        for p in activity.findall(".//{*}Trackpoint"):
            t = p.findtext("{*}Time"); hr = p.findtext("{*}HeartRateBpm/{*}Value"); lat = p.findtext("{*}Position/{*}LatitudeDegrees"); lon = p.findtext("{*}Position/{*}LongitudeDegrees")
            if t: points.append({"time": t, "hr": int(hr) if hr else None, "lat": float(lat) if lat else None, "lon": float(lon) if lon else None})
        rows.append(points)
    return rows

def build_synthetic_fixture():
    base = datetime(2026, 1, 1, tzinfo=timezone.utc)
    out = []
    def add(athlete, n, hr=150, route="A", direction="OUT", bad=None, speed=3.0):
        samples = tuple({"t": t, "hr": (hr + (t // 600) * 4), "speed": speed + n * .01, "lat": (0.0 if route == "A" else 1.0) + t / 120000, "lon": 0.0 if route == "A" else 1.0} for t in range(720, 1921, 5))
        if bad == "hr": samples = tuple(dict(s, hr=None) if 820 <= s["t"] < 920 else s for s in samples)
        if bad == "gap": samples = tuple(s for s in samples if s["t"] < 820 or s["t"] > 850)
        if bad == "pause": samples = tuple(dict(s, pause=820 <= s["t"] < 850) for s in samples)
        if bad == "spike": samples = tuple(dict(s, speed=20 if s["t"] == 1220 else s["speed"]) for s in samples)
        if bad == "gps": samples = tuple(dict(s, lat=None, lon=None) if 1120 <= s["t"] < 1220 else s for s in samples)
        out.append(Activity(athlete, f"{athlete}-{n:02d}", (base + timedelta(days=n)).isoformat(), samples, route_direction=direction))
    for n in range(6): add("healthy", n)
    for n in range(3): add("hr_mismatch", n, hr=150 + n * 15)
    for n in range(3): add("route_mismatch", n, route="A" if n % 2 else "B")
    add("trace_bad", 0, bad="hr"); add("trace_bad", 1, bad="gap"); add("trace_bad", 2, bad="spike")
    add("unsupported", 0, bad="gps"); out.append(out[-1])
    return tuple(out)
