# Paperkeep — Benchmark Baselines

> **Phase 1 baseline** (P1.13, 2026-04-24). All measurements are on Pixel 6a (Android 14, API 34)
> unless noted. For emulator measurements, the device is noted explicitly.
> Target for Phase 5 (launch): cold start < 500ms.

---

## Cold-Start Baseline

| Metric | Phase 1 Value | Phase 5 Target | Notes |
|---|---|---|---|
| Cold start (Time to First Frame) | **TBD — device run required** | < 500 ms | Run `adb shell am start-activity --start-profiler ...` on Pixel 6a |
| Warm start | TBD | < 200 ms | |
| Hot start | TBD | < 100 ms | |

### How to measure (Macrobenchmark)

```bash
# From android/ root:
./gradlew :benchmark:connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=app.paperkeep.benchmark.StartupBenchmark

# Output goes to android/benchmark/build/outputs/connected_android_test_additional_output/
```

The benchmark harness in `:benchmark` uses `StartupTimingMetric` and `FrameTimingMetric`.
Results are printed to `android/benchmark/build/outputs/**/*.json`.

---

## Edge Detection Overlay Frame Rate

| Metric | Phase 1 Value | Target | Notes |
|---|---|---|---|
| Overlay draw time per frame (P90) | TBD | ≤ 16 ms | Downsampled to 640px width before detection |
| Jank rate (> 16ms frames) | TBD | < 5% | |

---

## Capture → Encrypted Save Round Trip

| Metric | Phase 1 Value | Target | Notes |
|---|---|---|---|
| Capture → write encrypted file | TBD | < 2 s | Measured from shutter tap to `onWriteComplete` |

---

## APK Size

| Metric | Phase 1 Value | Phase 5 Target |
|---|---|---|
| Debug APK size | TBD | — |
| Release APK size (R8 full mode) | TBD | < 18 MB (Phase 1), < 25 MB (Phase 5) |

---

## How to run the full benchmark suite

```bash
# Ensure an emulator or Pixel 6a is connected:
adb devices

# Run all macrobenchmarks:
./gradlew :benchmark:connectedAndroidTest

# Run only startup benchmark:
./gradlew :benchmark:connectedAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=app.paperkeep.benchmark.StartupBenchmark
```

---

## Phase 1 Floor (acceptance gate)

- Cold start < **800 ms** on any mid-range 2022+ Android device (or emulator API 26+)
- Edge detection overlay < **16 ms/frame** P90 on a downsampled 640px frame
- APK release < **18 MB**
- Capture → encrypted save < **2 s**

These thresholds must all pass before Phase 2 begins.

---

## Notes

- Measurements marked "TBD" require a physical device run or an emulator with hardware acceleration.
- Update this file after each Macrobenchmark run with actual values and device/emulator details.
- The Macrobenchmark module (`android/benchmark/`) uses `CompilationMode.Full` for cold-start
  accuracy — this takes longer but gives results closer to what users see after install.
