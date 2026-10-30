"""Wire canonical marker artwork and a native, opacity-masked seconds hand into Vigil."""
from pathlib import Path
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parents[2]
PATH = ROOT / "watchfaces/vigil/src/main/res/raw/watchface.xml"


def node(parent, tag, **attributes):
    return E.SubElement(parent, tag, {key: str(value) for key, value in attributes.items()})


def visible(parent):
    condition = node(parent, "Condition")
    node(node(condition, "Expressions"), "Expression", name="handsVisible").text = '[CONFIGURATION.showHands] == "TRUE"'
    return node(condition, "Compare", expression="handsVisible")


def configure():
    tree = E.parse(PATH)
    root = tree.getroot()
    root.find("Metadata[@key='PREVIEW_TIME']").set("value", "10:08:32")
    configs = root.find("UserConfigurations")
    configs.find("BooleanConfiguration[@id='showHands']").set("defaultValue", "FALSE")
    color = configs.find("ColorConfiguration[@id='secondColor']")
    if color is not None:
        configs.remove(color)
    color = node(configs, "ColorConfiguration", id="secondColor", displayName="seconds_color", defaultValue="red")
    node(color, "ColorOption", id="red", displayName="seconds_red", colors="#FFFF3B30")
    node(color, "ColorOption", id="gray", displayName="seconds_gray", colors="#FF9E9E9E")

    scene = root.find("Scene")
    # Marker variants and grayscale options are maintained in the canonical WFF document.

    # Full-face rendering belongs to the existing full-face hands slot. BoundingOval
    # also clips rendering on the real device; it cannot host a full seconds hand.
    slot = scene.find("ComplicationSlot[@slotId='7']")
    complication = slot.find("Complication[@type='SMALL_IMAGE']")
    complication.clear()
    complication.set("type", "SMALL_IMAGE")
    owner = node(complication, "Group", name="vigil-hands-visibility", x=0, y=0, width=450, height=450)
    group = node(visible(owner), "Group", name="vigil-native-hands", x=0, y=0, width=450, height=450)
    node(group, "Variant", mode="AMBIENT", target="alpha", value=160)
    node(node(group, "PartImage", renderMode="MASK", x=0, y=0, width=450, height=450), "Image", resource="[COMPLICATION.SMALL_IMAGE]")
    for shadow in (True, False):
        clock = node(group, "AnalogClock", x=0, y=0, width=450, height=450)
        if shadow:
            node(clock, "Variant", mode="AMBIENT", target="alpha", value=0)
        for kind in ("Hour", "Minute"):
            hand = node(clock, kind + "Hand", resource=kind.lower() + "_hand_transparent", x=2 if shadow else 0, y=3 if shadow else 0,
                        width=450, height=450, pivotX="0.5", pivotY="0.5")
            if shadow:
                hand.set("tintColor", "#A0000000")
    hub = node(group, "PartDraw", x=0, y=0, width=450, height=450)
    for radius, color in ((8.5 * 450 / 512, "#FFFFFFFF"), (5 * 450 / 512, "#FF000000")):
        node(node(hub, "Ellipse", x=225-radius, y=225-radius, width=radius*2, height=radius*2), "Fill", color=color)
    for shadow in (True, False):
        clock = node(group, "AnalogClock", x=0, y=0, width=450, height=450)
        node(clock, "Variant", mode="AMBIENT", target="alpha", value=0)
        hand = node(clock, "SecondHand", resource="second_hand_transparent", x=2 if shadow else 0, y=3 if shadow else 0,
                    width=450, height=450, pivotX="0.5", pivotY="0.5",
                    tintColor="#A0000000" if shadow else "[CONFIGURATION.secondColor.0]")
        node(hand, "Sweep", frequency=15)
    slot = scene.find("ComplicationSlot[@slotId='8']")
    slot.attrib.update(x="200", y="200", width="50", height="50")
    slot.find("BoundingOval").attrib.update(x="0", y="0", width="50", height="50")
    complication = slot.find("Complication[@type='SMALL_IMAGE']")
    complication.clear()
    complication.set("type", "SMALL_IMAGE")
    owner = node(complication, "Group", name="vigil-center-visibility", x=0, y=0, width=50, height=50)
    node(node(visible(owner), "PartImage", x=0, y=0, width=50, height=50, alpha=0), "Image", resource="[COMPLICATION.SMALL_IMAGE]")
    E.indent(tree, space="    ")
    tree.write(PATH, encoding="utf-8", xml_declaration=True)


if __name__ == '__main__':
    configure()
