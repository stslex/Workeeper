#!/usr/bin/env python3
"""Store screenshot gate: every file in a supply screenshot directory is one Play accepts.

Spec: documentation/feature-specs/wear-release-pipeline.md §7.4. Run over one directory:

    python3 .github/scripts/assert_store_screenshots.py \
        fastlane/metadata-wear/android/en-US/images/wearScreenshots
    python3 .github/scripts/assert_store_screenshots.py --self-test

Per file: a PNG (signature and IHDR CRC), colour type 2 (RGB, no alpha channel), no tRNS chunk (no
transparency by other means), square, at least 384 px. The directory: at least one file, every file
named N_<language>.png with N = 1..count, where <language> is the metadata language directory, so
supply's lexical upload order is the numbering. Reads chunk headers only; stdlib. Exit 0: pass.
Exit 1: a rule failed, including zero files.
"""

import argparse
import contextlib
import io
import struct
import sys
import tempfile
import zlib
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
COLOR_TYPE_RGB = 2
COLOR_TYPES = {0: "grey", 2: "RGB", 3: "palette", 4: "grey+alpha", 6: "RGBA"}
MIN_SIDE = 384


def read_png_header(path):
    """Return (width, height, bit_depth, color_type, chunk_names_before_IDAT). Raises ValueError."""
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG (bad signature)")
    offset, names, header = len(PNG_SIGNATURE), [], None
    while offset + 8 <= len(data):
        length, name = struct.unpack(">I4s", data[offset:offset + 8])
        body = data[offset + 8:offset + 8 + length]
        crc = data[offset + 8 + length:offset + 12 + length]
        if len(body) != length or len(crc) != 4:
            raise ValueError(f"truncated {name!r} chunk")
        if struct.unpack(">I", crc)[0] != zlib.crc32(name + body):
            raise ValueError(f"bad CRC in {name.decode('latin-1')} chunk")
        if not names and name != b"IHDR":
            raise ValueError("first chunk is not IHDR")
        if name == b"IHDR":
            if length != 13:
                raise ValueError("IHDR length is not 13")
            header = struct.unpack(">IIBBBBB", body)
        if name == b"IDAT":
            break
        names.append(name.decode("latin-1"))
        offset += 12 + length
    if header is None:
        raise ValueError("no IHDR chunk")
    width, height, bit_depth, color_type = header[:4]
    return width, height, bit_depth, color_type, names


def check_file(path, index, language):
    """Return a list of failures for one file (empty when it passes) and a description."""
    failures = []
    if path.name != f"{index}_{language}.png":
        failures.append(f"name {path.name!r}, expected {index}_{language}.png")
    try:
        width, height, bit_depth, color_type, chunks = read_png_header(path)
    except ValueError as error:
        return failures + [str(error)], "unreadable"
    kind = COLOR_TYPES.get(color_type, f"type {color_type}")
    description = f"{width}x{height} {kind} (colour type {color_type}) {bit_depth}-bit"
    if color_type != COLOR_TYPE_RGB:
        failures.append(f"colour type {color_type} ({kind}), expected {COLOR_TYPE_RGB} (RGB without alpha)")
    if "tRNS" in chunks:
        failures.append("tRNS chunk present (transparency)")
    if width != height:
        failures.append(f"not square: {width}x{height}")
    if min(width, height) < MIN_SIDE:
        failures.append(f"smaller than {MIN_SIDE} px: {width}x{height}")
    return failures, description


def check_directory(directory):
    """Print one line per file and a summary. Returns the exit code."""
    directory = Path(directory)
    if not directory.is_dir():
        print(f"FAIL {directory}: not a directory")
        return 1
    language = directory.parent.parent.name
    files = sorted(entry for entry in directory.iterdir() if not entry.name.startswith("."))
    print(f"{directory}: {len(files)} files, language {language!r}")
    if not files:
        print("RESULT FAIL: zero screenshots")
        return 1

    # supply uploads in lexical order (Dir.glob(...).sort), so position N must hold N_<language>.png.
    failed = 0
    for index, path in enumerate(files, start=1):
        failures, description = check_file(path, index, language)
        print(f"  {'PASS' if not failures else 'FAIL'} {path.name}: {description}" +
              ("" if not failures else " -- " + "; ".join(failures)))
        failed += bool(failures)
    print(f"RESULT {'PASS' if not failed else 'FAIL'} ({len(files) - failed}/{len(files)} files pass)")
    return 1 if failed else 0


