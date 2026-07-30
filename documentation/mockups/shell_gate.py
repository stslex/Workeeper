#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
#
# Appearance gate for the v3 shell mockup. ONE command, run it from anywhere:
#
#     python3 documentation/mockups/shell_gate.py            # silent + exit 0 == green
#     python3 documentation/mockups/shell_gate.py -v         # the evidence table
#     python3 documentation/mockups/shell_gate.py --target f52462c7
#
# CI runs it on every pull request — `.github/workflows/mockup_gate.yml`. To reproduce what CI runs:
#
#     PR_BASE=dev   # or the branch below you, if the PR is stacked — NOT dev then
#     python3 documentation/mockups/shell_gate.py --base "$(git merge-base "origin/$PR_BASE" HEAD)" -v
#
# The branch matters: substituting `dev` on a stacked PR reports the parent PR's token changes
# as yours. Add `--allow-root-change <names>` only if a commit in the range declares one (see
# BASE, below).
#
# WHY THIS EXISTS. `pass2d.html` sections `#s-list` and `#s-nav` are the appearance contract for the
# chrome shared by the eight derived screens of the v3 arc — bottom bar, list row, selection mode, the
# add action, the paging tails. Nothing else in CI reads HTML, so until this script existed the contract that
# eight screens will be built against was gated by nobody. That is B9's class exactly ("nothing gates
# prose"), and the file is now load-bearing enough that the class is no longer cheap.
#
# WHAT IT DOES DIFFERENTLY. Six of the eight checks are structural — they read text, and text is what
# the previous instrument read. All six passed, continuously, while the thing the mockup exists to
# demonstrate was broken:
#
#   `#s-nav`'s indicator pill was PERMANENTLY ZERO-WIDTH for several rounds. `nbPlace()` sizes it from
#   `on.offsetWidth` at load; `#s-nav` is not the default screen; a `display:none` element measures
#   zero. Clicking the tabs moved an invisible box. Six green checks, one dead demo, nobody noticed.
#
# So checks 7 and 8 RENDER the file in headless Chromium and measure it. They are the cheapest
# instrument that can see this class at all. Check 7 is deliberately built in the escape's own shape:
# it loads with `#s-nav` NOT default, reaches it BY CLICKING THE SWITCHER BUTTON, and then asserts both
# that the pill has width and that it TRACKS — a pill frozen at a plausible offset must not pass.
#
# ---------------------------------------------------------------------------------------------------
# DO NOT SIMPLIFY THIS BACK — `BASE` IS A PIN, NOT A BRANCH NAME
# ---------------------------------------------------------------------------------------------------
# BASE defaults to the commit 9139d8c8, which is `f52462c7^`. The obvious simplification is
# `BASE = "dev"`. It has been tried. It produces a gate that certifies its own subject.
#
#   `f52462c7` put the shell mockup on `dev` BY DIRECT COMMIT — `git log -1 --format=%P f52462c7` is a
#   single parent, no merge, no PR. So `dev` and the thing under test are the same tree. Measured:
#
#       git diff dev f52462c7 -- documentation/mockups/pass2d.html   ->  0 lines of output
#       git diff 9139d8c8    -- documentation/mockups/pass2d.html    ->  174 added lines
#
#   Exactly two of the six structural checks consume the diff, and they are the two that go vacuous:
#
#     * check 3 (no new hex literal) inspects ADDED lines. With BASE=dev there are none, so it inspects
#       nothing and passes unconditionally. It cannot fail. It is not a check.
#     * check 1 (`:root` byte-identical) compares BASE's token block against TARGET's. With BASE=dev it
#       compares the merged file against itself. It cannot fail either.
#
#   A gate that certifies its own subject is worse than no gate: it converts "unverified" into
#   "verified", which is the one direction a reader cannot recover from. The remaining four structural
#   checks (2, 4, 5, 6) and both render checks read TARGET alone and are unaffected by BASE — which is
#   why the failure was survivable and also why it was invisible.
#
# IN CI, BASE IS A MERGE-BASE, AND THAT IS NOT THE FAILURE ABOVE. The rule the pin protects is that
#   THE BASELINE MUST NOT ALREADY CONTAIN THE CHANGE UNDER TEST. A pull request's base branch
#   satisfies that by construction — the PR's own commits are not in it — so
#   `.github/workflows/mockup_gate.yml` passes `--base $(git merge-base origin/<base_ref> HEAD)` and
#   is right to. What matters is that it is the BASE BRANCH, not that it is called `dev`: for a
#   stacked PR the base branch is the branch below, and substituting `dev` there imports the parent
#   PR's own declared `:root` change into the diff of the PR being gated (measured from
#   `docs/v3-token-parity`: `--base dev` reds check 1 on `rust`, `meta` and `molten`, none of which
#   the PR above it touches). Outside a pull request there is no base branch and the fallback is
#   `dev` — which on a run whose HEAD *is* `dev` degenerates to precisely the empty diff above, so
#   that path is a manual convenience and not the gate.
#
# RUN IT IN THE PR, BEFORE THE MERGE. A gate that runs after the merge certifies; it does not gate.
# `.github/workflows/mockup_gate.yml` is what now does this on every pull request, and it runs the
# `--target f52462c7` known negative alongside, requiring it to go red — so each run is evidence the
# detector still fires rather than a green last shown to fail at authoring time.
# ---------------------------------------------------------------------------------------------------
#
# WHAT THIS SCRIPT DOES NOT GUARD. It does not look at the app. `--rust` in this mockup is `#C4574A`,
# and `provideDarkAppColors()` ships `status.error = #DF714B`; the two have been out of step since the
# palette repaint and no instrument on either side can see it, because this one reads HTML and the
# contrast gate reads `AppColors.kt`. It also does not assert that the pill's slide and stretch LOOK
# right — check 7 kills the transition on purpose (see below) and measures placement, not motion.
# Motion is a device-review item and has never been signed off by anyone.
#
# EXIT CODES. 0 = every check passed. 1 = at least one check failed, or the instrument could not run.
# A missing browser is a FAILURE, never a skip: a check that quietly does not run is a green from a
# detector never shown to fire, which is the exact class this script was written to end.

