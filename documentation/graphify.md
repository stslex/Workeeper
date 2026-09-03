# Graphify repository index

Graphify provides an advisory discovery index for repository-wide, multi-hop dependency and
blast-radius exploration. It complements exact searches with `rg`; it does not define the
architecture. Source code and canonical documents under `documentation/` always take precedence.
Treat `INFERRED` and `AMBIGUOUS` edges as leads only, and verify every decision-critical result
against source.

## Committed baseline

The committed baseline was generated before its repository integration and was not regenerated,
rewritten, or normalized by the integration task.

| Property | Baseline |
| --- | --- |
| Graphify | `graphifyy==0.9.50`, installed through `uv` |
| Origin | [Graphify-Labs/graphify](https://github.com/Graphify-Labs/graphify) |
| Indexed root | repository root |
| Indexed branch and commit | `dev` at `97b55d7318273e567c58b32dccbdf2919039aba2` |
| Extraction | default mode, undirected graph, semantic extraction enabled |
| Semantic backend | Codex host-agent workflow |
| Exact model | `UNVERIFIED` |
| Original unified CLI command | `UNVERIFIED` |
| `graph.json` | 28,981,793 bytes; SHA-256 `8e7967f81d611d605058cc223269b1a251fa13eddc9b43188ef5226ccd47e326` |
| `manifest.json` | 601,704 bytes; SHA-256 `c9c6387d519c93eee6c2f3593fdc966bdd98dc45701be10f75974daf12374093` |

The snapshot can become stale as source changes. Its provenance limitations are recorded rather
than inferred: the exact model and a single original CLI invocation were not recoverable.
The generated health report also records 8,324 dangling-endpoint edges, 16 self-loops, and
1,821 undirected edge collapses before export. These warnings reinforce the advisory-only policy;
they are not source verification.

## Read-only use

Install [`uv`](https://docs.astral.sh/uv/) as the only bootstrap prerequisite. The intentionally
read-only repository wrapper accepts only `query`, `path`, and `explain` plus global help and
version flags. It runs the exact baseline version without creating a root Python project, virtual
environment, or lockfile:

```bash
./.github/scripts/graphify query "How is AppScopeLifetime used?"
./.github/scripts/graphify path "AppCoroutineScopeImpl" "AppScopeLifetime"
./.github/scripts/graphify explain "AppCoroutineScopeImpl"
```

Use `rg` when the task is an exact text, symbol, or file lookup. Graphify traversal is discovery
evidence only; verify its source paths and relationships directly in the repository.

## Artifact policy

Only `graphify-out/graph.json` and `graphify-out/manifest.json` are tracked. Reports,
visualizations, caches, cost data, converted content, logs, lock files, agent statistics,
transcripts, memory, reflections, and learning overlays stay local and ignored. Never stage an
additional Graphify output as part of feature work.

Automatic Git hooks are intentionally disabled: an ordinary commit must not trigger an expensive
or nondeterministic generated-index rewrite. Graph freshness is not a CI gate because refresh
determinism and Linux/macOS reproducibility remain `UNVERIFIED`; source correctness must not depend
on generated-index freshness.

## Future maintenance

Agents consume the committed graph without rebuilding it and verify important results against
source. The current provenance does not recover the exact semantic model or unified command, so a
refresh cannot proceed merely from this document. Before any refresh, a separately approved
maintenance specification must establish:

- the exact command and flags;
- the exact backend and model;
- the input scope and ignore policy;
- the expected graph format;
- determinism controls;
- Linux/macOS portability controls;
- accepted graph-health deltas.

That maintenance task may use an explicitly reviewed pinned invocation, but it must not silently
broaden the ordinary read-only wrapper. Until the specification exists, the committed snapshot may
be queried but not refreshed. Feature implementation must not modify the graph, and generated graph
changes remain separate from feature logic.
