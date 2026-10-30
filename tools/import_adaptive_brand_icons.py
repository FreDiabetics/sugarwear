"""Import owned adaptive artwork through the canonical shared tool."""
from pathlib import Path
import runpy
import sys

ROOT = Path(__file__).resolve().parents[1]
if __name__ == "__main__":
    sys.path.insert(0, str(ROOT / "shared/sugarlicious/tools"))
    sys.argv[1:1] = ["--product", "sugarwear", "--repository-root", str(ROOT)]
    runpy.run_path(str(ROOT / "shared/sugarlicious/tools/import_adaptive_brand_icons.py"), run_name="__main__")