from __future__ import annotations

import argparse
import base64
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

# --- parameters -------------------------------------------------------------------------------------
# Each value carries the reason for THE VALUE, not for the name.

BASE = "9139d8c8"  # f52462c7^ — the last commit BEFORE the shell sections landed. See the header.
TARGET = None  # None == the working tree. A git ref reads that ref's blob instead.

MOCKUP = "documentation/mockups/pass2d.html"

# The other half of the parity seam check 9 gates: the app's own colour tokens. Read from the
# WORKING TREE always, regardless of --target — this check asks "does the mockup match what ships
# right now", not "what did the mockup match as of some historical ref". A historical TARGET is
# expected to disagree (that disagreement is what B19 found), and check 9 is allowed to fail there.
APP_COLORS = (
    "core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/theme/AppColors.kt"
)

# Tokens with no corresponding AppColors.kt constant, by design — not drift. Each carries the
# citation a reader needs to not re-litigate it. Loosening check 9 to "most tokens resolve" would
# make it the kind of check that stops seeing a real drift among the noise of expected exceptions,
# so every exception is named individually here rather than pattern-matched away.
TOKEN_PARITY_EXCEPTIONS = {
    "--dim": "merged into meta, both themes — no AppColors.kt slot for a fourth text-dim step "
             "(§2.5, #184 C1)",
    "--hair-s": "EXPIRED, AND KEPT ONLY UNTIL THE PALETTE DECISION LANDS. The recorded reason was "
                "that the slots that would take it are enabled-control-outline borders owing 3:1 "
                "under WCAG 1.4.11, which hair-s's 1.12-1.52:1 cannot clear, so the app ships "
                "*_CONTROL_OUTLINE instead (B19) — and, in AppColors.kt's own words, that "
                "'borderSubtle covers every decorative stroke in the app'. That clause stopped "
                "being true when the v3 list row landed: the 88dp row is RULED with --hair-s "
                "(.row border-bottom), a decorative stroke borderSubtle does not cover — "
                "borderSubtle is --hair, a different value. A named exception is valid only while "
                "its reason holds; this one's has stopped. See all-trainings-extraction.md D3",
}

# The one variable legitimately absent from :root — nbPick() writes it per-element at runtime
# (`el.style.setProperty('--sx', ...)`). Named explicitly rather than loosening check 2 to a pattern,
# because a loosened check 2 stops seeing typos, which is the only thing it is for.
RUNTIME_VARS = {"--sx"}

BALANCED_TAGS = ("section", "div", "button", "svg", "span")

# §26's "Bottom navigation" row makes ONE variant normative — a `--sec` track under a hairline, active
# = lifted slab pill — and records the two it beat. `#s-nav` collapsed to it; `#s-list`'s own copy of
# the bar and the `.clash` demo were left at the pre-collapse `.nb plain pill` for a round, so the
# contract drew TWO DIFFERENT NAV BARS at once. That is the "Drawn rejections" failure one level up:
# there the worry is a rejected alternative surviving in the drawing, here it is a superseded variant
# surviving in a second copy of the same component.
#
# Every other check here reads the drawing against a BASELINE or against AppColors.kt. This one reads
# the drawing against ITSELF, which is the class nothing was watching. It is deliberately narrow —
# the general form ("no class is drawn with two different geometries") currently fails on `.chev`,
# which carries two different paths (`M9 5l7 7-7 7` in the shell rows, `M9 6l6 6-6 6` in `#s-past`),
# and widening it is a decision about that drawing, not about this one.
NB_REQUIRED_VARIANT = "track"
NB_REJECTED_VARIANTS = {
    "plain": "the untracked variant — rejected on the stage-5 gate-0 device pass (§26, Bottom navigation)",
}

DEFAULT_SCREEN = "s-live"  # exactly one section may carry `screen on`, and it must be this one

# Both switcher targets the render probe clicks. Their EXISTENCE is asserted separately and fails in
# its own name — see check 7/8 note.
PROBE_TARGETS = ("s-nav", "s-list")

# Load-bearing for every measurement: the mockup is a 452px-max phone layout and the pill width is read
# in pixels. At 420x900 the nav bar measures 410 and the pill 129. Change this and the numbers change.
WINDOW_SIZE = "420,900"

# Must exceed the probe's own timer timeline. Without it --dump-dom fires at load, no post-load timer
# ever runs, and the probe emits nothing at all. Budget size is nearly free in wall clock (a 30000
# budget and a 6000 budget both return in ~1s), so this is set generously.
VIRTUAL_TIME_BUDGET = 8000
PROBE_TIMEOUT_S = 90

CHROME_CANDIDATES = ("chromium", "chromium-browser", "google-chrome", "google-chrome-stable", "chrome")


# --- helpers ----------------------------------------------------------------------------------------

def repo_root() -> str:
    here = os.path.dirname(os.path.abspath(__file__))
    return os.path.abspath(os.path.join(here, "..", ".."))


def git(root: str, *args: str) -> str:
    out = subprocess.run(
        ["git", *args], cwd=root, capture_output=True, text=True, encoding="utf-8",
    )
    if out.returncode != 0:
        raise SystemExit(f"shell_gate: git {' '.join(args)} failed:\n{out.stderr.strip()}")
    return out.stdout


def read_blob(root: str, ref: str, path: str) -> str:
    return git(root, "show", f"{ref}:{path}")


def read_target(root: str, target: str | None, path: str) -> str:
    if target is None:
        with open(os.path.join(root, path), encoding="utf-8") as fh:
            return fh.read()
    return read_blob(root, target, path)


