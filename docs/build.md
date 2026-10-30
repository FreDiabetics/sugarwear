# Build and install

Clone with submodules, or initialize them after checkout:

```sh
git submodule update --init --recursive
```

Use JDK 21 and Android SDK platform 37, build tools 37.0.0, and platform tools. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to your SDK directory. The repository includes its own Gradle wrapper and needs no sibling checkout.

Run checks from this repository root:

```sh
python -m unittest discover -s tools/tests
./gradlew test assembleDebug :watchfaces:vigil:assembleRelease detekt ktlintCheck lint
```

CI also validates Vigil using the official pinned WFF validator and checks that its release APK has no DEX code.

Connect exactly one Wear OS watch through ADB and enable debugging. Other connected phones are ignored; multiple physical watches cause an explicit error. Duplicate wireless transports are grouped by hardware serial. In the Android Studio terminal on Windows, build and install with this single command:

```powershell
.\gradlew.bat installSugarWearDebug
```

This installs only `app.sugarwear` and `app.sugarwear.watchfacepush.vigil` on the selected watch. It never installs a phone application. ADB must confirm `Success` for each package. This command does not migrate data or uninstall older packages.

Shared libraries are `core-model`, `dexcom-g7`, `wear-protocol`, `wear-storage`, `ui-shared`, and `complications`, all under `shared/sugarlicious`. To update shared sources, review and commit a new submodule revision; do not copy shared Kotlin sources into this repository.

## Source provenance

The SugarWear application and Vigil sources were extracted from FreDiabetics/sugarlicious at commit `4d394c92be946dbb7101c9f026e0abedd7d84f1c`, preserving the existing license and compatibility class names. Their Android application IDs, signing configuration and stored data contracts remain unchanged. The submodule records the independently tested revision of the shared libraries.
