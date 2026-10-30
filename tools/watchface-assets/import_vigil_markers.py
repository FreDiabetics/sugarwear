"""Render the user's unchanged 450-unit Vigil marker artwork for WFF."""
from pathlib import Path
import argparse
import shutil
import re
import xml.etree.ElementTree as E
import cairosvg

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "design-assets/vigil"
FACE = ROOT / "watchfaces/vigil/src/main/res"


def render(source_directory=None):
    SOURCE.mkdir(parents=True, exist_ok=True)
    destination = FACE / "drawable-nodpi"
    destination.mkdir(parents=True, exist_ok=True)
    for name in ("vigil_minutemarkers_close", "vigil_minutemarkers_wide", "vigil_hourmarkers_close", "vigil_hourmarkers_wide"):
        path = SOURCE / (name + ".svg")
        if source_directory:
            shutil.copyfile(source_directory / path.name, path)
        assert E.parse(path).getroot().get("viewBox") == "0 0 450 450"
        artwork = path.read_text(encoding="utf-8")
        if "hourmarkers" in name:
            tree = E.fromstring(artwork)
            for parent in tree.iter():
                for child in list(parent):
                    if child.tag.endswith("line") and child.get("x1") == "225" and child.get("x2") == "225" and float(child.get("y1", "0")) > 225:
                        parent.remove(child)
            artwork = E.tostring(tree, encoding="unicode")
        # Native WFF tint supplies the original or chosen grayscale; keep only source alpha here.
        artwork = re.sub(r"#[0-9a-fA-F]{6}", "#ffffff", artwork)
        cairosvg.svg2png(bytestring=artwork.encode("utf-8"), write_to=str(destination / (name + ".png")), output_width=450, output_height=450)
    for name in ("hour_hand_transparent.png", "minute_hand_transparent.png", "second_hand_transparent.png"):
        shutil.copyfile(ROOT / "shared/sugarlicious/watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi" / name, destination / name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path)
    render(parser.parse_args().source)
