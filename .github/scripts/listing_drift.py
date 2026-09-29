#!/usr/bin/env python3
"""Store listing drift guard: stop a deploy before it silently reverts Play Console edits.

Spec: documentation/feature-specs/wear-release-pipeline.md §8. supply overwrites every listing text
and replaces every image type present locally (§3 F10), so a Console edit to anything the repository
also holds is reverted by the next deploy. Three-way rule, per compared item:

- base: the metadata at the latest release-v.* tag reachable from the deployed commit, excluding the
  tag of the version being deployed. A path absent at base is empty.
- local: the metadata at the deployed commit (the working tree must match it for these paths).
- remote: Play, read by fastlane/play_state.rb in a read-only edit.

remote == base → OK (a repository change, or none); remote == local → OK; anything else → DRIFT.
Text is compared after CRLF → LF, trailing whitespace stripped per line, trailing empty lines dropped;
images as ordered sha256 lists. The compared set is exactly what the lane uploads: for phone every
text field, image and screenshot type present locally, per language; for wear its screenshot types.

    python3 .github/scripts/listing_drift.py plan --role phone|wear --plan-out <plan.json>
    python3 .github/scripts/listing_drift.py decide --role phone|wear --plan <plan.json> \
        --remote <remote.json> --out <dir> [--allow-overwrite]
    python3 .github/scripts/listing_drift.py --self-test

decide exits 0 (no drift, or drift under --allow-overwrite, logged as a warning), 1 (DRIFT) or 2
(the check could not run: zero compared items, a reader gap, a dirty tree, a git error). On DRIFT it
writes, under --out, the remote text of every drifted item laid out like the metadata tree,
fetch.json for the drifted images (fastlane downloads them), and drift.json with every verdict.
"""

import argparse
import contextlib
import difflib
import hashlib
import io
import json
import re
import subprocess
import sys
import tempfile
import tomllib
from pathlib import Path, PurePosixPath

REPO_ROOT = Path(__file__).resolve().parents[2]
# supply 2.228.0: AVAILABLE_METADATA_FIELDS, IMAGES_TYPES, SCREENSHOT_TYPES, IMAGE_FILE_EXTENSIONS.
TEXT_FIELDS = ("title", "short_description", "full_description", "video")
IMAGE_TYPES = ("featureGraphic", "icon", "tvBanner")
SCREENSHOT_TYPES = ("phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots", "tvScreenshots",
                    "wearScreenshots")
EXTENSIONS = ("png", "jpg", "jpeg")
ROLES = {
    # role: (metadata root, compares text and single images too)
    "phone": ("fastlane/metadata/android", True),
    "wear": ("fastlane/metadata-wear/android", False),
}
TAG = re.compile(r"release-v\.(\d+)\.(\d+)\.(\d+)")


class DriftError(Exception):
    """The check could not run; exit 2."""


def git(repo, *args, binary=False):
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True)
    if result.returncode != 0:
        raise DriftError(f"git {' '.join(args)}: {result.stderr.decode(errors='replace').strip()}")
    return result.stdout if binary else result.stdout.decode("utf-8")


def normalize_text(text):
    lines = [line.rstrip() for line in (text or "").replace("\r\n", "\n").split("\n")]
    while lines and not lines[-1]:
        lines.pop()
    return "\n".join(lines)


def tree(repo, ref, root):
    """Every file path under root at ref, relative to root."""
    if ref is None:
        return []
    listing = git(repo, "ls-tree", "-r", "--name-only", ref, "--", root)
    return [str(PurePosixPath(path).relative_to(root)) for path in listing.splitlines() if path]


def compared_items(paths, with_text):
    """Mirror supply's uploader: which items it uploads for these metadata paths, and from which files."""
    items = {}
    for language in sorted({path.split("/")[0] for path in paths if "/" in path and not path.startswith(".")}):
        own = [path.split("/", 1)[1] for path in paths if path.startswith(language + "/")]
        if with_text:
            for field in TEXT_FIELDS:
                if f"{field}.txt" in own:
                    items[f"{language}/text/{field}"] = [f"{language}/{field}.txt"]
            for image_type in IMAGE_TYPES:
                # Dir.glob("images/<type>.{png,jpg,jpeg}", FNM_CASEFOLD).last
                found = [f"images/{image_type}.{ext}" for ext in EXTENSIONS
                         for name in own if name.lower() == f"images/{image_type}.{ext}".lower()]
                if found:
                    items[f"{language}/image/{image_type}"] = [f"{language}/{found[-1]}"]
        for shot_type in SCREENSHOT_TYPES:
            # Dir.glob("images/<type>/*.{png,jpg,jpeg}", FNM_CASEFOLD).sort
            found = sorted(name for name in own
                           if PurePosixPath(name).parent == PurePosixPath("images", shot_type)
                           and PurePosixPath(name).suffix.lower().lstrip(".") in EXTENSIONS)
            if found:
                items[f"{language}/screenshots/{shot_type}"] = [f"{language}/{name}" for name in found]
    return items


