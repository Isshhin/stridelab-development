"""Decision-time descriptive shadow summaries for invented P2-A fixtures only."""
from datetime import datetime, timezone
from statistics import median

LINEAGE_FIELDS = ("athlete_id", "discipline", "event_at", "source",
                  "source_version", "protocol", "device", "rights",
                  "plan_revision")


def _time(value):
    if not isinstance(value, str):
        raise ValueError("timestamps must be ISO-8601 strings")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise ValueError("invalid ISO-8601 timestamp") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("timestamps must include a timezone")
    return parsed.astimezone(timezone.utc)


def summarize(history, decision_at, checkin=None):
    """Return history-only and optional check-in descriptive views; never actions."""
    cutoff = _time(decision_at)
    rows = list(history)
    ids = [row.get("id") for row in rows]
    if any(not isinstance(item, str) or not item for item in ids) or len(set(ids)) != len(ids):
        raise ValueError("observation IDs must be nonempty and unique")

    visible, excluded, units = [], 0, set()
    for row in rows:
        if any(not isinstance(row.get(field), str) or not row[field]
               for field in LINEAGE_FIELDS):
            raise ValueError("observation is missing required Session G lineage")
        observed, available = _time(row.get("observed_at")), _time(row.get("available_at"))
        event = _time(row["event_at"])
        if observed > available:
            raise ValueError("observation cannot be available before it is observed")
        if event > observed:
            raise ValueError("observation cannot precede its event")
        if available > cutoff:
            continue
        if (row.get("quality") != "valid"
                or not isinstance(row.get("value"), (int, float))
                or isinstance(row.get("value"), bool)
                or row.get("value") < 0):
            excluded += 1
            continue
        visible.append(row)
        units.add(row.get("unit"))
    if visible and any(row["athlete_id"] != visible[0]["athlete_id"]
                       or row["discipline"] != visible[0]["discipline"]
                       for row in visible):
        raise ValueError("one shadow view must not mix athlete or discipline")
    if len(units) > 1 or (visible and (None in units or "" in units)):
        raise ValueError("visible observations must use one explicit unit")

    def view(inputs):
        values = [row["value"] for row in inputs]
        support = len(inputs)
        newest = max((_time(row["available_at"]) for row in inputs), default=None)
        return {
            "status": "DESCRIPTIVE_ONLY" if support >= 2 else "ABSTAIN_UNCALIBRATED",
            "input_ids": [row["id"] for row in inputs],
            "eligible_count": support,
            "excluded_count": excluded,
            "descriptive_median": median(values) if support >= 2 else None,
            "unit": next(iter(units)) if units else None,
            "provenance": [{"id": row["id"], "lineage": {
                field: row[field] for field in LINEAGE_FIELDS
            }, "unit": row["unit"], "quality": row["quality"],
                "observed_at": row["observed_at"],
                "available_at": row["available_at"]} for row in inputs],
            "newest_input_age_seconds": (cutoff - newest).total_seconds() if newest else None,
            "uncertainty_reason": None if support >= 2 else "INSUFFICIENT_HISTORY",
            "model_version": "p2a-shadow-descriptive-v1",
        }

    history_view = view(visible)
    plus = list(visible)
    checkin_status = "NOT_PROVIDED"
    if checkin is not None:
        if any(not isinstance(checkin.get(field), str) or not checkin[field]
               for field in LINEAGE_FIELDS):
            raise ValueError("check-in is missing required Session G lineage")
        observed, available = _time(checkin.get("observed_at")), _time(checkin.get("available_at"))
        event = _time(checkin["event_at"])
        if observed > available:
            raise ValueError("check-in cannot be available before it is observed")
        if event > observed:
            raise ValueError("check-in cannot precede its event")
        if checkin.get("id") in ids:
            raise ValueError("check-in ID must be unique")
        if available > cutoff:
            checkin_status = "CHECKIN_NOT_AVAILABLE_AS_OF_DECISION"
        elif (checkin.get("quality") != "reported"
              or not isinstance(checkin.get("value"), (int, float))
              or isinstance(checkin.get("value"), bool)
              or checkin.get("value") < 0):
            checkin_status = "CHECKIN_INVALID_OR_UNAVAILABLE"
        else:
            if visible and (checkin["athlete_id"] != visible[0]["athlete_id"]
                            or checkin["discipline"] != visible[0]["discipline"]):
                raise ValueError("check-in athlete or discipline does not match history")
            checkin_status = "CHECKIN_AVAILABLE"
            plus.append(checkin)
    plus_view = view(plus)
    if checkin_status == "CHECKIN_AVAILABLE":
        plus_view["status"] = "ABSTAIN_UNCALIBRATED"
        plus_view["descriptive_median"] = None
        plus_view["unit"] = None
        plus_view["uncertainty_reason"] = "CHECKIN_SCALE_NOT_CALIBRATED_WITH_HISTORY"
    return {"decision_at": cutoff.isoformat(), "checkin_status": checkin_status,
            "history_only": history_view, "history_plus_checkin": plus_view}
