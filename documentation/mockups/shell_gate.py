#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
#
# Appearance gate for the v3 shell mockup. ONE command, run it from anywhere:
#
#     python3 documentation/mockups/shell_gate.py            # silent + exit 0 == green
#     python3 documentation/mockups/shell_gate.py -v         # the evidence table
#     python3 documentation/mockups/shell_gate.py --target f52462c7
#
# WHY THIS EXISTS. `pass2d.html` sections `#s-list` and `#s-nav` are the appearance contract for the
# chrome shared by the eight derived screens of the v3 arc — bottom bar, list row, selection mode, the
# add action, the paging tails. Nothing in CI reads HTML, so until this script existed the contract that
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
# RUN IT IN THE PR, BEFORE THE MERGE. A gate that runs after the merge certifies; it does not gate.
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

# The one variable legitimately absent from :root — nbPick() writes it per-element at runtime
# (`el.style.setProperty('--sx', ...)`). Named explicitly rather than loosening check 2 to a pattern,
# because a loosened check 2 stops seeing typos, which is the only thing it is for.
RUNTIME_VARS = {"--sx"}

BALANCED_TAGS = ("section", "div", "button", "svg", "span")

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

def check_1_root_identical(rep: Report, base_src: str, tgt_src: str) -> None:
    parts = []
    ok = True
    for sel in (":root", "body.light"):
        b, t = css_block(base_src, sel), css_block(tgt_src, sel)
        if b is None or t is None:
            ok = False
            parts.append(f"{sel}: MISSING in {'BASE' if b is None else 'TARGET'}")
            continue
        if b == t:
            parts.append(f"{sel}: identical ({len(t)} bytes)")
        else:
            ok = False
            bl = [ln.strip() for ln in b.splitlines() if ln.strip()]
            tl = [ln.strip() for ln in t.splitlines() if ln.strip()]
            diff = [f"    - {ln}" for ln in bl if ln not in tl] + [f"    + {ln}" for ln in tl if ln not in bl]
            parts.append(f"{sel}: CHANGED\n" + "\n".join(diff))
    rep.add(
        1, ":root byte-identical", ok,
        "; ".join(p.splitlines()[0] for p in parts),
        parts if not ok else [],
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


def check_3_no_new_hex(rep: Report, diff: str) -> None:
    added = [ln[1:] for ln in diff.splitlines() if ln.startswith("+") and not ln.startswith("+++")]
    pat = re.compile(r"#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})\b")
    hits = [(i, ln.strip(), m) for i, ln in enumerate(added, 1) for m in pat.findall(ln)]
    rep.add(
        3, "no new hex literal", not hits,
        f"{len(added)} added lines inspected"
        + (f", {len(hits)} hex literal(s) found" if hits else ", 0 hex literals"),
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

    var bar = document.getElementById('nb2'), ind = document.getElementById('ind2');
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
    if b["bg"] == a["bg"]:
        fails.append(f"    fill did not change: {b['bg']} before and after")
    rust = hex_to_rgb_css(p.get("tokens", {}).get("rust", ""))
    if rust and a["bg"] != rust:
        fails.append(f"    the morphed fill is {a['bg']}, but --rust resolves to {rust}.\n"
                     "    The destructive fill must come from the token, not from a literal.")
    if not (b["plus"] != "none" and a["plus"] == "none"):
        fails.append(f"    the plus glyph did not go hidden: display {b['plus']} → {a['plus']}")
    if not (b["trash"] == "none" and a["trash"] != "none"):
        fails.append(f"    the trash glyph did not go visible: display {b['trash']} → {a['trash']}")
    rep.add(
        8, "FAB morph fires", not fails,
        f"radius {b['radius']}→{a['radius']}  fill {b['bg']}→{a['bg']}  "
        f"glyphs +{b['plus']}→{a['plus']} /trash {b['trash']}→{a['trash']}  "
        f"(--rust {p.get('tokens', {}).get('rust')})",
        fails,
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
    args = ap.parse_args()

    root = repo_root()
    rep = Report()

    base_src = read_blob(root, args.base, MOCKUP)
    tgt_src = read_target(root, args.target, MOCKUP)
    diff = git(root, "diff", args.base, *( [args.target] if args.target else [] ), "--", MOCKUP)

    check_1_root_identical(rep, base_src, tgt_src)
    check_2_vars_defined(rep, tgt_src)
    check_3_no_new_hex(rep, diff)
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
            print("  branch name before believing anything else.")

    return 1 if rep.failed else 0


if __name__ == "__main__":
    sys.exit(main())
