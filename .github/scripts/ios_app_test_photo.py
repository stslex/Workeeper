"""Write the two-color picker fixture for an owned simulator."""
import struct
import sys
import zlib
from pathlib import Path


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def write_photo(path):
    width, height = 1600, 1200
    scanline = b"\0" + b"\xff\0\0" * (width // 2) + b"\0\0\xff" * (width // 2)
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                     + chunk(b"IDAT", zlib.compress(scanline * height)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    write_photo(Path(sys.argv[1]))
