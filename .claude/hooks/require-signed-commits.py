#!/usr/bin/env python3
"""PreToolUse guard: refuse Claude-issued Git commands that could create an unsigned commit.

The repository ruleset requires verified signatures, so an unsigned commit cannot merge and can
only be undone by a maintainer bypass or a history rewrite. This hook moves that failure from
"discovered at merge time by someone else" to "refused at the moment it is typed".

It is the Claude-local guard only. It cannot inspect the resulting object, and it is not a
substitute for the GitHub ruleset or for the post-push `verification.verified` check in the
`signed-commits` skill — GitHub remains authoritative.

Contract: reads a PreToolUse payload on stdin, exits 0 to allow and 2 to block (with the reason
on stderr). It fails CLOSED: anything commit-producing that cannot be classified confidently is
blocked, and any internal exception blocks rather than falling open. Standard library only.
"""

import json
import re
import shlex
import subprocess
import sys

# Subcommands that can write a commit object, mapped to the flag that signs them.
SIGNING_FLAG = {
    "commit": ("-S", "--gpg-sign"),
    "commit-tree": ("-S",),
    "merge": ("-S", "--gpg-sign"),
    "cherry-pick": ("-S", "--gpg-sign"),
    "revert": ("-S", "--gpg-sign"),
    "rebase": ("-S", "--gpg-sign"),
    "am": ("-S", "--gpg-sign"),
    "pull": ("-S", "--gpg-sign"),
}

# Read-only porcelain/plumbing: never writes a commit, so it is always allowed.
READ_ONLY = {
    "status", "log", "show", "diff", "cat-file", "rev-parse", "rev-list", "ls-files",
    "ls-remote", "for-each-ref", "branch", "remote", "fetch", "describe", "blame",
    "shortlog", "reflog", "verify-commit", "grep", "check-ignore", "merge-base",
    "symbolic-ref", "name-rev", "count-objects", "var", "help", "version",
}

# Config values that turn signing off.
FALSY = {"false", "0", "no", "off"}

# GUARD: disable-detection matches parsed argv TOKENS, never the raw command text. A commit
# message that merely names `--no-gpg-sign` is not a use of it, and scanning the raw string
# blocks honest commits — measured, by this hook rejecting the commit that introduced it.

# Shell operators that separate one command from the next.
SPLIT_TOKENS = {"&&", "||", ";", "|", "&", "\n"}


def block(message: str) -> None:
    print(f"[require-signed-commits] {message}", file=sys.stderr)
    sys.exit(2)


def local_gpgsign_enabled() -> bool:
    try:
        result = subprocess.run(
            ["git", "config", "--local", "--get", "commit.gpgsign"],
            capture_output=True, text=True, timeout=10,
        )
    except Exception:
        return False
    return result.stdout.strip().lower() in {"true", "1", "yes", "on"}


def split_commands(command: str) -> list[list[str]]:
    """Split a compound shell command into argv lists, tolerating quoting and newlines.

    A parse failure is not "no git here" — it is an unclassifiable command, so it raises and the
    caller blocks.
    """
    tokens = shlex.split(command, comments=True, posix=True)
    commands: list[list[str]] = []
    current: list[str] = []
    for token in tokens:
        if token in SPLIT_TOKENS:
            if current:
                commands.append(current)
            current = []
        else:
            current.append(token)
    if current:
        commands.append(current)
    return commands


def git_invocations(argv: list[str]) -> list[list[str]]:
    """Return the git argv (subcommand onward) for each git invocation in this argv."""
    out = []
    for index, token in enumerate(argv):
        base = token.rsplit("/", 1)[-1]
        if base == "git":
            out.append(argv[index:])
    return out


def split_git_argv(git_argv: list[str]) -> tuple[list[str], str | None, list[str]]:
    """Return (global options, subcommand, subcommand args).

    Global options that take a value (-C, -c, --git-dir, …) consume the next token so that a
    value can never be mistaken for the subcommand.
    """
    takes_value = {"-C", "-c", "--git-dir", "--work-tree", "--namespace", "--exec-path",
                   "--config-env"}
    global_opts: list[str] = []
    index = 1
    while index < len(git_argv):
        token = git_argv[index]
        if token in takes_value:
            global_opts.append(token)
            if index + 1 < len(git_argv):
                global_opts.append(git_argv[index + 1])
            index += 2
            continue
        if token.startswith("-"):
            global_opts.append(token)
            index += 1
            continue
        return global_opts, token, git_argv[index + 1:]
    return global_opts, None, []


