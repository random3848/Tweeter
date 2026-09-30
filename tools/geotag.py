#!/usr/bin/env python3
"""Make test photos with GPS in their EXIF metadata. Standard library only.

Examples:
    # Tag one photo:
    python3 tools/geotag.py in.jpg out.jpg --lat 34.16165 --lng -119.04310 --error 4

    # Regenerate the sample photos in tools/test-photos/:
    python3 tools/geotag.py --samples

Then copy a photo to the emulator's gallery:
    adb push out.jpg /sdcard/Pictures/
    adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d file:///sdcard/Pictures/out.jpg

Any existing EXIF block in the input is replaced.
"""
import argparse
import struct
from pathlib import Path

PHOTO_DIR = Path(__file__).parent / "test-photos"

# Rough campus points picked off the map, not surveyed.
SAMPLES = [
    ("geo_belltower.jpg", 34.16165, -119.04310, 4, "2026:09:30 10:15:00"),
    ("geo_southquad.jpg", 34.16030, -119.04290, 8, "2026:09:30 10:20:00"),
    ("geo_studentunion.jpg", 34.16130, -119.04470, 12, "2026:09:30 10:25:00"),
]

# TIFF field types.
BYTE, ASCII, LONG, RATIONAL, UNDEFINED = 1, 2, 4, 5, 7


def rational(value, denominator=1_000_000):
    return struct.pack("<II", int(round(value * denominator)), denominator)


def degrees_minutes_seconds(degrees):
    degrees = abs(degrees)
    whole = int(degrees)
    minutes = int((degrees - whole) * 60)
    seconds = (degrees - whole - minutes / 60) * 3600
    return rational(whole, 1) + rational(minutes, 1) + rational(seconds, 10_000)


def build_ifd(entries, offset):
    """Encode an IFD placed at `offset`. entries: (tag, type, count, value bytes)."""
    entries = sorted(entries)
    data_offset = offset + 2 + 12 * len(entries) + 4
    table, data = struct.pack("<H", len(entries)), b""
    for tag, field_type, count, value in entries:
        if len(value) <= 4:
            table += struct.pack("<HHI", tag, field_type, count) + value.ljust(4, b"\0")
        else:
            table += struct.pack("<HHII", tag, field_type, count, data_offset + len(data))
            data += value + (b"\0" if len(value) % 2 else b"")
    return table + struct.pack("<I", 0) + data


def exif_segment(lat, lng, error_m, taken):
    placeholder = b"\0" * 4
    ifd0_offset = 8
    ifd0_size = len(build_ifd([(0x8769, LONG, 1, placeholder),
                               (0x8825, LONG, 1, placeholder)], ifd0_offset))
    exif_offset = ifd0_offset + ifd0_size
    taken_bytes = taken.encode() + b"\0"
    exif_ifd = build_ifd([(0x9003, ASCII, len(taken_bytes), taken_bytes)], exif_offset)
    gps_offset = exif_offset + len(exif_ifd)
    gps_ifd = build_ifd([
        (0x0000, BYTE, 4, bytes([2, 3, 0, 0])),                          # GPSVersionID
        (0x0001, ASCII, 2, b"N\0" if lat >= 0 else b"S\0"),              # GPSLatitudeRef
        (0x0002, RATIONAL, 3, degrees_minutes_seconds(lat)),             # GPSLatitude
        (0x0003, ASCII, 2, b"E\0" if lng >= 0 else b"W\0"),              # GPSLongitudeRef
        (0x0004, RATIONAL, 3, degrees_minutes_seconds(lng)),             # GPSLongitude
        (0x001B, UNDEFINED, 3, b"GPS"),                                  # GPSProcessingMethod
        (0x001F, RATIONAL, 1, rational(error_m)),                        # GPSHPositioningError
    ], gps_offset)
    ifd0 = build_ifd([(0x8769, LONG, 1, struct.pack("<I", exif_offset)),  # ExifIFD pointer
                      (0x8825, LONG, 1, struct.pack("<I", gps_offset))],  # GPSInfo pointer
                     ifd0_offset)
    tiff = b"II*\0" + struct.pack("<I", 8) + ifd0 + exif_ifd + gps_ifd
    payload = b"Exif\0\0" + tiff
    return b"\xff\xe1" + struct.pack(">H", len(payload) + 2) + payload


def geotag(src, dst, lat, lng, error_m, taken):
    data = Path(src).read_bytes()
    if data[:2] != b"\xff\xd8":
        raise ValueError(f"{src} is not a JPEG")
    segment = exif_segment(lat, lng, error_m, taken)
    out, i, inserted = [b"\xff\xd8"], 2, False
    # Walk the header segments up to start-of-scan, dropping any old EXIF (APP1).
    while data[i] == 0xFF and data[i + 1] != 0xDA:
        marker = data[i + 1]
        length = struct.unpack(">H", data[i + 2:i + 4])[0]
        if marker != 0xE1:
            out.append(data[i:i + 2 + length])
        if marker == 0xE0 and not inserted:   # EXIF goes right after JFIF (APP0)
            out.append(segment)
            inserted = True
        i += 2 + length
    if not inserted:
        out.insert(1, segment)
    out.append(data[i:])
    Path(dst).write_bytes(b"".join(out))


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("src", nargs="?", help="input JPEG")
    parser.add_argument("dst", nargs="?", help="output JPEG")
    parser.add_argument("--lat", type=float)
    parser.add_argument("--lng", type=float)
    parser.add_argument("--error", type=float, default=5, help="GPS error radius in meters")
    parser.add_argument("--taken", default="2026:09:30 12:00:00",
                        help='capture time, "YYYY:MM:DD HH:MM:SS"')
    parser.add_argument("--samples", action="store_true",
                        help="regenerate the sample photos in tools/test-photos/")
    args = parser.parse_args()

    if args.samples:
        for name, lat, lng, error_m, taken in SAMPLES:
            geotag(PHOTO_DIR / "untagged.jpg", PHOTO_DIR / name, lat, lng, error_m, taken)
            print(f"wrote {PHOTO_DIR / name}")
        return
    if not (args.src and args.dst and args.lat is not None and args.lng is not None):
        parser.error("give src, dst, --lat and --lng, or use --samples")
    geotag(args.src, args.dst, args.lat, args.lng, args.error, args.taken)
    print(f"wrote {args.dst}")


if __name__ == "__main__":
    main()
