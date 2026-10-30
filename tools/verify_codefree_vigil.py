"""Check only this product's release watchface APK, never the shared catalogue."""
import hashlib
import re
import zipfile
from pathlib import Path


def verify(apk):
    with zipfile.ZipFile(apk) as archive:
        if any(re.search(r"(^|/)classes[0-9]*\.dex$", name) for name in archive.namelist()):
            raise ValueError(f"{apk} contains executable DEX code")
    return hashlib.sha256(apk.read_bytes()).hexdigest()


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    apks = list((root / "watchfaces/vigil/build/outputs/apk/release").glob("*.apk"))
    if len(apks) != 1:
        raise SystemExit(f"Expected exactly one Vigil release APK, found {len(apks)}")
    print(f"PASS code-free: {apks[0].name} SHA-256 {verify(apks[0])}")
