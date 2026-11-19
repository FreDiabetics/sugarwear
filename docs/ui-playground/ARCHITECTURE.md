# SugarWear UI Playground Architecture

## Overview

The SugarWear Universal UI Playground is a dedicated, debug-only visual development environment for Wear OS. It enables rapid previewing,Live Edit tuning, scenario playback, and visual parameter export across all SugarWear UI surfaces (Home Overview, Glucose Graph Canvas, Alarm Screens, System Notifications, and ProtoLayout Tiles) without requiring an active sensor connection or triggering medical/BLE side effects.

## Architectural Boundaries

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SugarWear App Module                            │
├──────────────────────────────────┬─────────────────────────────────────┤
│        Production SourceSet      │            Debug SourceSet          │
│            (src/main)            │               (src/debug)           │
│                                  │                                     │
│  ┌────────────────────────────┐  │  ┌───────────────────────────────┐  │
│  │ UI Visual Style Contracts  │  │  │     PlaygroundActivity        │  │
│  │ (Graph, Home, Alarm Styles)│  │  │      PlaygroundScreen        │  │
│  └──────────────┬─────────────┘  │  └──────────────┬────────────────┘  │
│                 │                │                 │                   │
│  ┌──────────────▼─────────────┐  │  ┌──────────────▼────────────────┐  │
│  │    Production UI Views     │  │  │     PlaygroundViewModel       │  │
│  │ (CgmAlarmScreenContent,    │◄─┼──┤    (StateFlow, Playback)     │  │
│  │ CgmCollectorGraphView)     │  │  └──────────────┬────────────────┘  │
│  └────────────────────────────┘  │                 │                   │
│                                  │  ┌──────────────▼────────────────┐  │
│                                  │  │    Demo & Data Subsystems     │  │
│                                  │  │ (DemoGlucoseFactory, Mapping) │  │
│                                  │  └───────────────────────────────┘  │
└──────────────────────────────────┴─────────────────────────────────────┘
```

## Key Architectural Principles

1. **Strict Production Isolation**: All Playground launcher activities, view models, demo generators, and debug publishers are located exclusively in `src/debug`. Release builds omit these classes entirely.
2. **Reuse of Original Rendering Engines**: The Playground does NOT reimplement graph drawing or alarm UI. It calls the exact same `CgmCollectorGraphView` / `cgmSharedGraphInput` canvas renderer and `CgmAlarmScreenContent` composable as the production app.
3. **Passive Read-Only Data Isolation**: In LIVE mode, the playground passively reads existing database entries. It never issues BLE commands, triggers sensor reconnects, plays alarm audio, or modifies medical thresholds.
4. **Deterministic Scenario Simulation**: In DEMO mode, `DemoGlucoseFactory` generates reproducible glucose sequences using fixed seeds.
5. **Zero Side-Effect Actions**: Alarm buttons in the Playground perform local UI state updates without dismissing real alarms or scheduling real snooze timers in production stores.