def css_block(src: str, selector: str) -> str | None:
    """Return `selector{...}` including both braces, matched by brace counting."""
    m = re.search(re.escape(selector) + r"\s*\{", src)
    if not m:
        return None
    i = m.end() - 1
    depth = 0
    for j in range(i, len(src)):
        if src[j] == "{":
            depth += 1
        elif src[j] == "}":
            depth -= 1
            if depth == 0:
                return src[m.start():j + 1]
    return None


def tools_block(src: str) -> str | None:
    """The `.tools` div, brace-matched on <div>/</div> so a nested div cannot truncate it."""
    m = re.search(r'<div class="tools">', src)
    if not m:
        return None
    depth = 0
    for tok in re.finditer(r"<div\b|</div>", src[m.start():]):
        depth += 1 if tok.group().startswith("<div") else -1
        if depth == 0:
            return src[m.start():m.start() + tok.end()]
    return None


def open_tags(src: str) -> list[tuple[str, dict[str, str]]]:
    """(tagname, attrs) for every opening tag. Used by checks 5 and 6 so attribute ORDER never matters."""
    out = []
    for m in re.finditer(r"<([A-Za-z][A-Za-z0-9]*)((?:\s+[^<>]*?)?)/?>", src):
        attrs = dict(re.findall(r'([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*"([^"]*)"', m.group(2)))
        out.append((m.group(1).lower(), attrs))
    return out


def hex_to_rgb_css(value: str) -> str | None:
    v = value.strip().lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    if len(v) != 6 or not re.fullmatch(r"[0-9a-fA-F]{6}", v):
        return None
    return f"rgb({int(v[0:2], 16)}, {int(v[2:4], 16)}, {int(v[4:6], 16)})"


def normalize_hex6(value: str) -> str | None:
    """3- or 6-digit hex, with or without '#', to uppercase 6-digit RGB. None if not a hex colour."""
    v = value.strip().lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    if len(v) != 6 or not re.fullmatch(r"[0-9a-fA-F]{6}", v):
        return None
    return v.upper()


def extract_props(block_src: str) -> dict[str, str]:
    """`{name: raw_value}` for every `--name: value;` declaration in a css_block() string."""
    return {name: value.strip() for name, value in
            re.findall(r"(--[A-Za-z0-9_-]+)\s*:\s*([^;]+);", block_src)}


def extract_hex_props(block_src: str) -> dict[str, str]:
    """`{name: HEXHEXHEX}` for every declaration whose value is a plain (non-alpha) hex colour.

    `rgba(...)` values are excluded by construction (normalize_hex6 rejects a value that isn't a
    bare 3/6-digit hex), which is exactly the scoping check 9 wants: translucent tokens carry no
    single opaque byte triple to compare against an AppColors.kt constant.
    """
    out = {}
    for name, raw in extract_props(block_src).items():
        hexed = normalize_hex6(raw)
        if hexed is not None:
            out[name] = hexed
    return out


def extract_kt_color_constants(kt_src: str) -> tuple[dict[str, str], dict[str, str]]:
    """`({RGB: const_name}, {RGB: const_name})` for DARK_* / LIGHT_* Long hex constants.

    `AppColors.kt` writes each as `private const val DARK_FOO: Long = 0xAARRGGBB` (or `0xRRGGBB`
    for the few 6-digit ones). Only the low 6 hex digits are kept — the alpha byte, when present,
    is compositing information the mockup's flat `#rrggbb` tokens don't carry, and check 9 compares
    RGB only, on the same reasoning `hex_to_rgb_css` already uses for the FAB glyph token.
    """
    dark: dict[str, str] = {}
    light: dict[str, str] = {}
    pat = re.compile(r"private const val (DARK|LIGHT)_([A-Z0-9_]+)\s*:\s*Long\s*=\s*0x([0-9A-Fa-f]{6,8})")
    for theme, name, digits in pat.findall(kt_src):
        rgb = digits[-6:].upper()
        (dark if theme == "DARK" else light)[rgb] = f"{theme}_{name}"
    return dark, light


# --- results ----------------------------------------------------------------------------------------

class Report:
    def __init__(self) -> None:
        self.rows: list[tuple[int | None, str, bool, str, list[str]]] = []

    def add(self, num: int | None, name: str, ok: bool, note: str, detail: list[str] | None = None) -> None:
        self.rows.append((num, name, ok, note, detail or []))

    @property
    def failed(self) -> bool:
        return any(not ok for _, _, ok, _, _ in self.rows)


# --- structural checks ------------------------------------------------------------------------------