def item_value(repo, ref, root, kind, files):
    """Comparable value of an item at a ref: normalized text or an ordered sha256 list."""
    if ref is None or not files:
        return "" if kind == "text" else []
    if kind == "text":
        return normalize_text(git(repo, "show", f"{ref}:{root}/{files[0]}"))
    return [hashlib.sha256(git(repo, "show", f"{ref}:{root}/{name}", binary=True)).hexdigest() for name in files]


def base_tag(repo, ref):
    """The latest release-v.* tag reachable from ref, excluding the tag of the version at ref."""
    toml = tomllib.loads(git(repo, "show", f"{ref}:gradle/libs.versions.toml"))
    current = f"release-v.{toml['versions']['versionName']}"
    tags = [tag for tag in git(repo, "tag", "--merged", ref, "--list", "release-v.*").split()
            if TAG.fullmatch(tag) and tag != current]
    tags.sort(key=lambda tag: tuple(int(part) for part in TAG.fullmatch(tag).groups()))
    return (tags[-1] if tags else None), current


def plan(repo, role, ref="HEAD"):
    root, with_text = ROLES[role]
    items = compared_items(tree(repo, ref, root), with_text)
    languages = {}
    for key in items:
        language, kind, name = key.split("/")
        entry = languages.setdefault(language, {"text": [], "images": []})
        entry["text" if kind == "text" else "images"].append(name)
    return {"role": role, "root": root, "ref": git(repo, "rev-parse", ref).strip(), "languages": languages}, items


def assert_clean(repo, root, items):
    """supply uploads the working tree, so for the compared files it must be the deployed commit."""
    paths = [f"{root}/{name}" for files in items.values() for name in files]
    if not paths:
        return
    dirty = git(repo, "status", "--porcelain", "--", *paths).strip()
    if dirty:
        raise DriftError(f"the working tree differs from the deployed commit for compared files:\n{dirty}")


def remote_value(remote, language, kind, name):
    entry = remote.get("languages", {}).get(language)
    if entry is None:
        raise DriftError(f"remote state has no language {language}")
    if kind == "text":
        if name not in entry.get("text", {}):
            raise DriftError(f"remote state has no {language} text field {name}")
        return normalize_text(entry["text"][name])
    if name not in entry.get("images", {}):
        raise DriftError(f"remote state has no {language} image type {name}")
    return [image["sha256"] for image in entry["images"][name]]


def decide(repo, role, plan_file, remote, out, allow_overwrite, ref="HEAD"):
    expected_plan, items = plan(repo, role, ref)
    root = expected_plan["root"]
    if plan_file != expected_plan:
        raise DriftError("the plan the reader used is not this commit's plan")
    if not items:
        raise DriftError(f"zero compared items under {root}: nothing proves the listing is unchanged")
    assert_clean(repo, root, items)
    base, current = base_tag(repo, ref)
    base_paths = compared_items(tree(repo, base, root), ROLES[role][1]) if base else {}
    print(f"{role} listing: {len(items)} compared items under {root}; base {base or '(none)'}, "
          f"excluding {current}; local {expected_plan['ref'][:12]}")
    verdicts, drifted = [], []
    for key, files in items.items():
        language, kind, name = key.split("/")
        base_value = item_value(repo, base, root, kind, base_paths.get(key, []))
        local_value = item_value(repo, ref, root, kind, files)
        remote_val = remote_value(remote, language, kind, name)
        if remote_val == base_value:
            verdict = "OK (remote == base" + (", repository change)" if local_value != base_value else " == local)")
        elif remote_val == local_value:
            verdict = "OK (remote == local)"
        else:
            verdict = "DRIFT"
            drifted.append((key, files, base_value, local_value, remote_val))
        verdicts.append({"item": key, "verdict": verdict.split()[0]})
        print(f"  {verdict.split()[0]:5} {key}: {verdict}")
    if not drifted:
        print(f"RESULT OK: {len(items)} items, no drift")
        return 0
    write_artifact(out, root, remote, drifted, verdicts)
    for key, _, base_value, local_value, remote_val in drifted:
        print(f"--- DRIFT {key}")
        if isinstance(remote_val, str):
            for label, value in (("base", base_value), ("local", local_value)):
                diff = difflib.unified_diff(value.splitlines(), remote_val.splitlines(), f"{label}/{key}",
                                            f"remote/{key}", lineterm="")
                print("\n".join(diff))
        else:
            print(f"    base   {base_value}\n    local  {local_value}\n    remote {remote_val}")
    summary = f"{len(drifted)} of {len(items)} items differ from both base and local; remote state under {out}"
    if allow_overwrite:
        print(f"WARNING: listing DRIFT overridden by allow_listing_overwrite: {summary}")
        return 0
    print(f"RESULT DRIFT: {summary}")
    return 1


