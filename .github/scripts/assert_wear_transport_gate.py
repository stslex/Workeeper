#!/usr/bin/env python3
r"""Un-suppressible source gate for the Wear transport privacy blocker.

The privacy gate on sending workout payloads (wear-phase-1-active-workout-tile.md
section 6) is enforced in two layers. Detekt is the fast one: `ForbiddenImport`
covers imports, `WearDataLayerApiRule` covers every spelling that carries no
import, and both run in the pre-commit hook. Neither can be the whole gate,
because both are ordinary detekt rules and detekt honours `@Suppress` by rule id
and by rule-set id, and a rule cannot police its own suppression:
`@Suppress("WearDataLayerApiRule")` silences the very finding that would have
reported the annotation.

This script is that second layer, and it is deliberately not a detekt rule:

1. No tracked Kotlin source may contain the Data Layer package name at all,
   after the text is canonicalised the way kotlinc reads it: comments reduced to
   one separating space, trivia around the dots of a qualified name collapsed,
   string literals constant-folded, same-file constant variables inlined, and
   literal escapes resolved. Text matching, not AST matching, so it also covers
   the reflective route (`Class.forName("com.google.android.gms.wearable...")`)
   that no AST visitor can see. That route needs no build-file edit in
   `app/wear` or `feature/wear-bridge`, which already declare
   `play-services-wearable`.

   The line this draws is the compiler's own: the gate sees what the compiler can
   constant-fold. A name assembled at RUNTIME -- from a char array, a decode, a
   resource -- is invisible to this and to any other static gate, and no list of
   patterns closes that class. Code review is the control there.

2. No tracked source may suppress the gate, by rule id, by rule-set id, by
   detekt's prefixed spellings, or by a blanket `ALL`.

3. No tracked `.java` file may exist at all.

KOTLIN-ONLY BY DECISION, which is what check 3 is for. This repository has no
Java sources, so a Java canonicaliser here -- `\uXXXX` decoding, text blocks,
JLS indentation ordering -- was code no input could reach: the same unreachable
class this gate exists to catch, produced by the gate itself. Rejecting `.java`
outright turns the absence of Java from a coincidence into an enforced
invariant, and makes restoring that half a deliberate, reviewed act rather than
something a new file quietly needs.

The single exemption from checks 1 and 2 is `lint-rules/`, where the gate is
defined and tested: the rule names the package it bans, and its fixtures spell
out the violations it must catch. Nothing there is a transport call site.

Run from the repository root:

    python3 .github/scripts/assert_wear_transport_gate.py
    python3 .github/scripts/assert_wear_transport_gate.py --self-test
"""

import re
import subprocess
import sys
from pathlib import Path

FORBIDDEN_PACKAGE = "com.google.android.gms.wearable"

# Matched on a package boundary, so `...gms.wearablefake` is not a hit. Only source files are
# scanned, which is why this script may spell the package it bans.
FORBIDDEN_REFERENCE = re.compile(re.escape(FORBIDDEN_PACKAGE) + r"(?![A-Za-z0-9_])")

SOURCE_GLOBS = ("*.kt", "*.kts")

# Check 3. Detekt does not read Java at all, so a `.java` call site would be invisible to both
# detekt layers, and AGP compiles it in the same variants. Rather than carry a Java canonicaliser
# that no input in this repository can reach, the file type itself is refused.
JAVA_GLOBS = ("*.java",)

# The gate defines and tests itself here; every other tracked source is a call site.
EXEMPT_PREFIXES = ("lint-rules/",)

# Every argument that would silence either half of the gate. Rule ids, the rule-set ids that
# contain them, detekt's `detekt:`/`detekt.` prefixed spellings, and the blanket form.
SUPPRESSION_TARGETS = (
    "WearDataLayerApiRule",
    "ForbiddenImport",
    "mvi-architecture",
    "style",
    "ALL",
    "all",
)

_TARGETS = "|".join(
    re.escape(target) for target in SUPPRESSION_TARGETS
)
SUPPRESS_CALL = re.compile(r"@(?:file:)?Suppress\s*\(([^)]*)\)", re.DOTALL)
SUPPRESSED_TARGET = re.compile(rf'"(?:detekt[:.])?(?:{_TARGETS})"')

# A literal is either form: `"..."` or a raw string. Both concatenate into the same constant, so
# both have to be foldable.
_LITERAL = r'(?:"""(?:.|\n)*?"""|"(?:[^"\\\n]|\\.)*")'
ANY_LITERAL = re.compile(_LITERAL)
ADJACENT_LITERALS = re.compile(rf"({_LITERAL})\s*\+\s*({_LITERAL})")