def check_1_root_identical(rep: Report, base_src: str, tgt_src: str, allow_root_change: list[str] | None) -> None:
    """Default mode (`allow_root_change is None`): BASE's and TARGET's `:root`/`body.light` must be
    byte-identical — the original, unconditional rule.

    `--allow-root-change NAME [NAME ...]` mode: byte-identity is dropped for a property-level
    check instead. Every custom property whose value differs between BASE and TARGET (in either
    block) must be named; every named property must have actually changed. Both directions are
    enforced because either one alone is a hole: unnamed-allowed lets an unreviewed change ride
    along with a reviewed one, and named-but-unchanged lets a stale flag rot into a standing
    excuse that stops meaning anything (the same failure §3.3 names for a duplicate contrast-map
    key — a declaration nobody checks is not a declaration)."""
    parts: list[str] = []
    detail: list[str] = []
    ok = True
    all_changed: set[str] = set()
    changes_by_name: dict[str, list[str]] = {}

    for sel, theme in ((":root", "dark"), ("body.light", "light")):
        b, t = css_block(base_src, sel), css_block(tgt_src, sel)
        if b is None or t is None:
            ok = False
            parts.append(f"{sel}: MISSING in {'BASE' if b is None else 'TARGET'}")
            continue
        if b == t:
            parts.append(f"{sel}: identical ({len(t)} bytes)")
            continue
        if allow_root_change is None:
            ok = False
            bl = [ln.strip() for ln in b.splitlines() if ln.strip()]
            tl = [ln.strip() for ln in t.splitlines() if ln.strip()]
            diff = [f"    - {ln}" for ln in bl if ln not in tl] + [f"    + {ln}" for ln in tl if ln not in bl]
            parts.append(f"{sel}: CHANGED")
            detail += [f"  {sel}:"] + diff
            continue
        base_props, tgt_props = extract_props(b), extract_props(t)
        names = set(base_props) | set(tgt_props)
        block_changed = {n for n in names if base_props.get(n) != tgt_props.get(n)}
        all_changed |= block_changed
        for n in sorted(block_changed):
            changes_by_name.setdefault(n, []).append(
                f"    {theme} {n}: {base_props.get(n, '(absent)')} → {tgt_props.get(n, '(absent)')}")
        parts.append(f"{sel}: {len(block_changed)} propert{'y' if len(block_changed) == 1 else 'ies'} changed")

    if allow_root_change is not None:
        allowed = set(allow_root_change)
        unnamed = all_changed - allowed
        not_actually = allowed - all_changed
        if unnamed:
            ok = False
            detail += [f"    UNNAMED change: {n} changed but was not passed to --allow-root-change"
                       for n in sorted(unnamed)]
        if not_actually:
            ok = False
            detail += [f"    NAMED but unchanged: --allow-root-change named {n}, but its value is "
                       f"identical between BASE and TARGET in every block" for n in sorted(not_actually)]
        if not unnamed and not not_actually and changes_by_name:
            detail += ["    allowed changes:"] + [line for n in sorted(changes_by_name) for line in changes_by_name[n]]

    rep.add(
        1, ":root byte-identical (or declared via --allow-root-change)", ok,
        "; ".join(p.splitlines()[0] for p in parts),
        detail,
    )


def check_2_vars_defined(rep: Report, tgt_src: str) -> None:
    root = css_block(tgt_src, ":root") or ""
    defined = set(re.findall(r"(--[A-Za-z0-9_-]+)\s*:", root))
    used = set(re.findall(r"var\(\s*(--[A-Za-z0-9_-]+)", tgt_src))
    undefined = sorted(used - defined - RUNTIME_VARS)
    rep.add(
        2, "no undefined var(--x)", not undefined,
        f"{len(used)} used, {len(defined)} defined in :root, {len(RUNTIME_VARS)} runtime-set"
        + (f", UNDEFINED: {', '.join(undefined)}" if undefined else ""),
        [f"    {v} is used but never defined in :root" for v in undefined],
    )


def check_3_no_new_hex(rep: Report, diff: str, allow_root_change: list[str] | None) -> None:
    """Same diff check 1 reads — the DO NOT SIMPLIFY header already calls checks 1 and 3 "the two
    that consume the diff." A `:root`/`body.light` value edit necessarily adds a line containing a
    hex literal, so this check and check 1 are coupled by construction, not by oversight: without
    an escape hatch here too, --allow-root-change would only move where the PR's own gate stays
    permanently red, not remove it.

    Under --allow-root-change, EVERY `--name: value;` custom-property fragment is stripped from
    each added line before scanning — not just the named tokens' own fragments. That is
    deliberately broader than "trust what was named": this file packs several declarations per
    physical line, so changing one property re-adds the whole line, and every OTHER property on
    that line — changed or not, named or not — reappears as "added" text purely as a diff
    artefact. check 1 is what actually audits which properties changed and whether that matches
    what was named; check 3's only remaining job here is hex literals OUTSIDE the custom-property
    system (an inline `style="color:#..."`, say), which is exactly what survives this strip."""
    added = [ln[1:] for ln in diff.splitlines() if ln.startswith("+") and not ln.startswith("+++")]
    strip_pat = re.compile(r"--[A-Za-z0-9_-]+\s*:\s*[^;]+;") if allow_root_change else None
    scanned = [strip_pat.sub("", ln) if strip_pat else ln for ln in added]
    pat = re.compile(r"#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})\b")
    hits = [(i, ln.strip(), m) for i, (ln, sln) in enumerate(zip(added, scanned), 1) for m in pat.findall(sln)]
    note = (f"{len(added)} added lines inspected"
            + (f", {len(hits)} hex literal(s) found" if hits else ", 0 hex literals"))
    if allow_root_change:
        note += "; custom-property declarations excluded from the scan under --allow-root-change (check 1 audits those)"
    rep.add(
        3, "no new hex literal", not hits, note,
        [f"    added line {i}: {m}  in  {ln[:110]}" for i, ln, m in hits],
    )


def check_4_tags_balanced(rep: Report, tgt_src: str) -> None:
    bad, parts = [], []
    for t in BALANCED_TAGS:
        o = len(re.findall(rf"<{t}[\s>/]", tgt_src))
        c = len(re.findall(rf"</{t}>", tgt_src))
        parts.append(f"{t} {o}/{c}")
        if o != c:
            bad.append(f"    <{t}> opened {o} times, closed {c} times ({o - c:+d})")
    rep.add(4, "tags balanced", not bad, " ".join(parts), bad)


def check_5_switcher_complete(rep: Report, tgt_src: str) -> None:
    sections = {a["id"] for t, a in open_tags(tgt_src)
                if t == "section" and a.get("id", "").startswith("s-")}
    tools = tools_block(tgt_src)
    if tools is None:
        rep.add(5, "switcher complete", False, "the .tools div is missing entirely",
                ["    <div class=\"tools\"> not found — nothing can switch screens"])
        return
    buttons = {a["data-s"] for t, a in open_tags(tools) if t == "button" and "data-s" in a}
    missing_button = sorted(sections - buttons)
    missing_section = sorted(buttons - sections)
    detail = ([f"    section #{s} has no .tools button pointing at it" for s in missing_button]
              + [f"    .tools button data-s=\"{s}\" points at a section that does not exist" for s in missing_section])
    rep.add(
        5, "switcher complete", not detail,
        f"{len(sections)} sections, {len(buttons)} switcher buttons"
        + (f", {len(detail)} mismatch(es)" if detail else ", sets equal both ways"),
        detail,
    )


