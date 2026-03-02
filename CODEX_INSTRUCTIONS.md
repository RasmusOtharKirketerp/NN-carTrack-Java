# Codex Instructions - NN Car Track (Java)

This file documents project-specific conventions, runtime modes, and behavior decisions established during development.

## Project Overview
- Language/build: Java + Maven
- Main class: `com.nncartrack.Simulation`
- Run script: `run.bat`

## Run Modes
- Normal UI mode:
  - `run.bat`
- Logs-only headless mode (faster, no graphics):
  - `run.bat logs`
- Logs mode forces app args + JVM headless flags:
  - `-Dnn.headless=true -Djava.awt.headless=true`
  - app arg `logs` is also passed to `main(...)`

## Logging
Logs are written to `logs/`.

Files:
- `logs/training_metrics.csv`
  - episode-level stats
- `logs/training_batches.csv`
  - batch-level training telemetry
- `logs/run_metadata.txt`
  - run configuration snapshot

Notes:
- Logger uses buffered writers to reduce runtime stutter.
- Batch log flushing is periodic (`LOG_FLUSH_EVERY_N_BATCHES`).

## Current Environment / Track Design
- Track has been scaled up (2x geometry compared to earlier baseline).
- Obstacles: 3 boxed obstacles in slalom layout (top/mid/bottom with staggered X positions).
- Finish line is visually and logically aligned via `Config.FINISH_LINE_X`.

## Car / Episode Behavior Decisions
- Cars start evenly distributed vertically.
- Boundary behavior:
  - Left/top/bottom and right-before-finish are enforced.
  - On boundary hit: penalty + clamp + full stop + continue (non-terminal).
- Obstacle behavior:
  - On obstacle hit: speed-dependent penalty + push out + full stop + continue (non-terminal).
- Crossing finish still marks that car finished.

## Sensor System (Important)
- Multi-ray sensors are used.
- Sensor count is configured by `Config.SENSOR_RAY_COUNT`.
- Current rays include forward directions + up/down + back sensor.
- Up/down sensors are range-scaled down (60%).
- Sensor detection semantics:
  - Detect obstacles and top/bottom/left boundaries.
  - Ignore far-right boundary behind finish line.
- Rendering:
  - Only triggered sensors are drawn.
  - Sensor lines are red with true opacity and thickness based on proximity.

## Reward System (Current)
Reward shaping is incremental (not absolute-position exploding reward):
- Step penalty each update.
- Incremental forward progress reward.
- Backward progress penalty.
- High-speed penalty above threshold.
- Stuck-window penalty if insufficient net progress over a window.
- Boundary collision penalty.
- Obstacle collision penalty (base + speed-scaled component).
- Finish reward.

## Network / Training Configuration Direction
Recent tuning changes to improve learning stability after adding richer sensors:
- Increased hidden size.
- Increased batch size.
- Slower epsilon decay.
- Added richer telemetry and anti-stuck shaping.

## Visualization Conventions
- Car circles use same color-hue family as their trail.
- Trails are thin.
- Sensor visualization only shows triggered rays.
- Fixed dashboard displays per-car metrics and mini history graph (last ~100 step rewards), instead of moving labels attached to cars.

## Known Practical Notes
- If UI appears in logs mode, ensure old Java processes are closed and use `run.bat logs`.
- Performance stalls were previously linked to synchronous logging; buffered logging is now used.

## Editing Guidelines for Future Changes
- Preserve run.bat dual mode behavior.
- Preserve non-terminal recovery behavior for boundary/obstacle collisions unless intentionally changed.
- Keep finish-line logic and drawing aligned through `Config.FINISH_LINE_X`.
- When changing sensors, update both:
  - NN input sizing (`Config.INPUT_SIZE`)
  - Rendering logic and sensor max-range checks
- Prefer adding config constants over magic numbers.
