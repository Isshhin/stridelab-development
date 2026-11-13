# R3 • Athlete-state measurement challenge

## Simultaneous Measurements, v1.0.2

Vollmer and colleagues supply beat-interval exports for 13 healthy adults. We read the first 32 consecutive interval entries after the HRData marker from every official Polar file, in file order. This is a fixed engineering prefix, not a physiological observation window. Every prefix had 32 positive readable values. Positivity cannot establish beat-detection accuracy. No training status or resting phase was assigned to these prefixes.

| Record | Minimum RR (ms) | Median RR (ms) | Maximum RR (ms) |
|---|---:|---:|---:|
| x001 | 553 | 579.5 | 613 |
| x002 | 357 | 668 | 1101 |
| x003 | 625 | 702.5 | 777 |
| x004 | 606 | 668 | 826 |
| x005 | 780 | 841.5 | 929 |
| x006 | 620 | 788.5 | 893 |
| x007 | 821 | 1007 | 1156 |
| x008 | 577 | 647.5 | 769 |
| x009 | 690 | 738.5 | 864 |
| x010 | 770 | 811 | 906 |
| x011 | 581 | 631 | 771 |
| x012 | 666 | 771.5 | 998 |
| x013 | 542 | 564.5 | 680 |

**Candidate R3-A1, RR alone as readiness evidence.** Compare the isolated first window with the frozen state's requirement for phase, signal quality and an individual baseline. Endpoint is whether the input justifies a readiness decision. Thirteen people contributed one window each, 416 beat intervals in total and zero repeated-day pairs. All 13 software decisions abstained by design. Two windows had identical median RR (668 ms) despite different ranges (357–1101 and 606–826 ms). A single median therefore loses observed variation. Its effect on readiness accuracy cannot be determined. The extreme intervals might reflect physiology, acquisition or artifact. No diagnosis or cleansing threshold follows.

**Disposition: REQUIRES_CONDITION** for interpreting a point summary. Preserve sensor, interval quality, phase and within-person history before interpreting readiness. **INSUFFICIENT_EVIDENCE** for any HRV baseline window or recovery prescription. The next falsification test compares simultaneously aligned Polar intervals and manual ECG beats within the same verified phases. Count excluded and conflicting windows; measure error by subject and phase. If discrepancies disappear after synchronization, distinguish clock alignment from physiological disagreement. A test of a hard-coded abstention cannot demonstrate decision superiority.

## Wrist PPG during Exercise, v1.0.0

Two inspected running headers (s3_run, s4_run) declare 15 channels, 256 Hz and 78,593 versus 74,753 samples. The first explicitly includes a timing channel for the non-ECG signals. Equal nominal frequency alone cannot establish ECG/wrist sample alignment. Two headers, no paired signal windows, no estimated HR error. Next use the reference beat annotations and device timing before measuring walking versus running errors. Comparator is ECG with hand-labelled peaks. Endpoint is within-participant paired HR disagreement with coverage. Applicability is DIRECT for alignment checks, INDIRECT for athlete state.

## Sport DB 2.0, release 2

The deposit reports 168 recordings from 130 participants. A recording may contain different subsets of ECG, HR, RR and breathing data. Treat channel absence as structural until verified against the device and protocol. The published conference abstract uses different cohort counts from the released deposit, so do not borrow its denominator. The public file API was unavailable. No participant-level results, fitted thresholds or estimates were produced. Next inventory the versioned archive by participant, sport, protocol, device and channel set before testing cross-sensor disagreement.

[Polar records, Vollmer et al.](https://physionet.org/content/simultaneous-measurements/1.0.2/raw_data/) · [Wrist record s3](https://physionet.org/content/wrist/1.0.0/s3_run.hea) · [Wrist record s4](https://physionet.org/content/wrist/1.0.0/s4_run.hea) · [Sport DB v2](https://doi.org/10.17632/kzkjkt7mx2.2)