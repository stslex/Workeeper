import subprocess, sys, pathlib
ROOT = pathlib.Path("/home/stslex/AndroidStudioProjects/Workeeper")

# Revert by CONTENT SNAPSHOT, never by `git checkout -- .`.
#
# The git-relative revert destroyed uncommitted work here (fifth time in this arc). The mutation
# harness has no business knowing what HEAD is: it changes one file, and it must put back exactly
# the bytes it found, whether or not they were ever committed.

def case(name, rel, old, new, task):
    p = ROOT / rel
    before = p.read_text(encoding="utf-8")
    n = before.count(old)
    if n != 1:
        print(f"\n=== {name} -> *** SKIPPED — anchor matched {n} times, expected 1 ***")
        sys.stdout.flush()
        return
    try:
        p.write_text(before.replace(old, new), encoding="utf-8")
        r = subprocess.run(["./gradlew", task], cwd=ROOT, capture_output=True, text=True)
        out = r.stdout + r.stderr
    finally:
        p.write_text(before, encoding="utf-8")
        assert p.read_text(encoding="utf-8") == before, f"RESTORE FAILED for {rel}"

    compile_errs = [l.strip() for l in out.splitlines() if l.startswith("e: ") or "error:" in l]
    fails = [l.strip() for l in out.splitlines()
             if "FAILED" in l and "> Task" not in l and "BUILD" not in l]
    if compile_errs:
        verdict, detail = "*** INVALID — DID NOT COMPILE, proves nothing ***", compile_errs[:4]
    elif r.returncode != 0 and fails:
        verdict, detail = f"RED ({len(fails)} test(s))", fails[:8]
    elif r.returncode != 0:
        verdict = "RED (no named test — check)"
        detail = [l.strip() for l in out.splitlines() if "FAIL" in l or "Visual gate" in l][:6]
    else:
        verdict, detail = "*** GREEN — GATE HOLE ***", []
    print(f"\n=== {name} -> {verdict}")
    for d in detail:
        print("     ", d)
    sys.stdout.flush()