def check_6_one_default_screen(rep: Report, tgt_src: str) -> None:
    on = [(t, a) for t, a in open_tags(tgt_src)
          if {"screen", "on"} <= set(a.get("class", "").split())]
    ids = [a.get("id", f"<{t}> with no id") for t, a in on]
    ok = len(on) == 1 and ids[0] == DEFAULT_SCREEN
    if len(on) != 1:
        note = f"{len(on)} elements carry `screen on`: {', '.join(ids) or '(none)'}"
    else:
        note = f"the default screen is #{ids[0]}"
    rep.add(
        6, f"one default screen, and it is #{DEFAULT_SCREEN}", ok, note,
        [f"    expected exactly one `screen on`, on #{DEFAULT_SCREEN}; found: {', '.join(ids) or '(none)'}"]
        if not ok else [],
    )


def check_10_nav_variant_consistent(rep: Report, tgt_src: str) -> None:
    """Every `.nb` drawn anywhere in the file must be the normative variant.

    Not a style rule — a self-consistency rule. A contract that draws one component two ways stops
    being a contract, which is the same argument that removed the count-bearing FAB from the file."""
    bars = re.findall(r'<div class="(nb[^"]*)"', tgt_src)
    fails: list[str] = []
    if not bars:
        fails.append("    no `.nb` is drawn anywhere — this check has nothing to compare and would "
                     "otherwise pass vacuously")
    for cls in bars:
        parts = set(cls.split())
        if NB_REQUIRED_VARIANT not in parts:
            fails.append(f"    `.{cls}` is missing the normative `{NB_REQUIRED_VARIANT}` variant")
        for rejected, why in NB_REJECTED_VARIANTS.items():
            if rejected in parts:
                fails.append(f"    `.{cls}` carries `{rejected}` — {why}")
    variants = sorted({" ".join(sorted(c.split())) for c in bars})
    rep.add(
        10, "nav bar drawn in one variant everywhere", not fails,
        f"{len(bars)} bar(s) drawn, {len(variants)} distinct variant(s): "
        + "; ".join(f".{v}" for v in variants),
        fails,
    )


# --- render checks ----------------------------------------------------------------------------------

# The probe runs INSIDE the page. Every interaction goes through the real handler (`.click()` fires the
# inline onclick) so the gate exercises what a reader's finger would.
PROBE_JS = r"""
<script>
(function () {
  var R = { stage: 'init', targets: {}, pill: {}, fab: {}, tokens: {} };

  function emit() {
    try {
      document.body.setAttribute(
        'data-probe', btoa(unescape(encodeURIComponent(JSON.stringify(R)))));
    } catch (e) {
      document.body.setAttribute('data-probe-error', String(e));
    }
  }

  function run() {
    var rootCS = getComputedStyle(document.documentElement);
    R.tokens.rust = rootCS.getPropertyValue('--rust').trim();
    R.tokens.max = rootCS.getPropertyValue('--max').trim();

    // The switcher buttons are asserted BEFORE anything is clicked, and reported under their own name.
    // Deleting the s-nav button breaks check 5 AND removes what this probe clicks; without this the
    // gate would report "pill zero-width" for a fault that is a missing button, and misdirect whoever
    // reads the red a year from now.
    var btn = {};
    __TARGETS__.forEach(function (id) {
      btn[id] = document.querySelector('.tools button[data-s="' + id + '"]');
      R.targets[id] = !!btn[id];
    });
    if (__TARGETS__.some(function (id) { return !btn[id]; })) {
      R.stage = 'switcher-target-missing';
      emit();
      return;
    }

    /* ---- check 7: the nav pill renders and tracks ---- */
    var secNav = document.getElementById('s-nav');
    R.pill.sectionPresent = !!secNav;
    if (!secNav) { R.stage = 'no-s-nav-section'; emit(); return; }

    // If #s-nav were the default screen the check could not discriminate — that is precisely the
    // configuration under which the escape hid. Record it so a vacuous pass is impossible.
    R.pill.defaultAtLoad = secNav.classList.contains('on');

    btn['s-nav'].click();
    R.pill.reached = secNav.classList.contains('on');

    // Structural, not by id — and that is load-bearing, not a style choice. This probe reads
    // TARGET's bytes via `git show <ref>:path`, which can be a ref from years before today's
    // markup; an id selector is a bet that whatever the nav pill was called at every ref this
    // gate must still discriminate against matches what it is called today, and that bet has
    // already been lost once (the collapse from three drawn nav variants to one renamed the
    // surviving one's ids, which silently turned the f52462c7 known-negative from "pill
    // measures 0px wide" into "no pill element found" — a different, weaker failure than the
    // one this check exists to prove). `#s-nav .ind` has no such dependency: verified unique at
    // both HEAD and f52462c7 (one `.ind` inside `#s-nav` at each — `#s-chart` has the chart tab
    // indicator, its own unrelated `.ind`, which is why the scope to `#s-nav` is required, not
    // optional). If a future edit needs two `.ind` elements inside `#s-nav` at once, this
    // selector stops being unique and this comment is the warning that should stop you before
    // the gate does.
    var ind = secNav.querySelector('.ind'), bar = ind && ind.closest('.nb');
    R.pill.elementsPresent = !!(bar && ind);
    if (!bar || !ind) { R.stage = 'no-pill-elements'; emit(); return; }

    // DO NOT REMOVE. Under --virtual-time-budget no compositor frames are produced and
    // requestAnimationFrame is effectively dead (measured: 12 ticks in 22 virtual seconds), so a
    // transition is sampled once and then never advances. Any read taken while one is in flight comes
    // back frozen at that first sample — measured: a 340ms transform transition still reporting
    // matrix(1,0,0,1,7.6446,0) twelve virtual seconds later, and a border-radius still reporting 18px
    // 8.8 virtual seconds after the class that changes it to 28px was applied. This is not a colour
    // problem: EVERY transitioned property is affected, geometry included. The reading you get without
    // this line is the element's true PRE-interaction style, so it looks like a legitimate measurement
    // rather than an instrument failure — it produced two false alarms in one session, a morph
    // reported as not firing and a theme reported as not applying. Kill the transition, then measure.
    ind.style.transition = 'none';

    var barLeft = bar.getBoundingClientRect().left;
    R.pill.w1 = ind.offsetWidth;
    R.pill.x1 = Math.round((ind.getBoundingClientRect().left - barLeft) * 100) / 100;

    var tabs = [].slice.call(bar.querySelectorAll('button'));
    var cur = bar.querySelector('button.on');
    var other = null;
    for (var i = 0; i < tabs.length; i++) { if (tabs[i] !== cur) { other = tabs[i]; break; } }
    R.pill.tabCount = tabs.length;
    if (!other) { R.stage = 'no-second-tab'; emit(); return; }

    R.pill.targetX = other.offsetLeft;
    other.click();
    R.pill.w2 = ind.offsetWidth;
    R.pill.x2 = Math.round((ind.getBoundingClientRect().left - bar.getBoundingClientRect().left) * 100) / 100;

    /* ---- check 8: the FAB morph fires ---- */
    btn['s-list'].click();
    var secList = document.getElementById('s-list');
    R.fab.reached = !!(secList && secList.classList.contains('on'));

    var fab = document.getElementById('morphA');
    var sel = document.getElementById('selBtn');
    R.fab.present = !!fab;
    R.fab.togglePresent = !!sel;
    if (!fab || !sel) { R.stage = 'no-fab-or-toggle'; emit(); return; }

    var gp = fab.querySelector('.gplus'), gt = fab.querySelector('.gtrash');
    R.fab.glyphsPresent = !!(gp && gt);
    if (!gp || !gt) { R.stage = 'no-fab-glyphs'; emit(); return; }

    // DO NOT REMOVE — same reason as the pill above. Set BEFORE the baseline read: a computed read
    // taken while a transition is armed poisons every later read of that property.
    fab.style.transition = 'none';

    function sample() {
      var cs = getComputedStyle(fab);
      return {
        radius: cs.borderTopLeftRadius,
        bg: cs.backgroundColor,
        fg: cs.color,
        plus: getComputedStyle(gp).display,
        trash: getComputedStyle(gt).display,
        cls: fab.className
      };
    }

    R.fab.before = sample();
    sel.click();              // the real handler: toggleSel()
    R.fab.after = sample();

    R.stage = 'complete';
    emit();
  }

  function boot() { setTimeout(function () { try { run(); } catch (e) {
    R.stage = 'threw: ' + (e && e.message ? e.message : String(e)); emit(); } }, 250); }

  if (document.readyState === 'complete') { boot(); }
  else { window.addEventListener('load', boot); }
})();
</script>
</body>"""