# `("a" + "b")` folds to one constant too, and parentheses are not a barrier to the compiler.
# The lookbehind keeps a call's argument list intact: `f("a")` must not become `f"a"`.
PARENTHESISED_LITERAL = re.compile(rf"(?<![A-Za-z0-9_)\]])\(\s*({_LITERAL})\s*\)")

# A folded body is re-emitted as an ordinary literal, so a quote, a backslash or a newline carried
# in from a raw-string body has to go. A space is the safe replacement: it cannot occur inside the
# package name, so it can only prevent a match, never invent one -- and a newline really is in the
# constant, which is why a name split across a raw string's lines does not name a class.
BODY_BREAKERS = re.compile(r'["\\\n]')

# Trivia between the tokens of a qualified name, so `com. /*gap*/ google` and a name split across
# lines are the same name to the compiler and must be the same name here.
SPACES_AROUND_DOT = re.compile(r"[ \t]*\.[ \t]*")
WHITESPACE_AROUND_DOT = re.compile(r"\s*\.\s*")

# Escapes RESOLVED BY THE COMPILER inside a string literal. Only escapes that can produce a letter
# or a dot matter, so whitespace escapes are left alone, and a decode to `"` or `\` is refused so
# it cannot forge a literal boundary. Kotlin has no octal escapes.
STRING_UNICODE_ESCAPE = re.compile(r"\\u+([0-9a-fA-F]{4})")

# A constant variable whose initialiser is a single literal. The compiler inlines these into the
# constant it builds, so `PREFIX + SUFFIX` is one constant and must be one match here.
CONSTANT_DECLARATION = re.compile(
    r"\b(?:const\s+val|val|var)\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*String\s*)?=\s*"
    rf"({_LITERAL})"
)

# Substitution only ever replaces identifiers with literals, so each round strictly reduces the
# identifiers left to resolve and the loop terminates on its own. The bound is a backstop against a
# pathological file, NOT a depth limit: reaching it reports the file rather than returning quietly,
# because a gate that gives up silently is the failure this whole layer exists to prevent.
MAX_CONSTANT_ROUNDS = 1000


def _decoded_char(value: int) -> str | None:
    char = chr(value)
    return None if char in '"\\' else char


def decode_string_escapes(literal: str) -> str:
    """A literal's compile-time value, for the escapes that can spell a package name."""

    def unicode_sub(match: re.Match[str]) -> str:
        return _decoded_char(int(match.group(1), 16)) or match.group(0)

    return STRING_UNICODE_ESCAPE.sub(unicode_sub, literal)


def _literal_body(literal: str) -> str:
    return literal[3:-3] if literal.startswith('"""') else literal[1:-1]


def _joined(first: str, second: str) -> str:
    body = _literal_body(first) + _literal_body(second)
    return '"' + BODY_BREAKERS.sub(" ", body) + '"'


class ConstantResolutionExhausted(RuntimeError):
    """Raised instead of returning a half-resolved file: this gate fails closed, never quiet."""


def outside_literals(text: str, transform) -> str:
    """Applies [transform] to the code between string literals, leaving the literals untouched.

    The distinction is not cosmetic. Between tokens a newline is trivia, so a qualified name split
    across lines is one name; INSIDE a literal the same newline is data, so a package name broken
    across the lines of a raw string does not name a class and must not be joined into one.
    """
    pieces = []
    cursor = 0
    for literal in ANY_LITERAL.finditer(text):
        pieces.append(transform(text[cursor:literal.start()]))
        pieces.append(literal.group(0))
        cursor = literal.end()
    pieces.append(transform(text[cursor:]))
    return "".join(pieces)


def reduce_constants(text: str) -> str:
    """Alternates folding and substitution until neither changes anything.

    Both directions feed each other, which is why one pass of each is not enough: folding turns
    `PREFIX = "com.google." + "android.gms."` into a single-literal declaration the table can read,
    and substitution turns `PREFIX + SUFFIX` into two adjacent literals that fold. Running them
    once, in either order, truncates one of the two.
    """
    for _ in range(MAX_CONSTANT_ROUNDS):
        reduced = substitute_constants(fold_literals(text))
        if reduced == text:
            return text
        text = reduced
    raise ConstantResolutionExhausted(
        f"constant reduction did not settle in {MAX_CONSTANT_ROUNDS} rounds"
    )


