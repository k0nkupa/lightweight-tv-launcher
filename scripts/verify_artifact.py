"""Static artifact checks only. Does not run Android, unit tests, or ADB."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
A = "{http://schemas.android.com/apk/res/android}"
manifest = ET.parse(sys.argv[1] if len(sys.argv) > 1 else
                    ROOT / "app/src/main/AndroidManifest.xml").getroot()
assert not manifest.findall("uses-permission"), "Unexpected permission"
app = manifest.find("application")
assert len(app.findall("activity")) == 1
for component in ("service", "receiver", "provider", "activity-alias"):
    assert not app.findall(component), f"Unexpected {component}"
activity = app.find("activity")
assert activity.get(A + "exported") == "true"
categories = [{c.get(A + "name") for c in f.findall("category")}
              for f in activity.findall("intent-filter")]
assert {"android.intent.category.HOME", "android.intent.category.DEFAULT"} in categories
assert {"android.intent.category.LEANBACK_LAUNCHER"} in categories
packages = {p.get(A + "name") for p in manifest.findall("queries/package")}
assert packages == {"com.netflix.ninja", "com.google.android.youtube.tv", "com.plexapp.android"}
assert len(manifest.find("queries")) == 3

layout = ET.parse(ROOT / "app/src/main/res/layout/activity_main.xml").getroot()
buttons = list(layout.iter("ImageButton"))
expected = {"netflix", "youtube", "plex", "hdmi", "settings"}
tiles = {b.get(A + "id").split("/")[1]: b for b in buttons}
assert len(buttons) == 5 and set(tiles) == expected
strings = {s.get("name"): s.text for s in ET.parse(
    ROOT / "app/src/main/res/values/strings.xml").getroot()}
assert [strings[b.get(A + "contentDescription").split("/")[1]] for b in buttons] == [
    "Netflix", "YouTube", "Plex", "HDMI", "Settings"]
for card in list(layout):
    button, label = list(card)
    assert button.get(A + "contentDescription") == label.get(A + "text")
    assert button.get(A + "src") is not None
edges = {}
for name, button in tiles.items():
    edges[name] = {button.get(A + "nextFocus" + direction).split("/")[1]
                   for direction in ("Left", "Right", "Up", "Down")}
    assert edges[name] <= expected
for start in expected:
    reached, pending = set(), [start]
    while pending:
        current = pending.pop()
        if current not in reached:
            reached.add(current)
            pending.extend(edges[current] - reached)
    assert reached == expected, f"Unreachable tile from {start}"

def luminance(rgb):
    values = [int(rgb[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in values]
    return sum(v * w for v, w in zip(linear, (.2126, .7152, .0722)))

colors = {c.get("name"): c.text for c in ET.parse(
    ROOT / "app/src/main/res/values/colors.xml").getroot()}
backdrop = ET.parse(ROOT / "app/src/main/res/drawable/home_background.xml").getroot()
for gradient in backdrop.iter("gradient"):
    for key in ("startColor", "centerColor", "endColor"):
        color = gradient.get(A + key)
        if color:
            rgb = "#" + color[-6:]
            assert (luminance(colors["text"]) + .05) / (luminance(rgb) + .05) >= 4.5
for name in ("tile", "tile_focused", "tile_pressed"):
    contrast = (luminance(colors["text"]) + .05) / (luminance(colors[name]) + .05)
    assert contrast >= 4.5, (name, contrast)
print("PASS: one Activity, no permissions/services/receivers/providers, narrow queries,")
print("HOME + TV app entries, exactly five labels, all directional paths reachable,")
print("and label/backdrop and tile-state contrast >= 4.5:1.")