def find_browser() -> str | None:
    for name in CHROME_CANDIDATES:
        p = shutil.which(name)
        if p:
            return p
    return None


def run_probe(tgt_src: str, browser: str, verbose: bool) -> dict:
    """Render TARGET's bytes and return the probe payload. Raises SystemExit if the probe never ran."""
    workdir = tempfile.mkdtemp(prefix="shell-gate-")
    page = os.path.join(workdir, "pass2d.probe.html")
    if "</body>" not in tgt_src:
        raise SystemExit("shell_gate: the mockup has no </body> — cannot inject the probe")
    injected = PROBE_JS.replace("__TARGETS__", json.dumps(list(PROBE_TARGETS)))
    with open(page, "w", encoding="utf-8") as fh:
        fh.write(tgt_src.replace("</body>", injected, 1))

    cmd = [
        browser, "--headless", "--disable-gpu", "--no-sandbox",
        f"--user-data-dir={os.path.join(workdir, 'chrome-profile')}",
        f"--virtual-time-budget={VIRTUAL_TIME_BUDGET}",
        f"--window-size={WINDOW_SIZE}",
        "--dump-dom", f"file://{page}",
    ]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True,
                             encoding="utf-8", timeout=PROBE_TIMEOUT_S)
    except subprocess.TimeoutExpired:
        raise SystemExit(f"shell_gate: the browser did not return within {PROBE_TIMEOUT_S}s\n"
                         f"  command: {' '.join(cmd)}")
    # Chromium writes dbus/UPower/SSL noise to stderr on every run here; stderr is NOT a failure
    # signal. The DOM arrives on stdout, and its absence is.
    m = re.search(r'data-probe="([A-Za-z0-9+/=]*)"', out.stdout)
    if not m:
        err = re.search(r'data-probe-error="([^"]*)"', out.stdout)
        raise SystemExit(
            "shell_gate: the render probe produced no payload — it never executed.\n"
            "  This is a FAILURE, not a skip: an unrun check is a green from a detector never shown\n"
            "  to fire, which is the class this gate exists to end.\n"
            f"  browser exit: {out.returncode}\n"
            + (f"  probe error : {html.unescape(err.group(1))}\n" if err else "")
            + f"  stderr tail : {out.stderr.strip().splitlines()[-1] if out.stderr.strip() else '(empty)'}"
        )
    payload = json.loads(base64.b64decode(html.unescape(m.group(1))).decode("utf-8"))
    if verbose:
        print(f"    probe stage: {payload.get('stage')}   ({' '.join(cmd[1:6])})")
    return payload


def check_probe_targets(rep: Report, p: dict) -> bool:
    missing = [k for k in PROBE_TARGETS if not p.get("targets", {}).get(k)]
    rep.add(
        None, "switcher target exists (probe precondition)", not missing,
        "both .tools buttons present" if not missing
        else f"MISSING: {', '.join('.tools button[data-s=\"%s\"]' % k for k in missing)}",
        [f"    .tools button[data-s=\"{k}\"] does not exist — the probe has nothing to click.\n"
         f"    Checks 7 and 8 are reported UNMEASURED below rather than as render failures: the fault\n"
         f"    is a missing button, not a dead pill or a dead morph." for k in missing],
    )
    return not missing