def write_artifact(out, root, remote, drifted, verdicts):
    """Remote state of the drifted items, laid out like the metadata tree."""
    out = Path(out)
    fetch = []
    for key, _, _, _, _ in drifted:
        language, kind, name = key.split("/")
        if kind == "text":
            target = out / root / language / f"{name}.txt"
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(remote["languages"][language]["text"][name] or "", encoding="utf-8")
            continue
        for index, image in enumerate(remote["languages"][language]["images"][name], start=1):
            stem = f"images/{name}" if kind == "image" else f"images/{name}/{index}_{language}"
            fetch.append({"url": image["url"], "sha256": image["sha256"],
                          "path": str(out / root / language / stem)})
    out.mkdir(parents=True, exist_ok=True)
    (out / "fetch.json").write_text(json.dumps(fetch, indent=2), encoding="utf-8")
    (out / "drift.json").write_text(json.dumps(verdicts, indent=2), encoding="utf-8")


# --- self-test ---------------------------------------------------------------------------------------

def _commit(repo, files, message, tag=None):
    for path, content in files.items():
        target = repo / path
        if content is None:
            target.unlink()
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(content if isinstance(content, bytes) else content.encode("utf-8"))
    git(repo, "add", "-A")
    git(repo, "-c", "user.name=self-test", "-c", "user.email=self-test@invalid", "-c", "commit.gpgsign=false",
        "commit", "-q", "-m", message)
    if tag:
        git(repo, "-c", "tag.gpgsign=false", "tag", tag)


def _remote(texts=None, images=None):
    return {"languages": {"en-US": {"text": texts or {}, "images": images or {}}}}


def _image(content):
    return {"id": "1", "sha256": hashlib.sha256(content).hexdigest(), "url": "https://example.invalid/i"}