def substitute_constants(text: str) -> str:
    """Inlines same-file constant variables, which is what the compiler does before folding.

    Substitution happens OUTSIDE string literals only: replacing an identifier that merely appears
    inside some unrelated literal would invent a constant the compiler never builds, and a false
    positive in a blocking gate is worse than a miss.

    The boundary is the file. A constant imported from another file is not resolved here -- see the
    limit recorded in documentation/lint-rules.md.
    """
    for _ in range(MAX_CONSTANT_ROUNDS):
        # Recomputed every round, not once: substituting `HEAD` is what turns `PREFIX = HEAD` into
        # a literal-backed constant, and a table collected before that never learns about `PREFIX`.
        constants = dict(CONSTANT_DECLARATION.findall(text))
        if not constants:
            return text
        names = re.compile(r"\b(" + "|".join(re.escape(name) for name in constants) + r")\b")
        substituted = outside_literals(
            text,
            lambda code: names.sub(lambda m: constants[m.group(1)], code),
        )
        if substituted == text:
            return text
        text = substituted
    raise ConstantResolutionExhausted(
        f"constant substitution did not settle in {MAX_CONSTANT_ROUNDS} rounds"
    )


def fold_literals(text: str) -> str:
    """Constant-folds string literals the way the compiler does, to a fixed point.

    Two rewrites, alternated until nothing changes, which is what lets arbitrary nesting collapse:
    adjacent literals join, and a parenthesised lone literal loses its parentheses. The paren rule
    refuses a `(` that follows an identifier or a closing bracket, so a call's argument list is
    never unwrapped and `f("a") + ("b")` cannot be folded into a constant the compiler would not
    fold either.
    """
    while True:
        folded = ADJACENT_LITERALS.sub(lambda m: _joined(m.group(1), m.group(2)), text)
        folded = PARENTHESISED_LITERAL.sub(lambda m: m.group(1), folded)
        if folded == text:
            return text
        text = folded


def strip_comments(text: str) -> str:
    """Comments become one space -- what a tokenizer does with them.

    One space, not nothing: `a/*x*/b` is two tokens to the compiler and must not be joined into
    one. String and character literals are walked rather than skipped, so a `//` inside a URL
    literal does not eat the rest of its line, and newlines are preserved so reported line numbers
    stay true. A raw string processes no escapes, so it is emitted untouched.
    """
    out: list[str] = []
    index = 0
    end = len(text)
    while index < end:
        char = text[index]
        if text.startswith('"""', index):
            close = text.find('"""', index + 3)
            close = end if close == -1 else close + 3
            out.append(text[index:close])
            index = close
        elif char in "\"'":
            cursor = index + 1
            while cursor < end:
                if text[cursor] == "\\":
                    cursor += 2
                    continue
                if text[cursor] == char or text[cursor] == "\n":
                    cursor += 1
                    break
                cursor += 1
            out.append(decode_string_escapes(text[index:cursor]))
            index = cursor
        elif text.startswith("//", index):
            close = text.find("\n", index)
            close = end if close == -1 else close
            out.append(" ")
            index = close
        elif text.startswith("/*", index):
            close = text.find("*/", index + 2)
            close = end if close == -1 else close + 2
            out.append(" " + "\n" * text.count("\n", index, close))
            index = close
        else:
            out.append(char)
            index += 1
    return "".join(out)


def canonical(text: str) -> str:
    """The text as kotlinc reads it: comments gone, constants and literals reduced."""
    return reduce_constants(strip_comments(text))


