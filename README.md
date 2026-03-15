# NN Car Track (Java)

This repository contains a reinforcement-learning car simulation written in Java. Multiple cars learn to drive from left to right on a 2D track with obstacles, using a small feed-forward Deep Q-Learning setup with prioritized replay.

## What the code does

- Runs episodes where each car observes sensors and position, picks an action, gets reward, and stores experiences.
- Trains a neural network from replay batches during the episode and with optional extra training at episode end.
- Renders training in Swing (track, cars, sensors, reward dashboard, top-run overlays) or runs fully headless.
- Logs episode and batch metrics to `logs/*.csv` for later analysis.

## Project structure

- `src/main/java/com/nncartrack/Simulation.java`: Main loop, rendering, episode orchestration, model checkpointing, top-run snapshots, app entrypoint.
- `src/main/java/com/nncartrack/Car.java`: Agent/environment interaction: state construction, action application, reward shaping, obstacle and boundary handling.
- `src/main/java/com/nncartrack/NeuralNetwork.java`: Q-network forward pass, epsilon-greedy policy, replay training, TD error/priorities update, model save/load.
- `src/main/java/com/nncartrack/PrioritizedReplayMemory.java`: Shared singleton PER buffer and weighted sampling.
- `src/main/java/com/nncartrack/Config.java`: All tunables (track, rewards, RL hyperparameters, UI, modes).
- `src/main/java/com/nncartrack/Logger.java`: Console + CSV logging (`training_metrics.csv`, `training_batches.csv`, `run_metadata.txt`).
- `src/main/java/com/nncartrack/LiveDataWindow.java`: Real-time telemetry chart (reward/loss/Q/epsilon).
- `src/main/java/com/nncartrack/LiveRankingWindow.java`, `RunSnapshot.java`: Live top-15 ranking of best runs and stored trail snapshots.
- `run.bat`, `run_headless.bat`: Launch scripts for normal, headless, logs-only, and play/inference modes.

## Runtime flow (code walkthrough)

1. `Simulation.main(...)` parses mode args (`logs`, `headless`, `play`) and starts the simulation.
2. `Simulation.runEpisode()` iterates time steps until budget is used or all cars terminate.
3. Each `Car.update()`:
   - Builds state vector: 6 ray distances + normalized `(x, y)`.
   - Chooses action with `NeuralNetwork.selectAction(...)` (epsilon-greedy unless play mode).
   - Moves, resolves collisions/boundaries, computes shaped reward.
   - Adds `(state, action, reward, nextState, done)` to replay memory.
   - Trains every `Config.TRAIN_EVERY_N_STEPS` or when done.
4. `NeuralNetwork.train()` samples PER batch, computes targets, applies weighted updates, and writes batch stats.
5. End of episode:
   - Stats + telemetry update.
   - Optional success-based extra replay batches.
   - Saves best model when a new top reward is reached.
   - Captures run snapshots for live ranking.
   - Resets cars for next episode.

## Reward and state design

- State (`Config.INPUT_SIZE = 8`):
  - 6 sensor rays (forward, diagonals, up/down, back) normalized by ray range.
  - Normalized X and Y position.
- Major positive reward sources:
  - New forward max-X progress.
  - New territory exploration.
  - Finish line bonus (`FINISH_REWARD`).
- Major penalties:
  - Step cost.
  - Stationary/stuck behavior.
  - Backward movement.
  - High-speed risk.
  - Boundary and obstacle collision penalties.

## Modes and running

Requirements:

- Java 21 or newer
- Maven (or Maven Wrapper if `mvnw.cmd` is present)

Windows launch:

```bat
run.bat
run.bat logs
run.bat headless
run.bat play
run.bat playlogs
```

`run.bat` requires Java 21+ and will:
- prefer `JAVA_HOME_25` if set
- otherwise prefer `JAVA_HOME_21` if set
- otherwise use `JAVA_HOME` when it points to JDK 21+
- stop early with a clear error if an older Java is active

Notes:

- `play` mode sets `-Dnn.mode=play` (inference only, no training).
- `headless` disables UI and file logs.
- `logs` runs headless but keeps CSV logging enabled.

## Git history and codebase progression

This summary is based on `git log --oneline --graph --all` and per-commit file changes.

1. `572422d` (2024-11-30) `first commit`
   - Initial RL simulation core (`Car`, `Config`, `NeuralNetwork`, `Simulation`).
2. `7e01e94` (2024-11-30) `maven`
   - Maven build setup (`pom.xml`) and early telemetry/logging classes.
3. `5a34ee4` -> `5bb421f` (2024-12-01)
   - Transition to shared prioritized replay memory (`PrioritizedReplayMemory` introduced and integrated).
4. `71635f3` (2024-12-01) `NN working ok`
   - Stabilization of network/episode behavior.
5. `d94f05f` (2024-12-02) `road drawing`
   - Stronger track/obstacle visualization direction.
6. `3a84398` (2024-12-02) `Progressbars added`
   - Episode/step progress UI added to simulation.
7. `0b80d7b` (2024-12-02) `optimizations`
   - Replay memory sampling/performance improvements.
8. `577c6a1` (2024-12-02) `added picture car`
   - Visual/UI polish and asset integration.
9. `46c135e` (2026-02-18) `Upgrade Java version from 17 to 21`
   - Toolchain/runtime modernization in `pom.xml`.
10. `b7ff98c` + `ea2dfec` + `32ab6aa` (2026-03-02)
    - Major training/logging/simulation updates: richer reward instrumentation, better telemetry, headless/play workflows, model-path handling.
11. `eb99fba` (2026-03-02) merge into `main`
    - Consolidates appmod branch work, including ranking window, run snapshots, launch scripts, and generated artifacts.

## Current state (main)

The current `main` branch includes:

- Java 21 build configuration.
- Multi-car training and inference mode split.
- Shared prioritized replay memory.
- Detailed reward-event tracking in `Car`.
- CSV telemetry for episodes and training batches.
- Two live dashboards (`LiveDataWindow`, `LiveRankingWindow`).
- Best-model checkpointing and configurable model paths.