def png(width, height, color_type=COLOR_TYPE_RGB, extra_chunks=(), corrupt_crc=False):
    """A minimal valid PNG: one black image of the given shape."""
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]

    def chunk(name, body):
        crc = zlib.crc32(name + body) ^ (1 if corrupt_crc and name == b"IHDR" else 0)
        return struct.pack(">I", len(body)) + name + body + struct.pack(">I", crc)

    raw = b"".join(b"\x00" + bytes(width * channels) for _ in range(height))
    return (PNG_SIGNATURE
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0))
            + b"".join(chunk(name, body) for name, body in extra_chunks)
            + chunk(b"IDAT", zlib.compress(raw))
            + chunk(b"IEND", b""))


SELF_TEST_CASES = [
    # (name, {file name: png bytes or raw bytes}, expected exit code, text the output must contain)
    ("two RGB 480 and one RGB 384", {"1_en-US.png": png(480, 480), "2_en-US.png": png(480, 480),
                                     "3_en-US.png": png(384, 384)}, 0, "RESULT PASS (3/3"),
    ("383x383 (M-B5)", {"1_en-US.png": png(383, 383)}, 1, "smaller than 384 px: 383x383"),
    ("non-square 480x400 (M-B6)", {"1_en-US.png": png(480, 400)}, 1, "not square: 480x400"),
    ("colour type 6, RGBA (M-B7)", {"1_en-US.png": png(480, 480, color_type=6)}, 1, "colour type 6 (RGBA)"),
    ("empty directory (M-B8)", {}, 1, "RESULT FAIL: zero screenshots"),
    ("RGB with a tRNS chunk", {"1_en-US.png": png(480, 480, extra_chunks=[(b"tRNS", b"\x00\x00" * 3)])}, 1,
     "tRNS chunk present"),
    ("palette colour type 3", {"1_en-US.png": png(480, 480, color_type=3,
                                                  extra_chunks=[(b"PLTE", b"\x00\x00\x00")])}, 1,
     "colour type 3 (palette)"),
    ("corrupted IHDR CRC", {"1_en-US.png": png(480, 480, corrupt_crc=True)}, 1, "bad CRC in IHDR"),
    ("JPEG bytes named .png", {"1_en-US.png": b"\xff\xd8\xff\xe0" + bytes(64)}, 1, "bad signature"),
    ("numbering gap", {"1_en-US.png": png(480, 480), "3_en-US.png": png(480, 480)}, 1,
     "name '3_en-US.png', expected 2_en-US.png"),
    ("wrong language suffix", {"1_ru-RU.png": png(480, 480)}, 1, "expected 1_en-US.png"),
    ("ten files sort 1, 10, 2, ...", {f"{n}_en-US.png": png(384, 384) for n in range(1, 11)}, 1,
     "name '10_en-US.png', expected 1_en-US.png"),
]


def self_test():
    print(f"self-test: {len(SELF_TEST_CASES)} cases")
    mismatches = []
    for name, files, expected, evidence in SELF_TEST_CASES:
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp) / "en-US" / "images" / "wearScreenshots"
            directory.mkdir(parents=True)
            for file_name, content in files.items():
                (directory / file_name).write_bytes(content)
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                actual = check_directory(directory)
        matched = actual == expected and evidence in output.getvalue()
        print(f"  {'ok' if matched else 'MISMATCH':8} {name}: exit {actual}, expected {expected} with {evidence!r}")
        if not matched:
            print(output.getvalue(), end="")
            mismatches.append(name)
    expectations = {expected for _, _, expected, _ in SELF_TEST_CASES}
    if expectations != {0, 1}:
        print("self-test FAIL: the cases do not show both PASS and FAIL")
        return 1
    if mismatches:
        print(f"self-test FAIL: {len(mismatches)}/{len(SELF_TEST_CASES)} mismatched: {mismatches}")
        return 1
    print(f"self-test PASS: {len(SELF_TEST_CASES)} cases")
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("directory", nargs="?")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.directory:
        parser.error("a screenshot directory is required unless --self-test")
    return check_directory(args.directory)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
