# Performance and memory (Q03)

This is the measurement method and the report template. **No numbers are claimed in this file without a run on a
device**: debug and Robolectric timings are not evidence, and an emulator without hardware acceleration is not a
phone.

## Preconditions

- `fast` (R8, not debuggable) or `release` build, installed with its data cleared for a cold start.
- Record the device, Android version, screen refresh rate, CPU/GPU governor (if known), free storage, temperature,
  network type and whether the screen was at full brightness.
- One run per scenario per repeat, at least five repeats; report the median and the p95, not the best run.
- Wi-Fi controlled: one pass offline (cached library), one on the target network. A metered network is a separate
  pass, because the data saver changes preloading.

## Fixtures

Build the libraries once and keep their files:

| Fixture | Size | What it exercises |
|---|---|---|
| 1000 local books | EPUB/TXT mix, 100 with covers | library query, paging, FTS, cover decode |
| 10 000 online titles | one catalog source | library online projection, filters, shelf |
| 500+ page PDF | text layer and a scanned one | PDFium render, reflow, annotations, crop |
| Large CBZ | ≥ 2 GB, mixed page sizes | archive opening, page cache, split/spread |
| Tall webtoon | ≥ 30 000 px strip | decode budget, scrolling, memory |
| Large EPUB | ~20 MB text, 600 chapters | whole-book document build and first page |

## Scenarios and metrics

| Scenario | Metric | Tool |
|---|---|---|
| Cold start to the library | time to first frame, time to first useful row | `am start -W`, Perfetto |
| Warm start | time to first frame | `am start -W` |
| Open a book | time to first readable page (text/PDF/manga) | Perfetto slice around `ReaderViewModel` |
| Page turns | frame p50/p95/p99, % frames > 50 ms | `dumpsys gfxinfo <package> framestats`, Perfetto |
| Long strip / webtoon | memory steady and peak, frames | `dumpsys meminfo`, Perfetto |
| Library scroll of 1000 books | frames, memory after 3 minutes | `dumpsys gfxinfo`, `dumpsys meminfo` |
| Offline audiobook playback | CPU while playing, battery % per hour | Battery Historian or the system battery screen |
| Sync of a full library | bytes, requests, time | the in-app Traffic meter, Perfetto network track |

## Procedure

1. Install the `fast` build, clear data, copy the fixture library.
2. Cold start five times (force-stop between runs); record the five values.
3. Warm start five times (back to home, then open).
4. Open each fixture and record the first useful page; turn 30 pages or scroll for 60 s.
5. After each scenario, collect `dumpsys meminfo` and `dumpsys gfxinfo`; keep the Perfetto trace.
6. Repeat the same scenario on the same build after any optimisation; do not compare across builds or devices.

## Report template

```
Device/OS/build:
Fixture/network:
Scenario | repeat | p50 | p95 | p99 | >50 ms | memory steady | memory peak | notes
```

## Limitations recorded in this build

- No device or emulator run was made for 2.9.4. The authoring environment has no attached Android device.
- The numbers previously quoted in the plan are targets, not measurements.
- M05 (experimental renderer) stays off until this baseline exists: without a measured bottleneck there is nothing to
  justify a second renderer.