def check_7_nav_pill(rep: Report, p: dict, measured: bool) -> None:
    if not measured:
        rep.add(7, "nav pill renders and tracks", False,
                f"UNMEASURED — the probe stopped at '{p.get('stage')}'",
                ["    Not a render verdict. Fix the failure reported above and re-run."])
        return
    pill = p.get("pill", {})
    fails = []
    if pill.get("defaultAtLoad"):
        fails.append("    #s-nav IS the default screen — check 7 cannot discriminate in this "
                     "configuration.\n    The escape it exists to catch only happens when it is not.")
    if not pill.get("reached"):
        fails.append("    clicking the s-nav switcher button did not put `on` on #s-nav")
    w1, w2 = pill.get("w1"), pill.get("w2")
    x1, x2, tx = pill.get("x1"), pill.get("x2"), pill.get("targetX")
    if not w1:
        fails.append(f"    the pill measured {w1}px wide after reaching #s-nav by click.\n"
                     "    nbPlace() sizes it from offsetWidth; a hidden element measures zero, so a\n"
                     "    zero here means nothing re-measured it on the switch.")
    if not w2:
        fails.append(f"    the pill measured {w2}px wide after the second tab click")
    if w1 and x1 is not None and x2 is not None and x1 == x2:
        fails.append(f"    the pill did NOT track: it sat at x={x1}px before and after the second tab\n"
                     "    click. A pill frozen at a plausible offset is what this assertion exists for.")
    if w1 and tx is not None and x2 is not None and abs(x2 - tx) > 1.0:
        fails.append(f"    the pill moved to x={x2}px but the tab it was sent to is at x={tx}px")
    rep.add(
        7, "nav pill renders and tracks", not fails,
        f"default-at-load={pill.get('defaultAtLoad')} reached={pill.get('reached')} "
        f"width={w1}px→{w2}px  x={x1}→{x2} (target {tx}, {pill.get('tabCount')} tabs)",
        fails,
    )


def check_8_fab_morph(rep: Report, p: dict, measured: bool) -> None:
    if not measured:
        rep.add(8, "FAB morph fires", False,
                f"UNMEASURED — the probe stopped at '{p.get('stage')}'",
                ["    Not a render verdict. Fix the failure reported above and re-run."])
        return
    fab = p.get("fab", {})
    b, a = fab.get("before"), fab.get("after")
    if not b or not a:
        rep.add(8, "FAB morph fires", False,
                f"the probe never sampled the FAB (stage '{p.get('stage')}')", [])
        return
    fails = []
    if not fab.get("reached"):
        fails.append("    clicking the s-list switcher button did not put `on` on #s-list")
    if b["radius"] == a["radius"]:
        fails.append(f"    radius did not change: {b['radius']} before, {a['radius']} after.\n"
                     "    The morph is a squircle opening into a circle; an unchanged radius is no morph.")
    # INVERTED, DELIBERATELY. This asserted that the fill CHANGED and that it became `--rust`,
    # which encoded the decision §26 "FAB in selection mode" has since retracted: the action is
    # archive, archive is reversible, and §1 makes `--rust` mark destruction only, so a rust fill
    # promised irreversibility for a reversible act. The morph is shape and glyph only. A gate that
    # still demanded the old fill would have made the correction unshippable, so the check moves
    # with the ledger — and it moves to the *stronger* form, because "the fill must not change" is
    # the assertion that catches a regression back to rust, which "the fill must change" never could.
    if b["bg"] != a["bg"]:
        fails.append(f"    the fill CHANGED: {b['bg']} → {a['bg']}. The morph is shape and glyph only;\n"
                     "    the fill stays `--max`. A colour change here is the retracted `--rust` fill\n"
                     "    coming back — see §26 \"FAB in selection mode\".")
    mx = hex_to_rgb_css(p.get("tokens", {}).get("max", ""))
    if mx and a["bg"] != mx:
        fails.append(f"    the morphed fill is {a['bg']}, but --max resolves to {mx}.\n"
                     "    The FAB keeps its ordinary treatment through the morph, from the token.")
    if not (b["plus"] != "none" and a["plus"] == "none"):
        fails.append(f"    the plus glyph did not go hidden: display {b['plus']} → {a['plus']}")
    if not (b["trash"] == "none" and a["trash"] != "none"):
        fails.append(f"    the trash glyph did not go visible: display {b['trash']} → {a['trash']}")
    rep.add(
        8, "FAB morph fires", not fails,
        f"radius {b['radius']}→{a['radius']}  fill {b['bg']} (unchanged, --max)  "
        f"glyphs +{b['plus']}→{a['plus']} /trash {b['trash']}→{a['trash']}",
        fails,
    )


