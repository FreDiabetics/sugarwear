# SugarWear

SugarWear is a standalone Wear OS Continuous Glucose Monitoring (CGM) application providing real-time glucose tracking, customizable graphs, alarms, tiles, and complications.

## Overview

SugarWear provides a direct Bluetooth connection for Dexcom G7 on Wear OS, offering low-latency glucose monitoring, emergency alarm overlays, and rich watchface integrations.

## Features

- **Direct Wear OS Bluetooth Collector**: Direct G7 communication without requiring a phone bridge.
- **Customizable Glucose Graph**: High-contrast, time-scaled canvas graph renderer.
- **Full-Screen Alarm Overlays**: Urgent low, low, high, and technical warning alerts.
- **Wear OS Tiles and Complications**: ProtoLayout tiles and complications for watchfaces.
- **Universal UI Playground**: Debug visual environment with live style tuning and scenario simulation.

## Building

Build the debug Watch application and watchface:

```powershell
.\gradlew.bat :app-sugarwear:assembleDebug :watchfaces:vigil:assembleDebug
```

Run unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

## License

See [LICENSES/README.md](LICENSES/README.md) for third-party notices and licensing information.
