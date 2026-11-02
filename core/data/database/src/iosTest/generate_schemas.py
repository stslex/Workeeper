#!/usr/bin/env python3
"""Embed the tracked exports, byte-for-byte, for Native MigrationTestHelper inputs."""

import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", required=True, type=Path)
args = parser.parse_args()
module = Path(__file__).resolve().parents[2]
exports = []
blobs = []
identities = []
for version in (5, 6):
    path = module / "schemas/io.github.stslex.workeeper.core.data.database.AppDatabase" / f"{version}.json"
    raw = path.read_bytes()
    text = raw.decode("utf-8")
    assert '"""' not in text
    literal = text.replace("$", "${'$'}")
    exports.append(f'        {version} to """{literal}""",')
    blob = hashlib.sha1(f"blob {len(raw)}\0".encode() + raw).hexdigest()
    blobs.append(f'        {version} to "{blob}",')
    identity = json.loads(raw)["database"]["identityHash"]
    identities.append(f'        {version} to "{identity}",')
    print(f"Native schema fixture v{version}: blob={blob} identity={identity}")
output = "\n".join([
    "// Generated from tracked schemas by src/iosTest/generate_schemas.py.",
    "package io.github.stslex.workeeper.core.data.database",
    "", "internal object IosHistoricalSchemas {",
    "    val exports = mapOf(", *exports, "    )",
    "    val blobs = mapOf(", *blobs, "    )",
    "    val identities = mapOf(", *identities, "    )", "}", "",
])
args.output.mkdir(parents=True, exist_ok=True)
(args.output / "IosHistoricalSchemas.kt").write_text(output, encoding="utf-8")
