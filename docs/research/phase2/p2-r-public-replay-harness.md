# Replay contract and reproduction

The executable vertical slice is research/validation/p2_public_replay.py. It supports the Polar RR representation from the open PhysioNet v1.0.2 deposit. Cache original x001_POLAR.hrm through x013_POLAR.hrm outside Git from the [publisher's raw directory](https://physionet.org/content/simultaneous-measurements/1.0.2/raw_data/). Confirm version, dataset, record and interval mode before running. Only use admitted files from this source. Never use the CLI on an arbitrary HRM export as proof of source identity.

From research/validation run:

```
python -m unittest -v test_p2_public_replay
python p2_public_replay.py /external/cache/x001_POLAR.hrm --beats 32
```

Unit of observation is one completed beat interval in milliseconds. Time zero is the synthetic start of the device interval stream. Device-time availability at interval end is a simulation assumption. Actual upload and correction times are unknown. The replay accepts a distinct availability clock for delayed observations and includes arrivals exactly at cutoff. It filters participant and cutoff before computing summaries, rejects ambiguous visible duplicate sequences and orders by time and sequence. It exposes no outcome or retrospective phase-correction input. Raw records retain their own schema. The output binds observation IDs, sensor, version, missing context, license, applicability and the isolated-use boundary. These summaries remain outside any product parameter registry.

This first slice supports abstention only. KEEP, PROGRESS, MAINTAIN, REDUCE, RE_ENTRY and NO_AUTOMATED_DECISION are vocabulary reserved for separately tested candidates. The registry is a governance boundary; the tool is not a license detector. No cross-dataset action runner, ECG-alignment module, Groningen or 4TU parser is verified. Extend the interface when a second executable representation has been checked against source bytes.