def check_9_token_parity(rep: Report, tgt_src: str, root: str) -> None:
    """Every opaque `#rrggbb` the mockup draws must resolve to an `AppColors.kt` constant of the
    same theme, or be a named exception (`TOKEN_PARITY_EXCEPTIONS`) — not a loosened rule, an
    individually-cited one. `AppColors.kt` is read from the WORKING TREE regardless of --target;
    see APP_COLORS's own comment for why a historical TARGET is allowed to fail this check."""
    try:
        kt_src = read_target(root, None, APP_COLORS)
    except (OSError, SystemExit) as e:
        rep.add(9, "token parity with AppColors.kt", False,
                f"could not read {APP_COLORS}: {e}",
                ["    Checks 9 needs this file to compare against; it cannot be green without it."])
        return

    dark_consts, light_consts = extract_kt_color_constants(kt_src)
    if not dark_consts or not light_consts:
        rep.add(9, "token parity with AppColors.kt", False,
                f"parsed {len(dark_consts)} DARK_* and {len(light_consts)} LIGHT_* constants from "
                f"{APP_COLORS} — expected both non-empty; the regex or the file moved",
                ["    A check that silently matches nothing is a pass no different from a check that"
                 "    never ran — see the header's note on that class of failure."])
        return

    root_hex = extract_hex_props(css_block(tgt_src, ":root") or "")
    light_hex = extract_hex_props(css_block(tgt_src, "body.light") or "")

    fails: list[str] = []
    excused: list[str] = []
    checked = 0
    for theme, props, consts in (("dark", root_hex, dark_consts), ("light", light_hex, light_consts)):
        for name, hexv in sorted(props.items()):
            if name in TOKEN_PARITY_EXCEPTIONS:
                excused.append(f"    (excused) {theme} {name}:#{hexv} — {TOKEN_PARITY_EXCEPTIONS[name]}")
                continue
            checked += 1
            if hexv in consts:
                continue
            fails.append(
                f"    {theme} {name}:#{hexv} matches no AppColors.kt {theme.upper()}_* constant "
                f"— either it drifted (see B19) or it is a new exception this check does not know about yet"
            )

    rep.add(
        9, "token parity with AppColors.kt", not fails,
        f"{checked} opaque token(s) checked against {len(dark_consts)}/{len(light_consts)} "
        f"dark/light constants, {len(TOKEN_PARITY_EXCEPTIONS)} named exception(s)"
        + (f", {len(fails)} unresolved" if fails else ", all resolve"),
        fails + excused,
    )


# --- main -------------------------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser(
        description="Appearance gate for the v3 shell mockup (documentation/mockups/pass2d.html).",
        epilog="BASE is a PIN, not a branch name. Read the DO NOT SIMPLIFY THIS BACK block at the top "
               "of this file before changing it.",
    )
    ap.add_argument("--base", default=BASE,
                    help=f"baseline ref for checks 1 and 3 (default: {BASE}, i.e. f52462c7^)")
    ap.add_argument("--target", default=TARGET,
                    help="git ref to gate (default: the working tree)")
    ap.add_argument("-v", "--verbose", action="store_true",
                    help="print the evidence table even when green")
    ap.add_argument("--allow-root-change", nargs="+", default=None, metavar="TOKEN",
                    help="declare :root/body.light token names (bare, no leading --, e.g. "
                         "'rust meta') expected to differ from BASE. Check 1 then verifies the "
                         "actual diff is EXACTLY this set — not a smaller one, not a bigger one — "
                         "instead of requiring byte-identity. Omit for the default, unconditional "
                         "byte-identical rule.")
    args = ap.parse_args()

    root = repo_root()
    rep = Report()

    base_src = read_blob(root, args.base, MOCKUP)
    tgt_src = read_target(root, args.target, MOCKUP)
    diff = git(root, "diff", args.base, *( [args.target] if args.target else [] ), "--", MOCKUP)

    allow_root_change = (
        [f"--{t.lstrip('-')}" for t in args.allow_root_change]
        if args.allow_root_change is not None else None
    )

    check_1_root_identical(rep, base_src, tgt_src, allow_root_change)
    check_2_vars_defined(rep, tgt_src)
    check_3_no_new_hex(rep, diff, allow_root_change)
    check_4_tags_balanced(rep, tgt_src)
    check_5_switcher_complete(rep, tgt_src)
    check_6_one_default_screen(rep, tgt_src)

    browser = find_browser()
    if browser is None:
        # A missing browser is a FAIL, never a skip. See the header.
        rep.add(None, "headless browser available", False,
                f"none of {', '.join(CHROME_CANDIDATES)} is on PATH",
                ["    Checks 7 and 8 cannot run, so they cannot be green. Install Chromium or run this",
                 "    gate where one exists — do not read the absence as a pass."])
        check_7_nav_pill(rep, {"stage": "no-browser"}, measured=False)
        check_8_fab_morph(rep, {"stage": "no-browser"}, measured=False)
    else:
        payload = run_probe(tgt_src, browser, args.verbose)
        targets_ok = check_probe_targets(rep, payload)
        complete = payload.get("stage") == "complete"
        check_7_nav_pill(rep, payload, measured=targets_ok and complete)
        check_8_fab_morph(rep, payload, measured=targets_ok and complete)

    check_9_token_parity(rep, tgt_src, root)
    check_10_nav_variant_consistent(rep, tgt_src)

    if rep.failed or args.verbose:
        tgt_label = args.target if args.target else "working tree"
        print(f"==== shell gate — {MOCKUP} ====")
        print(f"base   {args.base}   target   {tgt_label}")
        if browser:
            print(f"render {browser} @ {WINDOW_SIZE}")
        print()
        for num, name, ok, note, detail in rep.rows:
            tag = f"{num}" if num is not None else "·"
            print(f"  [{'PASS' if ok else 'FAIL'}] {tag:>1}  {name}")
            if note:
                print(f"           {note}")
            for line in detail:
                print(line)
        print()
        bad = sum(1 for _, _, ok, _, _ in rep.rows if not ok)
        print(f"  {len(rep.rows) - bad} passed, {bad} failed")
        if rep.failed:
            print()
            print("  Reading it: a FAIL on 7 or 8 that says UNMEASURED is not a render verdict — look")
            print("  at the precondition row above it first. A FAIL on 1 or 3 means a token block or an")
            print("  added line changed relative to BASE; check that BASE is still the pin and not a")
            print("  branch name before believing anything else. A FAIL on 1 naming an UNNAMED change")
            print("  under --allow-root-change means the diff is bigger than what was declared — name")
            print("  it or revert it, do not widen the flag to make the message go away. A FAIL on 9")
            print("  means a mockup token no longer matches any AppColors.kt constant of its theme;")
            print("  that is either new drift (see B19) or a new legitimate exception that")
            print("  TOKEN_PARITY_EXCEPTIONS does not know about yet — the fix is never to delete the")
            print("  token from the check, only to explain it in that dict, same as B19 itself.")

    return 1 if rep.failed else 0


if __name__ == "__main__":
    sys.exit(main())
