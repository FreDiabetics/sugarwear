# Independent product installation

Sugarlicious and SugarWear are separate repositories and products.

## Sugarlicious

From [FreDiabetics/sugarlicious](https://github.com/FreDiabetics/sugarlicious), with one connected phone and one connected watch:

```powershell
.\gradlew.bat installSugarliciousDebug
```

Installs only Mobile and its Wear companion (`app.sugarlicious`). The optional Push catalog contains Digital, ApeX and Orbit. It never installs SugarWear or Vigil.

## SugarWear and Vigil

Clone [FreDiabetics/sugarwear](https://github.com/FreDiabetics/sugarwear) with `git clone --recurse-submodules https://github.com/FreDiabetics/sugarwear.git`. From that checkout, with one connected watch:

```powershell
.\gradlew.bat installSugarWearDebug
```

Installs only `app.sugarwear` and `app.sugarwear.watchfacepush.vigil`. Neither installer clears data or changes the other product.

## Shared source ownership

SugarWear pins this repository as `shared/sugarlicious`. Only core-model, dexcom-g7, wear-protocol, wear-storage, ui-shared and complications are included as library projects. Shared conventions, artwork resources and generic tools also have one canonical source here; they are not copied into SugarWear. Product app modules, Vigil and its artwork/tests belong exclusively to SugarWear. Updating the gitlink is an explicit, tested shared-library upgrade.

## Legacy identity migration

Existing installations under former IDs need the one-time signed migration in the Kotlin command `./gradlew :tools:maintenance:run --args="migrate-products --help"`. It preserves settings, history and decrypted credentials, then disables the old package without deleting its data. Explicit devices and cached legacy bridge APKs built with `-PlegacyProductMigration=true` are required. For SugarWear, also pass `--sugarwear-root` pointing to its independent checkout. Ordinary installers reject the legacy build flag. Native widget/complication bindings require reassignment after an identity migration; sensor pairing is preserved.
