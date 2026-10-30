"""Vigil reuses canonical hands and keeps rounded indices clear of provider ink."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
FACE = ROOT / "watchfaces/vigil"


class VigilHandsTest(unittest.TestCase):
    def test_open_app_action_is_a_local_native_launch_not_a_full_face_complication(self):
        root = ET.parse(FACE / "src/main/res/raw/watchface.xml").getroot()
        action = root.find("Scene/Group[@name='vigil-open-sugarwear']")
        self.assertIsNotNone(action)
        self.assertEqual("300", action.get("width"))
        self.assertEqual("108", action.get("height"))
        self.assertEqual("app.sugarwear/app.aapswear.g7watch.G7WatchActivity", action.find("Launch").get("target"))

    def test_analog_text_and_pairing_button_render_below_native_hands(self):
        root = ET.parse(FACE / "src/main/res/raw/watchface.xml").getroot()
        ids = [s.get("slotId") for s in root.find("Scene").findall("ComplicationSlot")]
        self.assertLess(ids.index("9"), ids.index("7"))

    def test_scale_age_text_is_inset_inside_the_current_graph_surface(self):
        root = ET.parse(FACE / "src/main/res/raw/watchface.xml").getroot()
        status = root.find(".//ComplicationSlot[@slotId='2']")
        self.assertEqual(40, int(status.get("x")))
        self.assertEqual(146, int(status.get("y")))

    def test_analog_preview_keeps_square_provider_art_in_both_marker_layouts(self):
        for part in self.face.findall(".//ComplicationSlot[@slotId='9']//PartImage"):
            self.assertEqual('450', part.get('width'))
            self.assertEqual('450', part.get('height'))

    def setUp(self):
        self.face = ET.parse(FACE / "src/main/res/raw/watchface.xml").getroot()

    def test_provider_clock_keeps_full_rendering_and_center_tap_separate_with_eight_slots(self):
        scene = self.face.find("Scene")
        slots = scene.findall(".//ComplicationSlot")
        self.assertEqual(8, len(slots))
        self.assertTrue(all(slot.get("isCustomizable") == "FALSE" for slot in slots))
        hands = scene.find(".//ComplicationSlot[@slotId='7']")
        center = scene.find(".//ComplicationSlot[@slotId='8']")
        self.assertIs(center, list(scene)[-1])
        self.assertEqual(8, len(scene.findall("ComplicationSlot")))
        self.assertEqual(["0", "0", "450", "450"], [hands.get(key) for key in ("x", "y", "width", "height")])
        self.assertEqual(["0", "0", "450", "450"], [hands.find("BoundingBox").get(key) for key in ("x", "y", "width", "height")])
        self.assertEqual(["200", "200", "50", "50"], [center.get(key) for key in ("x", "y", "width", "height")])
        self.assertEqual(["0", "0", "50", "50"], [center.find("BoundingOval").get(key) for key in ("x", "y", "width", "height")])
        self.assertIsNone(scene.find("AnalogClock"))
        self.assertEqual("ANALOG", self.face.find("Metadata[@key='CLOCK_TYPE']").get("value"))
        build = (ROOT / "shared/sugarlicious/complications/build.gradle.kts").read_text()
        for resource in ("hour_hand_transparent", "minute_hand_transparent", "second_hand_transparent"):
            self.assertIn('"' + resource + '.png"', build)
            self.assertTrue((ROOT / "shared/sugarlicious/watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi" / (resource + ".png")).is_file())

    def test_digital_clock_and_hands_both_supply_active_and_ambient_images(self):
        self.assertIsNone(self.face.find("Scene/ComplicationSlot[@slotId='6']"))
        for slot_id in ("0", "1", "5"):
            slot = self.face.find(f".//ComplicationSlot[@slotId='{slot_id}']")
            images = slot.findall(".//PartImage/Image")
            self.assertEqual(["[COMPLICATION.SMALL_IMAGE]", "[COMPLICATION.SMALL_IMAGE_AMBIENT]"], [image.get("resource") for image in images])
            parts = slot.findall(".//PartImage")
            self.assertEqual("0", parts[0].find("Variant[@mode='AMBIENT']").get("value"))
            self.assertEqual("255", parts[1].find("Variant[@mode='AMBIENT']").get("value"))

    def test_header_and_graph_action_mirrors_restore_taps_above_hands(self):
        scene = self.face.find("Scene")
        slots = scene.findall("ComplicationSlot")
        self.assertEqual(["0", "1", "2", "5", "9", "7", "10", "8"], [slot.get("slotId") for slot in slots])
        original = scene.find("ComplicationSlot[@slotId='1']")
        mirror = scene.find("ComplicationSlot[@slotId='10']")
        self.assertEqual(["0", "140", "450", "285"], [original.get(k) for k in ("x", "y", "width", "height")])
        self.assertEqual(["0", "140", "450", "205"], [mirror.get(k) for k in ("x", "y", "width", "height")])
        analog = scene.find("ComplicationSlot[@slotId='9']")
        self.assertEqual(['0', '0', '450', '450'], [analog.get(k) for k in ('x', 'y', 'width', 'height')])
        self.assertIn('DirectToWatchAnalogHeaderComplication', analog.find('DefaultProviderPolicy').get('primaryProvider'))
        self.assertEqual('[CONFIGURATION.showHands] == "TRUE"', analog.find('.//Expression').text)

    def test_native_visibility_switch_hides_hands_and_center_content(self):
        option = self.face.find("UserConfigurations/BooleanConfiguration[@id='showHands']")
        self.assertEqual("FALSE", option.get("defaultValue"))
        for slot_id in ("7", "8"):
            condition = self.face.find(f"Scene/ComplicationSlot[@slotId='{slot_id}']/Complication/Group/Condition")
            self.assertEqual('[CONFIGURATION.showHands] == "TRUE"', condition.find("Expressions/Expression").text)
            self.assertIsNone(condition.find("Default"))
        info = ET.parse(FACE / "src/main/res/xml/watch_face_info.xml").getroot()
        self.assertEqual("true", info.find("Editable").get("value"))

    def test_digital_time_can_be_hidden_in_both_display_modes(self):
        option = self.face.find("UserConfigurations/BooleanConfiguration[@id='showDigitalTime']")
        self.assertEqual("TRUE", option.get("defaultValue"))
        condition = self.face.find("Scene/ComplicationSlot[@slotId='5']/Complication/Condition")
        self.assertEqual('[CONFIGURATION.showDigitalTime] == "TRUE" && [CONFIGURATION.showHands] == "FALSE"', condition.find("Expressions/Expression").text)
        self.assertEqual(2, len(condition.findall("Compare/Group/PartImage")))
        self.assertIsNone(condition.find("Default"))

    def test_hour_markers_have_an_independent_visibility_switch(self):
        self.assertIsNotNone(self.face.find("UserConfigurations/BooleanConfiguration[@id='showHourMarkers']"))
        self.assertEqual('[CONFIGURATION.showHourMarkers] == "TRUE"', self.face.find(".//Expression[@name='hourVisible']").text)

    def test_minute_markers_have_an_independent_visibility_switch(self):
        self.assertIsNotNone(self.face.find("UserConfigurations/BooleanConfiguration[@id='showMinuteMarkers']"))
        self.assertEqual('[CONFIGURATION.showMinuteMarkers] == "TRUE"', self.face.find(".//Expression[@name='minuteVisible']").text)

    def test_markers_preserve_user_artwork_and_follow_hand_visibility(self):
        markers = self.face.find("Scene/Group[@name='vigil-perimeter-markers']")
        self.assertEqual('[CONFIGURATION.showHands] == "TRUE"', markers.find('Condition/Expressions/Expression').text)
        self.assertIsNone(markers.find('.//PartDraw'))
        images = markers.findall('.//PartImage')
        self.assertEqual(['vigil_minutemarkers_close', 'vigil_minutemarkers_wide', 'vigil_hourmarkers_close', 'vigil_hourmarkers_wide'], [image.find('Image').get('resource') for image in images])
        for image in images:
            self.assertEqual(['0', '0', '450', '450'], [image.get(key) for key in ('x', 'y', 'width', 'height')])
            svg = ET.parse(ROOT / 'design-assets/vigil' / (image.find('Image').get('resource') + '.svg')).getroot()
            self.assertEqual('0 0 450 450', svg.get('viewBox'))

    def test_marker_grayscale_options_start_with_original_and_have_no_hues(self):
        for kind in ('minute', 'hour'):
            options = self.face.findall(f"UserConfigurations/ColorConfiguration[@id='{kind}MarkerColor']/ColorOption")
            self.assertEqual('original', options[0].get('id'))
            self.assertEqual('#FFFFFFFF', options[1].get('colors'))
            self.assertEqual('#FF000000', options[-1].get('colors'))
            for option in options:
                color = option.get('colors')
                self.assertEqual(color[3:5], color[5:7])
                self.assertEqual(color[5:7], color[7:9])

    def test_bottom_hour_marker_is_hidden_in_both_variants(self):
        from PIL import Image
        for variant in ('close', 'wide'):
            image = Image.open(FACE / 'src/main/res/drawable-nodpi' / f'vigil_hourmarkers_{variant}.png').convert('RGBA')
            self.assertIsNone(image.crop((219, 400, 232, 450)).getchannel('A').getbbox())

    def test_native_seconds_share_center_opacity_mask_and_offer_red_or_medium_gray(self):
        color = self.face.find("UserConfigurations/ColorConfiguration[@id='secondColor']")
        self.assertEqual('red', color.get('defaultValue'))
        self.assertEqual(['#FFFF3B30', '#FF9E9E9E'], [option.get('colors') for option in color])
        group = self.face.find(".//Group[@name='vigil-native-hands']")
        self.assertEqual('160', group.find("Variant[@mode='AMBIENT']").get('value'))
        self.assertEqual('[COMPLICATION.SMALL_IMAGE]', group.find("PartImage[@renderMode='MASK']/Image").get('resource'))
        self.assertEqual('TRUE', self.face.find("UserConfigurations/BooleanConfiguration[@id='showSecondHand']").get('defaultValue'))
        self.assertEqual('[CONFIGURATION.showSecondHand] == "TRUE"', group.find("Condition/Expressions/Expression").text)
        hands = group.findall('.//AnalogClock/SecondHand')
        self.assertEqual(2, len(hands))
        self.assertEqual('[CONFIGURATION.secondColor.0]', hands[-1].get('tintColor'))
        self.assertTrue(all(hand.find('Sweep') is not None for hand in hands))
        self.assertTrue(all(clock.find("Variant[@mode='AMBIENT']").get('value') == '0' for clock in group.findall('.//AnalogClock') if clock.find('SecondHand') is not None))


if __name__ == "__main__":
    unittest.main()
