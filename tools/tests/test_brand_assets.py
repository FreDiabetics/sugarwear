from pathlib import Path
import unittest
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parents[2]
A = "{http://schemas.android.com/apk/res/android}"


class BrandAssetsTest(unittest.TestCase):
    def test_sugarwear_uses_only_final_visible_source_symbol(self):
        source = E.parse(ROOT / "design-assets/sugarwear/adaptive108/combined.svg").getroot()
        symbol = E.parse(ROOT / "design-assets/sugarwear/adaptive108/foreground.svg").getroot()
        visible = source.findall('.//{http://www.w3.org/2000/svg}path')[-1].get('d')
        self.assertEqual(symbol.find("{http://www.w3.org/2000/svg}path").get("d"), visible)
        runtime = E.parse(ROOT / "shared/sugarlicious/ui-shared/src/main/res/drawable/ic_sugarwear_filled.xml").getroot()
        self.assertEqual(len(runtime.findall(".//path")), 1)
        self.assertEqual(runtime.find(".//path").get(A + "pathData"), visible)
        self.assertEqual(runtime.find(".//path").get(A + "fillColor"), "#FFFFFFFF")


    def test_launcher_gradients_match_full_masters_and_keep_symbols_separate(self):
        for master, targets in [
            ("design-assets/sugarwear/adaptive108/combined.svg", ["g7watch/src/main/res/drawable/sugarwear_launcher_background.xml"]),
        ]:
            master_root = E.parse(ROOT / master).getroot()
            source = master_root.find(".//{http://www.w3.org/2000/svg}linearGradient")
            for target in targets:
                runtime_root = E.parse(ROOT / target).getroot()
                if master_root.find('.//{http://www.w3.org/2000/svg}image') is not None:
                    self.assertEqual(runtime_root.tag, 'bitmap')
                    self.assertEqual(runtime_root.get(A + 'gravity'), 'fill')
                    import cairosvg, io
                    from PIL import Image, ImageChops
                    background = ROOT / 'design-assets/sugarwear/adaptive108/background.svg'
                    expected = Image.open(io.BytesIO(cairosvg.svg2png(url=str(background),output_width=432,output_height=432))).convert('RGBA')
                    actual = Image.open(ROOT / 'g7watch/src/main/res/drawable-nodpi/sugarwear_launcher_background_art.png').convert('RGBA')
                    self.assertIsNone(ImageChops.difference(expected,actual).getbbox())
                    continue
                runtime = runtime_root.find(".//gradient")
                for svg_key, android_key in [("x1", "startX"), ("y1", "startY"), ("x2", "endX"), ("y2", "endY")]:
                    self.assertEqual(source.get(svg_key), runtime.get(A + android_key))
                self.assertEqual([(x.get("offset"), x.get("stop-color").lower()) for x in source], [(x.get(A + "offset"), ("#" + x.get(A + "color")[3:]).lower()) for x in runtime])


    def test_adaptive_layers_center_exact_source_inside_safe_66_unit_motif(self):
        for directory, modules in [('sugarwear', ['g7watch'])]:
            source = E.parse(ROOT / 'design-assets' / directory / 'adaptive108/foreground.svg').getroot()
            data = source.find('{http://www.w3.org/2000/svg}path').get('d')
            for module in modules:
                filename = 'ic_sensor_launcher_foreground.xml' if module == 'g7watch' else 'ic_launcher_foreground.xml'
                runtime = E.parse(ROOT / module / 'src/main/res/drawable' / filename).getroot()
                self.assertEqual(runtime.tag, 'vector')
                self.assertEqual(runtime.get(A + 'viewportWidth'), '108')
                self.assertEqual(runtime.get(A + 'viewportHeight'), '108')
                self.assertEqual(runtime.find('.//path').get(A + 'pathData'), data)
                group = runtime.find('group')
                self.assertEqual(group.get(A + 'scaleX'), str(66/108))
                self.assertEqual(group.get(A + 'scaleY'), str(66/108))
                self.assertEqual(float(group.get(A + 'translateX')), 21)
                self.assertEqual(float(group.get(A + 'translateY')), 21)
                mono_name = 'ic_sensor_launcher_monochrome.xml' if module == 'g7watch' else 'ic_launcher_monochrome.xml'
                self.assertEqual((ROOT / module / 'src/main/res/drawable' / filename).read_bytes(), (ROOT / module / 'src/main/res/drawable' / mono_name).read_bytes())
