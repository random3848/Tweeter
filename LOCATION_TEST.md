# Photo location test

This branch is a test app, not the real Tweeter app. It answers one question for later
implementation: **where should a wildlife photo's map pin come from?** It compares:

- **EXIF**: GPS the camera wrote into the photo's metadata (blue dot).
- **Device**: a live fused-location fix taken when the photo reaches the app (green dot, with its
  accuracy circle).
- **Manual**: a pin the user drops by tapping the map (red dot). When testing, drop it where you
  actually stood and treat it as ground truth.

The map is MapLibre Native with OpenStreetMap raster tiles, centered on CSUCI.

## Running it

Open the repo in Android Studio, or run `./gradlew installDebug` with an emulator or phone
attached. Grant location access when asked.

| Button | How the photo arrives |
|---|---|
| Camera | `ACTION_IMAGE_CAPTURE` into a file the app owns (`TakePicture`) |
| Picker | Android Photo Picker (`PickVisualMedia`) |
| Files | Storage Access Framework (`OpenDocument`), also asking for the unredacted original |

**Save** appends the test to a CSV:

```
adb pull /sdcard/Android/data/edu.csuci.tweeter/files/location_tests.csv
```

On the emulator, set a fake position with `adb emu geo fix <lng> <lat>` or the Location panel in
the emulator's extended controls.

## Test photos

`tools/test-photos/` has three photos with GPS tagged at rough campus points (not surveyed)
and one with no GPS:

| File | EXIF location | EXIF error |
|---|---|---|
| `geo_belltower.jpg` | 34.16165, -119.04310 | 4 m |
| `geo_southquad.jpg` | 34.16030, -119.04290 | 8 m |
| `geo_studentunion.jpg` | 34.16130, -119.04470 | 12 m |
| `untagged.jpg` | none | |

The photos look the same; tell them apart by name. To put them in the emulator's gallery:

```
for f in tools/test-photos/*.jpg; do
  adb push "$f" /sdcard/Pictures/
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
      -d "file:///sdcard/Pictures/$(basename "$f")"
done
```

Dragging a photo from Finder onto the emulator window also works (it lands in Downloads).
`tools/geotag.py` tags any JPEG with a location of your choice; run it with `--help`.

## Findings so far (emulator, API 37)

| Path | EXIF GPS reaches the app? | Notes |
|---|---|---|
| Files | Yes | Read `geo_belltower.jpg` at 34.161650, -119.043100 ±4 m. |
| Picker | No | Same photo shows "No GPS tags in EXIF (location may have been redacted)". The Photo Picker strips location for privacy and apps can't opt out. |
| Camera | No EXIF GPS | The emulator camera never writes GPS. Many phone camera apps also skip it for intent captures. The live device fix (±5 m on the emulator) covers this case. |

What this means for the real app:
- To import photos with their location, use the Files (`OpenDocument`) path, not the Photo
  Picker.
- For photos taken in the app, use the live device fix taken at capture time.
- Always let the user drop or move the pin manually.

Still to test: real phones on campus (Android and iPhone photos), and how far EXIF and device
fixes land from a hand-placed pin.

In the app, "opened: plain URI" means the photo was read as handed to the app rather than as the
unredacted original. That is expected for Camera photos, which the app already owns.
