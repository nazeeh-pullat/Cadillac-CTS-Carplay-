"""Convert the downloaded, monochrome Cadillac SVG paths to native Android vectors.

Assets belong to Cadillac. Source URLs and changes are documented in SOURCES.md.
No rasterization or extra runtime library is required.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
import re

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT.parent / "src/main/res/drawable"
OUTPUT.mkdir(parents=True, exist_ok=True)
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)

def android_path(data):
    # Android 5's parser can crash on SVG's valid shorthand decimals such as -.09.
    pattern = r"[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?"
    values = []
    position = 0
    for match in re.finditer(pattern, data):
        assert not data[position:match.start()].strip(" ,\n\r\t"), "Unsupported SVG path syntax"
        value = match.group()
        if value.startswith("-."): value = "-0" + value[1:]
        elif value.startswith("+."): value = "+0" + value[1:]
        elif value.startswith("."): value = "0" + value
        values.append(value)
        position = match.end()
    assert not data[position:].strip(" ,\n\r\t")
    return " ".join(values)

for name, width, height in (("crest", 101, 38.05), ("wordmark", 267, 70.47)):
    source = ET.parse(ROOT / f"cadillac-{name}.svg").getroot()
    vector = ET.Element("vector", {
        f"{{{ANDROID}}}width": f"{width}dp",
        f"{{{ANDROID}}}height": f"{height}dp",
        f"{{{ANDROID}}}viewportWidth": str(width),
        f"{{{ANDROID}}}viewportHeight": str(height),
    })
    vector.append(ET.Comment(" Cadillac artwork; see legacy/design-assets/SOURCES.md. "
                             "Converted to Android paths; silver fill; crest whitespace trimmed. "))
    for element in source.iter():
        if element.tag.split("}")[-1] == "path":
            ET.SubElement(vector, "path", {
                f"{{{ANDROID}}}fillColor": "#E9E7E2",
                f"{{{ANDROID}}}pathData": android_path(element.attrib["d"]),
            })
    ET.indent(vector)
    ET.ElementTree(vector).write(OUTPUT / f"cadillac_{name}.xml", encoding="utf-8", xml_declaration=True)

crest = ET.parse(OUTPUT / "cadillac_crest.xml").getroot()
icon = ET.Element("vector", {
    f"{{{ANDROID}}}width": "72dp", f"{{{ANDROID}}}height": "72dp",
    f"{{{ANDROID}}}viewportWidth": "108", f"{{{ANDROID}}}viewportHeight": "108",
})
ET.SubElement(icon, "path", {f"{{{ANDROID}}}fillColor": "#080A0C", f"{{{ANDROID}}}pathData": "M0,0 H108 V108 H0 Z"})
group = ET.SubElement(icon, "group", {f"{{{ANDROID}}}translateX": "9", f"{{{ANDROID}}}translateY": "37", f"{{{ANDROID}}}scaleX": "0.89", f"{{{ANDROID}}}scaleY": "0.89"})
for path in crest.findall("path"):
    ET.SubElement(group, "path", path.attrib)
ET.indent(icon)
ET.ElementTree(icon).write(OUTPUT / "ic_launcher_cadillac.xml", encoding="utf-8", xml_declaration=True)