def self_test():
    phone = "fastlane/metadata/android/en-US"
    wear = "fastlane/metadata-wear/android/en-US/images/wearScreenshots"
    shot_a, shot_b, icon = b"shot-a", b"shot-b", b"icon"
    with tempfile.TemporaryDirectory() as tmp:
        repo = Path(tmp)
        git(repo, "init", "-q")
        _commit(repo, {"gradle/libs.versions.toml": '[versions]\nversionName = "1.0.0"\n',
                       f"{phone}/title.txt": "Workeeper\n", f"{phone}/video.txt": "",
                       f"{phone}/images/icon.png": icon, f"{phone}/images/phoneScreenshots/1_en-US.png": shot_a,
                       f"{phone}/images/phoneScreenshots/2_en-US.png": shot_b,
                       f"{phone}/changelogs/1.txt": "ignored\n"}, "base", tag="release-v.1.0.0")
        _commit(repo, {"gradle/libs.versions.toml": '[versions]\nversionName = "1.1.0"\n',
                       f"{phone}/title.txt": "Workeeper: gym log\n",
                       f"{wear}/1_en-US.png": shot_a, f"{wear}/2_en-US.png": shot_b},
                "deployed", tag="release-v.1.1.0")
        texts_base = {"title": "Workeeper", "video": ""}
        images_base = {"icon": [_image(icon)], "phoneScreenshots": [_image(shot_a), _image(shot_b)]}
        cases = [
            # (name, role, remote, allow, expected exit, evidence)
            ("remote differs from base and local, DRIFT (M-C1)", "phone",
             _remote({**texts_base, "title": "Workeeper (Console edit)"}, images_base), False, 1,
             "DRIFT en-US/text/title"),
            ("remote == base, local changed, OK (M-C2)", "phone", _remote(texts_base, images_base), False, 0,
             "OK    en-US/text/title: OK (remote == base, repository change)"),
            ("remote == local, OK (M-C3)", "phone",
             _remote({**texts_base, "title": "Workeeper: gym log"}, images_base), False, 0,
             "OK    en-US/text/title: OK (remote == local)"),
            ("screenshot order changed, DRIFT (M-C4)", "phone",
             _remote(texts_base, {**images_base, "phoneScreenshots": [_image(shot_b), _image(shot_a)]}), False, 1,
             "DRIFT en-US/screenshots/phoneScreenshots"),
            ("override, warning and exit 0 (M-C5)", "phone",
             _remote({**texts_base, "title": "Workeeper (Console edit)"}, images_base), True, 0,
             "WARNING: listing DRIFT overridden"),
            ("normalisation only: CRLF, trailing spaces and lines, OK", "phone",
             _remote({**texts_base, "title": "Workeeper  \r\n\r\n"}, images_base), False, 0, "RESULT OK: 4 items"),
            ("the tag being deployed is excluded from base", "phone", _remote(texts_base, images_base), False, 0,
             "base release-v.1.0.0, excluding release-v.1.1.0"),
            ("wear: absent at base and remote empty, OK", "wear", _remote(images={"wearScreenshots": []}), False, 0,
             "OK (remote == base, repository change)"),
            ("wear: Console screenshots differ from both, DRIFT", "wear",
             _remote(images={"wearScreenshots": [_image(b"console")]}), False, 1,
             "DRIFT en-US/screenshots/wearScreenshots"),
            ("remote misses a planned item, reader gap, exit 2", "phone", _remote({"title": "Workeeper"}, images_base),
             False, 2, "remote state has no en-US text field video"),
        ]
        failures, seen = [], set()
        for name, role, remote, allow, expected, evidence in cases:
            out = repo / "out" / str(len(seen))
            seen.add(name)
            actual, output = _run_decide(repo, role, remote, out, allow)
            ok = actual == expected and evidence in output
            print(f"  {'ok' if ok else 'MISMATCH':8} {name}: exit {actual}, expected {expected}")
            if not ok:
                print(output, end="")
                failures.append(name)
        artifact = repo / "out" / "0" / "fastlane/metadata/android/en-US/title.txt"
        if not (artifact.is_file() and artifact.read_text() == "Workeeper (Console edit)"):
            failures.append("M-C1 artifact does not hold the remote title at the metadata path")
        (repo / f"{phone}/title.txt").write_text("edited after checkout\n")
        actual, output = _run_decide(repo, "phone", _remote(texts_base, images_base), repo / "out" / "dirty", False)
        print(f"  {'ok' if actual == 2 else 'MISMATCH':8} dirty compared file, exit 2: exit {actual}")
        if actual != 2 or "working tree differs" not in output:
            failures.append("dirty tree")
        git(repo, "checkout", "-q", "--", f"{phone}/title.txt")
        _commit(repo, {f"{wear}/1_en-US.png": None, f"{wear}/2_en-US.png": None}, "no wear metadata")
        actual, output = _run_decide(repo, "wear", _remote(images={}), repo / "out" / "zero", False)
        print(f"  {'ok' if actual == 2 else 'MISMATCH':8} zero compared items, FAIL (M-C6): exit {actual}")
        if actual != 2 or "zero compared items" not in output:
            failures.append("zero items (M-C6)")
    total = len(cases) + 3
    if failures:
        print(f"self-test FAIL: {len(failures)} of {total} checks: {failures}")
        return 1
    print(f"self-test PASS: {total} checks, OK, DRIFT, override and FAIL each shown")
    return 0


def _run_decide(repo, role, remote, out, allow):
    output = io.StringIO()
    with contextlib.redirect_stdout(output):
        try:
            expected_plan, _ = plan(repo, role)
            code = decide(repo, role, expected_plan, remote, out, allow)
        except DriftError as error:
            print(f"LISTING ERROR (exit 2): {error}")
            code = 2
    return code, output.getvalue()


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("command", nargs="?", choices=("plan", "decide"))
    parser.add_argument("--role", choices=ROLES)
    parser.add_argument("--plan-out")
    parser.add_argument("--plan")
    parser.add_argument("--remote")
    parser.add_argument("--out")
    parser.add_argument("--allow-overwrite", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if not args.command or not args.role:
        parser.error("a command and --role are required unless --self-test")
    try:
        if args.command == "plan":
            if not args.plan_out:
                parser.error("plan needs --plan-out")
            planned, items = plan(REPO_ROOT, args.role)
            if not items:
                raise DriftError(f"zero compared items under {planned['root']}")
            Path(args.plan_out).parent.mkdir(parents=True, exist_ok=True)
            Path(args.plan_out).write_text(json.dumps(planned, indent=2), encoding="utf-8")
            print(f"plan: {len(items)} compared items for {args.role}: {sorted(items)}")
            return 0
        if not (args.plan and args.remote and args.out):
            parser.error("decide needs --plan, --remote and --out")
        planned = json.loads(Path(args.plan).read_text(encoding="utf-8"))
        remote = json.loads(Path(args.remote).read_text(encoding="utf-8"))
        return decide(REPO_ROOT, args.role, planned, remote, args.out, args.allow_overwrite)
    except (DriftError, OSError, json.JSONDecodeError, KeyError, tomllib.TOMLDecodeError) as error:
        print(f"LISTING ERROR (exit 2): {error}")
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
