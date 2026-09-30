#!/usr/bin/env python3
"""Render validated acceptance evidence as Markdown, an HTML gallery and summary JUnit XML."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import html
import json
from pathlib import Path
import re
import tempfile
from urllib.parse import quote
import xml.etree.ElementTree as ET


SCOPE = (
    "Presentation summary of canonical acceptance receipts. This is not raw instrumented "
    "or Gradle JUnit evidence. Original test output, screenshots and receipts remain authoritative. "
    "Emulator observations do not establish physical-watch acceptance or real phone transport."
)


def text(value: object) -> str:
    return "".join(
        char if char in "\t\n\r" or 0x20 <= ord(char) <= 0xD7FF
        or 0xE000 <= ord(char) <= 0xFFFD or 0x10000 <= ord(char) <= 0x10FFFF else "\ufffd"
        for char in str(value)
    )


def markdown(value: object) -> str:
    value = html.escape(" ".join(text(value).splitlines()), quote=True)
    return re.sub(r"([\\`*_{}\[\]()#+.!|>~-])", r"\\\1", value)


def escaped(value: object) -> str:
    return html.escape(text(value), quote=True)


def relative_link(cohort: Path, target: Path) -> str:
    if not target.resolve().is_relative_to(cohort):
        raise ValueError(f"Artifact leaves the cohort: {target.name}")
    return quote(target.relative_to(cohort).as_posix(), safe="/")


def link(cohort: Path, target: Path, label: object, *, html_format: bool = False) -> str:
    url = relative_link(cohort, target)
    if html_format:
        return f'<a href="{escaped(url)}">{escaped(label)}</a>'
    return f"[{markdown(label)}]({url})"


def read_json(path: Path) -> dict:
    # The canonical validator runs before any evidence is read for presentation.
    return json.loads(path.read_text())


def duration(receipt: dict) -> str | None:
    try:
        start = dt.datetime.fromisoformat(receipt["started_at"])
        finish = dt.datetime.fromisoformat(receipt["finished_at"])
        seconds = (finish - start).total_seconds()
        return f"{seconds:.3f}" if seconds >= 0 else None
    except (KeyError, ValueError, TypeError):
        return None


def source_properties(plan: dict) -> dict[str, str]:
    source = plan["source"]
    inventory = json.dumps(source["files"], sort_keys=True, separators=(",", ":")).encode()
    return {
        "Cohort": str(plan["cohort"]),
        "Source HEAD": str(source["head"]),
        "Source branch": str(source["branch"]),
        "Source inventory paths": str(len(source["files"])),
        "Source inventory SHA-256 (compact sorted JSON)": hashlib.sha256(inventory).hexdigest(),
        "Application package": str(plan["package"]),
        "Application APK SHA-256": str(plan["apk"]["sha256"]),
        "Instrumentation package": str(plan["test_package"]),
        "Instrumentation APK SHA-256": str(plan["test_apk"]["sha256"]),
    }


def invocation_details(receipt: dict) -> list[dict]:
    detail = receipt.get("detail", {})
    invocations = detail.get("invocations", [])
    if not invocations and isinstance(detail.get("receipt"), dict):
        invocations = [{"selection": detail.get("fixture", "instrumentation"),
                        "status": receipt["status"], "detail": detail}]
    rows = []
    for invocation in invocations:
        payload = invocation.get("detail", {})
        captured = payload.get("receipt", {})
        selection = str(invocation.get("selection", "unknown"))
        fixture = payload.get("fixture", captured.get("fixture"))
        if fixture is None and selection.startswith("fixture:"):
            fixture = selection.removeprefix("fixture:")
        rows.append({
            "selection": selection, "status": invocation.get("status", "BLOCKED"),
            "fixture": fixture or "not retained",
            "classification": captured.get("sourceClassification", "not retained; inspect raw receipts"),
            "reason": invocation.get("reason", ""),
            "artifact_directory": payload.get("artifacts"),
            "started_at": invocation.get("started_at"), "finished_at": invocation.get("finished_at"),
        })
    return rows


def cases(cohort: Path, plan: dict, summary: dict) -> list[dict]:
    rows = []
    for index, (case_id, expected) in enumerate(plan["expected"].items(), 1):
        directory = cohort / "cases" / case_id
        receipt_path = directory / "receipt.json"
        recorded = summary["cases"].get(case_id)
        receipt = read_json(receipt_path) if recorded else {}
        artifacts = [directory / relative for relative in sorted(receipt.get("artifacts", {}))]
        for artifact in artifacts:
            relative_link(cohort, artifact)
        rows.append({
            "id": case_id, "anchor": f"case-{index}", "kind": expected["kind"],
            "status": recorded["status"] if recorded else "BLOCKED",
            "reason": recorded["reason"] if recorded else "Missing required case receipt; observation not recorded.",
            "missing": recorded is None, "directory": directory, "receipt_path": receipt_path,
            "receipt": receipt, "artifacts": artifacts, "invocations": invocation_details(receipt),
            "screenshots": [path for path in artifacts if path.suffix.lower() == ".png"],
            "captured_receipts": [path for path in artifacts if path.name == "receipt.json"],
        })
    return rows


def evidence_links(cohort: Path, row: dict, *, html_format: bool = False) -> list[str]:
    paths = ([] if row["missing"] else [row["receipt_path"]])
    paths += [path for path in row["artifacts"] if path.parent == row["directory"]
              and (path.name in ("commands.jsonl", "observations.jsonl", "invocations.json")
                   or path.name.startswith("operator-"))]
    return [link(cohort, path, path.name, html_format=html_format) for path in paths]


def invocation_links(cohort: Path, row: dict, invocation: dict, *, html_format: bool = False) -> list[str]:
    paths = []
    if invocation["artifact_directory"]:
        path = row["directory"] / invocation["artifact_directory"] / "receipt.json"
        if path in row["artifacts"]:
            paths.append((path, "instrumented receipt"))
    ledger = row["directory"] / "commands.jsonl"
    if ledger in row["artifacts"]:
        paths.append((ledger, "timestamped commands/errors"))
        start, finish = invocation["started_at"], invocation["finished_at"]
        if start and finish:
            for line in ledger.read_text().splitlines():
                command = json.loads(line)
                if (start <= command.get("started_at", "") <= command.get("finished_at", "") <= finish
                        and any(str(arg).startswith("am instrument ") for arg in command.get("argv", []))):
                    for field in ("stdout", "stderr"):
                        path = row["directory"] / command[field]
                        if path in row["artifacts"] and path.stat().st_size:
                            paths.append((path, "raw instrumentation " + field))
    return [link(cohort, path, label, html_format=html_format) for path, label in paths]


def render_markdown(cohort: Path, summary: dict, plan: dict, rows: list[dict], generated: str) -> str:
    lines = ["# Wear emulator acceptance report", "", f"**Result: {markdown(summary['status'])}**", "",
             markdown(SCOPE), "", f"Generated: {markdown(generated)}", ""]
    counts = {status: sum(row["status"] == status for row in rows) for status in ("PASS", "FAIL", "BLOCKED")}
    lines += [f"Required rows: {len(rows)}; recorded: {summary['recorded']}; missing: {len(summary['missing'])}.",
              f"PASS: {counts['PASS']}; FAIL: {counts['FAIL']}; BLOCKED (including missing): {counts['BLOCKED']}.", "",
              "[HTML screenshot gallery](report.html) · [Summary JUnit XML](report.xml) · "
              "[Canonical JSON](report.json) · [Plan and full source hashes](plan.json)", "", "## Provenance", ""]
    lines.extend(f"- {markdown(key)}: {markdown(value)}" for key, value in source_properties(plan).items())
    lines += ["", "## Required observations", "", "| Case | Status | Reason |", "| --- | --- | --- |"]
    lines.extend(f"| [{markdown(row['id'])}](report.html#{row['anchor']}) | {row['status']} | "
                 f"{markdown(row['reason'])} |" for row in rows)
    for row in rows:
        lines += ["", f"## {markdown(row['id'])}", "", f"**{row['status']}** — {markdown(row['reason'])}", ""]
        lines.append(" · ".join(evidence_links(cohort, row)) or "No raw case evidence has been recorded.")
        for invocation in row["invocations"]:
            lines += ["", f"- {markdown(invocation['selection'])}: {markdown(invocation['status'])}; "
                      f"fixture {markdown(invocation['fixture'])}; {markdown(invocation['classification'])}. "
                      f"{markdown(invocation['reason'])}"]
            lines.append("  " + " · ".join(invocation_links(cohort, row, invocation)))
        for path in row["captured_receipts"]:
            captured = read_json(path)
            description = (f"{captured.get('method', 'unknown method')} / {captured.get('fixture', 'unknown fixture')}: "
                           f"{captured.get('status', 'unknown')} / {captured.get('sourceClassification', 'not retained')}")
            lines.append("- " + link(cohort, path, description))
        for path in row["screenshots"]:
            lines.append("- " + link(cohort, path, path.relative_to(row["directory"])))
    return "\n".join(lines) + "\n"


def render_html(cohort: Path, summary: dict, plan: dict, rows: list[dict], generated: str) -> str:
    parts = ['<!doctype html><html lang="en"><meta charset="utf-8">',
             '<meta name="viewport" content="width=device-width, initial-scale=1">',
             '<title>Wear emulator acceptance report</title><style>',
             'body{font:16px/1.5 system-ui,sans-serif;max-width:1200px;margin:2rem auto;padding:0 1rem;color:#20232a}',
             'a{color:#164f9c}td,th{padding:.5rem;text-align:left;vertical-align:top;border-bottom:1px solid #ddd}',
             'table{border-collapse:collapse;width:100%}code,pre{white-space:pre-wrap;overflow-wrap:anywhere}',
             '.PASS{color:#11632c}.FAIL{color:#a51f20}.BLOCKED{color:#805500}',
             '.gallery{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:1rem}',
             'figure{margin:0;padding:.75rem;border:1px solid #ddd}img{width:100%;height:280px;object-fit:contain}',
             'figcaption,dd{overflow-wrap:anywhere}section{margin-top:2.5rem}dt{font-weight:600}',
             '</style><body><h1>Wear emulator acceptance report</h1>',
             f'<h2 class="{summary["status"]}">{escaped(summary["status"])}</h2>',
             f'<p>{escaped(SCOPE)}</p><p>Generated: {escaped(generated)}</p>',
             f'<p>Required: {len(rows)} · Recorded: {summary["recorded"]} · Missing: {len(summary["missing"])}</p>']
    counts = {status: sum(row["status"] == status for row in rows) for status in ("PASS", "FAIL", "BLOCKED")}
    parts.append('<p>' + ' · '.join(f'{key}: {value}' for key, value in counts.items()) + ' (missing rows are BLOCKED)</p>')
    parts.append('<p><a href="report.md">Markdown</a> · <a href="report.xml">Summary JUnit XML</a> · '
                 '<a href="report.json">Canonical JSON</a> · <a href="plan.json">Plan and full source hashes</a></p>')
    parts.append('<h2>Provenance</h2><dl>')
    parts.extend(f'<dt>{escaped(key)}</dt><dd>{escaped(value)}</dd>' for key, value in source_properties(plan).items())
    parts.append('</dl><h2>Required observations</h2><table><tr><th>Case</th><th>Status</th><th>Reason</th></tr>')
    for row in rows:
        parts.append(f'<tr><td><a href="#{row["anchor"]}">{escaped(row["id"])}</a></td>'
                     f'<td class="{row["status"]}">{row["status"]}</td><td>{escaped(row["reason"])}</td></tr>')
    parts.append('</table>')
    for row in rows:
        parts.append(f'<section id="{row["anchor"]}"><h2>{escaped(row["id"])}</h2>'
                     f'<p class="{row["status"]}"><strong>{row["status"]}</strong>: {escaped(row["reason"])}</p>')
        parts.append('<p>' + (' · '.join(evidence_links(cohort, row, html_format=True)) or 'No raw case evidence recorded.') + '</p>')
        if row["invocations"]:
            parts.append('<table><tr><th>Invocation</th><th>Result</th><th>Fixture/source</th><th>Reason</th></tr>')
            for invocation in row["invocations"]:
                parts.append(f'<tr><td>{escaped(invocation["selection"])}</td><td>{escaped(invocation["status"])}</td>'
                             f'<td>{escaped(invocation["fixture"])}<br>{escaped(invocation["classification"])}</td>'
                             f'<td>{escaped(invocation["reason"])}<br>'
                             + ' · '.join(invocation_links(cohort, row, invocation, html_format=True)) + '</td></tr>')
            parts.append('</table>')
        if row["captured_receipts"]:
            parts.append('<details><summary>Captured instrumentation receipts and classifications</summary><ul>')
            for path in row["captured_receipts"]:
                captured = read_json(path)
                label = (f"{captured.get('method', 'unknown method')} / {captured.get('fixture', 'unknown fixture')}: "
                         f"{captured.get('status', 'unknown')} / {captured.get('sourceClassification', 'not retained')}")
                parts.append('<li>' + link(cohort, path, label, html_format=True) + '</li>')
            parts.append('</ul></details>')
        parts.append('<div class="gallery">')
        for path in row["screenshots"]:
            url = escaped(relative_link(cohort, path))
            caption = escaped(path.relative_to(row["directory"]))
            parts.append(f'<figure><a href="{url}"><img loading="lazy" src="{url}" alt="{caption}"></a>'
                         f'<figcaption>{caption}</figcaption></figure>')
        parts.append('</div></section>')
    parts.append('</body></html>')
    return "\n".join(parts) + "\n"


def render_junit(cohort: Path, plan: dict, rows: list[dict], generated: str) -> bytes:
    failures = sum(row["status"] == "FAIL" for row in rows)
    errors = sum(row["status"] == "BLOCKED" for row in rows)
    root = ET.Element("testsuites", tests=str(len(rows)), failures=str(failures), errors=str(errors), skipped="0")
    suite = ET.SubElement(root, "testsuite", name="Wear emulator acceptance summary", tests=str(len(rows)),
                          failures=str(failures), errors=str(errors), skipped="0", timestamp=generated)
    properties = ET.SubElement(suite, "properties")
    for key, value in {"Evidence scope": SCOPE, **source_properties(plan)}.items():
        ET.SubElement(properties, "property", name=text(key), value=text(value))
    for row in rows:
        attributes = {"classname": "wear.emulator.acceptance.summary." + row["kind"], "name": text(row["id"])}
        elapsed = duration(row["receipt"])
        if elapsed is not None:
            attributes["time"] = elapsed
        case = ET.SubElement(suite, "testcase", attributes)
        if row["status"] != "PASS":
            tag = "failure" if row["status"] == "FAIL" else "error"
            kind = "ObservedAcceptanceFailure" if tag == "failure" else "AcceptanceBlockedOrMissing"
            ET.SubElement(case, tag, type=kind, message=text(row["reason"])).text = text(row["reason"])
        output = [SCOPE, f"Status: {row['status']}", f"Reason: {row['reason']}",
                  f"Human report: report.html#{row['anchor']}"]
        if not row["missing"]:
            output.append("Raw receipt: " + relative_link(cohort, row["receipt_path"]))
        for invocation in row["invocations"]:
            output.append(f"{invocation['selection']}: {invocation['status']}; fixture={invocation['fixture']}; "
                          f"source={invocation['classification']}; {invocation['reason']}")
        ET.SubElement(case, "system-out").text = text("\n".join(output))
    ET.indent(root)
    return ET.tostring(root, encoding="utf-8", xml_declaration=True) + b"\n"


def write_output(path: Path, content: bytes) -> None:
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=path.name + ".", suffix=".tmp", delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(content)
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cohort", required=True, type=Path)
    args = parser.parse_args()
    cohort = args.cohort.resolve()
    repository = Path(__file__).resolve().parents[2]
    if cohort == repository or cohort.is_relative_to(repository):
        parser.error("Report output must remain outside the repository")

    from adb_acceptance import report

    summary = report(cohort)
    plan = read_json(cohort / "plan.json")
    rows = cases(cohort, plan, summary)
    generated = dt.datetime.now(dt.timezone.utc).isoformat()
    outputs = {
        "report.md": render_markdown(cohort, summary, plan, rows, generated).encode(),
        "report.html": render_html(cohort, summary, plan, rows, generated).encode(),
        "report.xml": render_junit(cohort, plan, rows, generated),
    }
    for filename, content in outputs.items():
        write_output(cohort / filename, content)
    print(json.dumps({"status": summary["status"], "rows": len(rows),
                      "outputs": [str(cohort / name) for name in outputs]}, indent=2))
    return {"PASS": 0, "FAIL": 1, "BLOCKED": 2}[summary["status"]]


if __name__ == "__main__":
    raise SystemExit(main())
