#!/usr/bin/env python3
"""Compare the exact RGBA captures produced by Android's PNG encoder."""

import argparse
import json
from pathlib import Path
import struct
import zlib

PNG = b'\x89PNG\r\n\x1a\n'
PROFILES = ('SMALL_ROUND-1.0', 'SMALL_ROUND-1.24', 'XL_ROUND-1.0', 'XL_ROUND-1.24')


def decode(path):
    data = path.read_bytes()
    if not data.startswith(PNG):
        raise ValueError(f'{path.name} is not a PNG')
    width, height, depth, color, compression, filtering, interlace = struct.unpack('!2I5B', data[16:29])
    if (depth, color, compression, filtering, interlace) != (8, 6, 0, 0, 0):
        raise ValueError(f'{path.name} must be non-interlaced 8-bit RGBA')
    packed = []
    offset = len(PNG)
    while offset < len(data):
        length, kind = struct.unpack_from('!I4s', data, offset)
        body = data[offset + 8:offset + 8 + length]
        if kind == b'IDAT':
            packed.append(body)
        offset += length + 12
    rows = zlib.decompress(b''.join(packed))
    stride = width * 4
    if len(rows) != height * (stride + 1):
        raise ValueError(f'{path.name} has incomplete pixels')
    prior = bytearray(stride)
    pixels = []
    for row in range(height):
        index = row * (stride + 1)
        mode = rows[index]
        current = bytearray(rows[index + 1:index + 1 + stride])
        for column, value in enumerate(current):
            left = current[column - 4] if column >= 4 else 0
            up = prior[column]
            corner = prior[column - 4] if column >= 4 else 0
            paeth = left + up - corner
            prediction = min((left, up, corner), key=lambda candidate: abs(paeth - candidate))
            current[column] = (value + (0, left, up, (left + up) // 2, prediction)[mode]) & 255
        pixels.extend(tuple(current[x:x + 4]) for x in range(0, stride, 4))
        prior = current
    return width, height, pixels


def chunk(kind, data):
    return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data))


def write_mask(path, width, height, mask):
    header = struct.pack('!2I5B', width, height, 8, 0, 0, 0, 0)
    rows = b''.join(b'\0' + mask[y * width:(y + 1) * width] for y in range(height))
    path.write_bytes(PNG + chunk(b'IHDR', header) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


def compare(directory, output):
    output.mkdir(parents=True, exist_ok=True)
    result = {}
    for profile in PROFILES:
        stem = profile + '-disabled'
        before, held = (directory / f'{stem}-{kind}.png' for kind in ('before', 'held'))
        if not before.is_file() or not held.is_file():
            result[profile] = {'available': False, 'before': before.is_file(), 'held': held.is_file()}
            continue
        width, height, first = decode(before)
        width2, height2, second = decode(held)
        if (width, height) != (width2, height2):
            result[profile] = {'available': True, 'size_mismatch': [[width, height], [width2, height2]]}
            continue
        pairs = [(i, a, b) for i, (a, b) in enumerate(zip(first, second)) if a != b]
        mask = bytearray(width * height)
        for i, _, _ in pairs:
            mask[i] = 255
        write_mask(output / f'{stem}-mask.png', width, height, mask)
        x = [i % width for i, _, _ in pairs]
        y = [i // width for i, _, _ in pairs]
        result[profile] = {
            'available': True, 'width': width, 'height': height, 'changed_pixels': len(pairs),
            'bounds_inclusive': [min(x), min(y), max(x), max(y)] if pairs else None,
            'unchanged_alpha_pixels': sum(a[3] == b[3] for _, a, b in pairs),
            'nondecreasing_rgb_pixels': sum(all(p <= q for p, q in zip(a[:3], b[:3])) for _, a, b in pairs),
        }
    (output / 'pixel-counts.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('captures', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    compare(args.captures, args.output)
