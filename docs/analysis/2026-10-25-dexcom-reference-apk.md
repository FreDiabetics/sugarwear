# User-provided Dexcom G7 reference APK

The user supplied `DexcomG7_1.6.1.4537f-broadcast.apk` as the phone app used daily, including its AndroidAPS broadcast modification. Static inspection only; the APK was not installed, executed, patched or republished.

- SHA-256: `F7F675868DC703653544FABAC3A0A6310D0A036775C351AEC71145232BD23B47`.
- Package `com.dexcom.g7`, versionCode 4537, versionName `1.6.1.4537`, minSdk 29, targetSdk 31. Package/version match the existing phone installation. This identifies the user's installed version, not the latest official Dexcom release.
- The manifest requests Bluetooth scan/connect, location/background location, foreground service, battery-optimization exemption, exact alarms and wake locks. These declarations do not prove runtime grants or identify a Samsung-specific radio setting.
- Inspected offline with JADX 1.5.6 from its official release. Kotlin metadata processing had to be disabled because protected metadata produced repeated parsing failures. The completed pass still reported 183 method reconstruction errors. Readable output includes Bluetooth UI helpers, but important code is flattened/obfuscated and strings are transformed by runtime helper calls. Native libraries are present as well.
- No recoverable, verified G7 scan-filter/reconnect timing/Broadcast-to-AndroidAPS implementation was established from this pass. In particular, absence of plain `startScan`/`connectGatt` names in a static search is not evidence that these operations do not occur. It is a limit of this protected artifact's static visibility.
- No protected code or guessed constants were copied into SugarWear. No change was made to the functioning Dexcom phone app, sensor pairing, receiver slot or AndroidAPS broadcast setup.

Further APK investigation would require a separate, controlled trace or deeper recovery of protected methods. It must not be presented as completed sensor-firmware analysis. Current fixes are based on the watch's measured callback/scanning behavior and independently readable reference implementations; see `2026-10-25-g7-radio-critical-path.md`.