def _tracked(globs: tuple[str, ...]) -> list[Path]:
    """Tracked sources only: an untracked scratch file is not what ships."""
    out = subprocess.run(
        ["git", "ls-files", "-z", *globs],
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    return [Path(name) for name in out.split("\0") if name]


def is_exempt(path: Path) -> bool:
    return str(path).startswith(EXEMPT_PREFIXES)


def java_source_violations() -> list[str]:
    """Any tracked Java file, anywhere, including under the gate's own exemption.

    Not a content check: this scanner canonicalises Kotlin only, so a `.java` file is a source it
    cannot read, in a language detekt cannot read either. Refusing the file type is what keeps the
    absence of Java an invariant instead of a coincidence.
    """
    return [
        f"{path}: tracked Java source; this gate canonicalises Kotlin only"
        for path in _tracked(JAVA_GLOBS)
    ]


def package_violations(path: Path, text: str) -> list[str]:
    """[text] must already be [canonical]."""
    spaced = outside_literals(text, lambda code: SPACES_AROUND_DOT.sub(".", code))
    violations = [
        f"{path}:{number}: names {FORBIDDEN_PACKAGE}"
        for number, line in enumerate(spaced.splitlines(), start=1)
        if FORBIDDEN_REFERENCE.search(line)
    ]
    if violations:
        return violations
    # A qualified name split across lines is one name to the compiler. Collapsing newlines too
    # would move every line number after it, so this second pass reports the file instead.
    wrapped = outside_literals(text, lambda code: WHITESPACE_AROUND_DOT.sub(".", code))
    if FORBIDDEN_REFERENCE.search(wrapped):
        return [f"{path}: names {FORBIDDEN_PACKAGE}, split across lines"]
    return violations


def suppression_violations(path: Path, text: str) -> list[str]:
    violations = []
    for match in SUPPRESS_CALL.finditer(text):
        silenced = SUPPRESSED_TARGET.findall(match.group(1))
        if not silenced:
            continue
        number = text.count("\n", 0, match.start()) + 1
        violations.append(
            f"{path}:{number}: @Suppress({match.group(1).strip()}) silences the Wear transport gate"
        )
    return violations


def scan(paths: list[Path]) -> list[str]:
    violations: list[str] = []
    for path in paths:
        if is_exempt(path):
            continue
        raw = path.read_text(encoding="utf-8", errors="replace")
        try:
            text = canonical(raw)
        except ConstantResolutionExhausted as exhausted:
            violations.append(f"{path}: {exhausted}; cannot prove this file clean")
            continue
        violations += package_violations(path, text)
        violations += suppression_violations(path, text)
    return violations


def self_test() -> int:
    """A gate never shown to fire is not a gate. Both anchors, on synthetic content.

    The tracked-Java check is absent here by nature: it asks the repository a question, not a file,
    so it is proven by adding and removing a real tracked `.java` file instead.
    """
    cases = [
        ("clean file", "package io.github.stslex.workeeper.wear\n\nval x = 1\n", 0),
        # Trivia between the tokens of a qualified name: legal, one name to the compiler, and
        # invisible to a contiguous-text match.
        (
            "comment inside the name",
            "val c = com. /*gap*/ google.android.gms.wearable.Wearable\n",
            1,
        ),
        (
            "name split across lines",
            "val c = com.\n    google.android.gms.wearable.Wearable\n",
            1,
        ),
        # A `//` inside a string literal is not a comment, so the rest of its line still counts.
        (
            "string literal is not a comment",
            'val u = "https://example.com"; val c = com.google.android.gms.wearable.Wearable\n',
            1,
        ),
        # A commented-out reference is not a call site. Comments are trivia to the compiler and to
        # this gate alike.
        ("commented-out reference", "// com.google.android.gms.wearable.Wearable\n", 0),
        # The compiler folds adjacent literals into one constant before anything sees them.
        (
            "split reflective literal",
            'val c = Class.forName("com.google.android.gms." + "wearable.Wearable")\n',
            1,
        ),
        (
            "three-way split literal",
            'val c = Class.forName("com.google." + "android.gms." + "wearable.Wearable")\n',
            1,
        ),
        (
            "split literal across lines",
            'val c = Class.forName(\n    "com.google.android.gms."\n        + "wearable.Wearable",\n)\n',
            1,
        ),
        # Escapes the compiler resolves inside the literal itself.
        (
            "unicode escape in a literal",
            'val c = Class.forName("com.google.android.gms.wea\\u0072able.Wearable")\n',
            1,
        ),
        (
            "escape survives folding",
            'val c = Class.forName("com.google.android.gms.wea" + "\\u0072able.Wearable")\n',
            1,
        ),
        # Parentheses do not stop the compiler folding a constant expression.
        (
            "parenthesised fold",
            'val c = Class.forName("com.google.android.gms." + ("wearable." + "Wearable"))\n',
            1,
        ),
        (
            "nested parenthesised fold",
            'val c = Class.forName(("com.google." + ("android.gms." + "wearable.Wearable")))\n',
            1,
        ),
        # ...but a call result is not a constant, so its parentheses must survive: unwrapping them
        # would fold something the compiler does not.
        (
            "call argument list is not unwrapped",
            'val c = f("com.google.android.gms.") + ("wearable.Wearable")\n',
            0,
        ),
        # A raw string processes no escapes, so these bytes name no package.
        (
            "raw string escapes nothing",
            'val c = """com.google.android.gms.wea\\u0072able.Wearable"""\n',
            0,
        ),
        (
            "raw string concatenation",
            'val c = Class.forName("""com.google.android.gms.""" + "wearable.Wearable")\n',
            1,
        ),
        # A newline really is part of a raw string's constant, so a name broken across its lines
        # does not name a class -- and must not be folded into one.
        (
            "raw string newline is part of the constant",
            'val c = """com.google.android.gms.\nwearable.Wearable"""\n',
            0,
        ),
        # Constant variables are inlined by the compiler before the fold.
        (
            "const val constants",
            'const val PREFIX = "com.google.android.gms."\n'
            'const val SUFFIX = "wearable.Wearable"\n'
            'val c = Class.forName(PREFIX + SUFFIX)\n',
            1,
        ),
        (
            "constant defined through another constant",
            'const val HEAD = "com.google."\n'
            'const val TAIL = "android.gms.wearable.Wearable"\n'
            'val c = Class.forName(HEAD + TAIL)\n',
            1,
        ),
        # An alias needs the constant table rebuilt mid-fixed-point, not collected once.
        (
            "constant alias chain",
            'const val HEAD = "com.google.android.gms."\n'
            'const val PREFIX = HEAD\n'
            'val c = Class.forName(PREFIX + "wearable.Wearable")\n',
            1,
        ),
        # An alias chain deeper than any fixed round count: the fixed point has to be a fixed point.
        (
            "deep constant alias chain",
            'const val A = "com.google.android.gms."\n'
            'const val B = A\nconst val C = B\nconst val D = C\n'
            'const val E = D\nconst val F = E\nconst val G = F\n'
            'val c = Class.forName(G + "wearable.Wearable")\n',
            1,
        ),
        # A constant whose initialiser is itself a concatenation must enter the table whole.
        (
            "constant with a folded initialiser",
            'const val PREFIX = "com.google." + "android.gms."\n'
            'val c = Class.forName(PREFIX + "wearable.Wearable")\n',
            1,
        ),
        # An identifier inside an unrelated literal must not be substituted: that would invent a
        # constant the compiler never builds, and a false positive here fails CI.
        (
            "identifier inside a literal is not substituted",
            'const val WORD = "wearable.Wearable"\n'
            'val doc = "com.google.android.gms.WORD"\n',
            0,
        ),
        # The documented limit, pinned so it stays a decision rather than an oversight: a name
        # assembled at RUNTIME is not a constant, and no static gate can see it.
        (
            "runtime-assembled name is the documented limit",
            'val c = Class.forName("com.google.android.gms." + suffix)\n',
            0,
        ),
        (
            "reflective load",
            f'val c = Class.forName("{FORBIDDEN_PACKAGE}.Wearable")\n',
            1,
        ),
        ("qualified call", f"val c = {FORBIDDEN_PACKAGE}.Wearable.get()\n", 1),
        ("package directive", f"package {FORBIDDEN_PACKAGE}\n", 1),
        ("rule suppression", '@file:Suppress("WearDataLayerApiRule")\n', 1),
        ("rule-set suppression", '@Suppress("style")\nval x = 1\n', 1),
        ("prefixed suppression", '@Suppress("detekt:ForbiddenImport")\nval x = 1\n', 1),
        ("blanket suppression", '@file:Suppress("ALL")\n', 1),
        ("unrelated suppression", '@Suppress("TooManyFunctions")\nval x = 1\n', 0),
        ("near-miss package", "package com.google.android.gms.wearablefake\n", 0),
    ]
    failures = 0
    for name, content, expected in cases:
        path = Path("synthetic.kt")
        text = canonical(content)
        found = len(package_violations(path, text) + suppression_violations(path, text))
        verdict = "ok" if found == expected else "MISMATCH"
        if found != expected:
            failures += 1
        print(f"  [{verdict}] {name}: {found} violation(s), expected {expected}")
    if failures:
        print(f"\nself-test FAILED: {failures} case(s) disagree")
        return 1
    print(f"\nself-test passed: {len(cases)} cases, both anchors exercised")
    return 0


def main() -> int:
    if "--self-test" in sys.argv:
        return self_test()

    paths = _tracked(SOURCE_GLOBS)
    scanned = [path for path in paths if not is_exempt(path)]
    violations = java_source_violations() + scan(paths)

    print(f"wear transport gate: {len(scanned)} tracked Kotlin file(s) scanned, "
          f"{len(paths) - len(scanned)} exempt under {', '.join(EXEMPT_PREFIXES)}")
    if not violations:
        print(f"no reference to {FORBIDDEN_PACKAGE}, nothing suppresses the gate, no Java sources")
        return 0

    print(f"\n{len(violations)} violation(s):\n")
    for violation in violations:
        print(f"  {violation}")
    print(
        "\nSending any workout payload over the Wearable Data Layer is blocked on the privacy\n"
        "review in documentation/feature-specs/wear-phase-1-active-workout-tile.md section 6.\n"
        "This gate is not a detekt rule precisely so that it cannot be suppressed from source."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
