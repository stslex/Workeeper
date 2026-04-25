# lint-rules

Centralized linting configuration and the custom Detekt rule set for Workeeper. This file is a
pointer; see [`documentation/lint-rules.md`](../documentation/lint-rules.md) for the canonical
reference (rule catalog with examples, Android Lint configuration, suppression conventions,
baseline management, and how to add a new rule).

## Layout

```
lint-rules/
├── detekt.yml               # Detekt configuration
├── lint.xml                 # Android Lint configuration (also where suppressions live)
├── detekt-baseline.xml      # Single Detekt baseline for all modules
├── lint-baseline.xml        # Single Android Lint baseline for all modules
├── baseline-manager.sh      # Helper to list / update / clean baselines
└── src/main/
    ├── kotlin/io/github/stslex/workeeper/lint_rules/   # Custom MVI Detekt rules
    └── resources/META-INF/services/                    # RuleSetProvider SPI registration
```

The module is wired into every Android module by `LintConventionPlugin`
(`build-logic/convention/src/main/kotlin/LintConventionPlugin.kt`).

## Common commands

```bash
# Build the rule set
./gradlew :lint-rules:build

# Run the rule unit tests
./gradlew :lint-rules:test

# Run the rules against the codebase
./gradlew detekt
./gradlew lintDebug

# Manage baselines
./lint-rules/baseline-manager.sh --help
```