def disables_signing(global_opts: list[str], args: list[str]) -> bool:
    """True when this invocation explicitly turns signing off, by token — never by raw text."""
    if "--no-gpg-sign" in args:
        return True
    index = 0
    while index < len(global_opts):
        token = global_opts[index]
        if token == "-c" and index + 1 < len(global_opts):
            setting, index = global_opts[index + 1], index + 2
        elif token.startswith("-c") and len(token) > 2:
            setting, index = token[2:], index + 1
        else:
            index += 1
            continue
        if "=" in setting:
            key, value = setting.split("=", 1)
            if key.strip().lower() == "commit.gpgsign" and value.strip().lower() in FALSY:
                return True
    return False


def has_signing_flag(args: list[str], accepted: tuple[str, ...]) -> bool:
    for arg in args:
        if arg in accepted:
            return True
        # --gpg-sign=<key> is the documented value form; -S<key> is the short form.
        for flag in accepted:
            if flag.startswith("--") and arg.startswith(flag + "="):
                return True
            if flag == "-S" and arg.startswith("-S") and len(arg) > 2:
                return True
    return False


def check_git(git_argv: list[str]) -> None:
    global_opts, subcommand, args = split_git_argv(git_argv)
    if subcommand is None:
        return  # bare `git` / `git --version`: writes nothing.
    if subcommand in READ_ONLY:
        return
    if subcommand == "config":
        # Allow turning the safety net ON; block turning it off.
        joined = " ".join(args).lower()
        if "commit.gpgsign" in joined and re.search(r"\b(false|0|no|off)\b", joined):
            block("refusing to disable commit.gpgsign; the ruleset requires verified signatures.")
        return
    if subcommand not in SIGNING_FLAG:
        return  # add/rm/mv/switch/restore/tag/... do not create commits.

    if disables_signing(global_opts, args):
        block(
            f"`git {subcommand}` disables signing (--no-gpg-sign / -c commit.gpgSign=false). "
            "Signed commits are mandatory here; drop that flag and sign with -S."
        )

    # Subcommand-specific escapes that produce no commit at all.
    if subcommand in {"cherry-pick", "revert"} and ("--no-commit" in args or "-n" in args):
        return
    if subcommand == "merge" and ("--ff-only" in args or "--abort" in args or "--continue" in args):
        return
    if subcommand in {"rebase", "cherry-pick", "revert", "am"} and (
        "--abort" in args or "--quit" in args or "--skip" in args
    ):
        return
    if subcommand == "pull":
        if "--ff-only" in args:
            return
        block(
            "`git pull` may create or rewrite commits (merge or rebase). Use "
            "`git pull --ff-only`, or fetch and integrate explicitly with a signed command."
        )

    if not local_gpgsign_enabled():
        block(
            f"repo-local commit.gpgsign is not true, so `git {subcommand}` could produce an "
            "unsigned commit. Run: git config --local commit.gpgsign true"
        )

    accepted = SIGNING_FLAG[subcommand]
    if not has_signing_flag(args, accepted):
        hint = " or ".join(accepted)
        block(
            f"`git {subcommand}` must sign explicitly ({hint}). Note -S signs; lowercase -s only "
            "adds a Signed-off-by trailer. See .claude/skills/signed-commits/SKILL.md"
        )


def main() -> None:
    raw_input_text = sys.stdin.read()
    try:
        payload = json.loads(raw_input_text) if raw_input_text.strip() else {}
    except Exception as error:
        block(f"could not parse the hook payload ({type(error).__name__}); blocking to fail closed.")

    if payload.get("tool_name") not in (None, "Bash"):
        sys.exit(0)
    command = (payload.get("tool_input") or {}).get("command")
    if not isinstance(command, str) or not command.strip():
        sys.exit(0)

    try:
        commands = split_commands(command)
    except Exception:
        # An unparseable command that mentions a commit-producing git subcommand is exactly the
        # ambiguous case this hook must not wave through.
        if re.search(r"\bgit\b", command) and re.search(
            r"\b(commit|commit-tree|merge|cherry-pick|revert|rebase|am|pull)\b", command
        ):
            block("could not parse this compound git command; blocking to fail closed.")
        sys.exit(0)

    for argv in commands:
        for git_argv in git_invocations(argv):
            check_git(git_argv)

    sys.exit(0)


if __name__ == "__main__":
    try:
        main()
    except SystemExit:
        raise
    except Exception as error:  # never fail open
        block(f"internal guard error ({type(error).__name__}); blocking to fail closed.")
