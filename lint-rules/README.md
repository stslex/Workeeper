# Lint Rules Module

Centralized linting rules management for Workeeper project.

## Structure

```
lint-rules/
├── src/main/kotlin/
│   └── io/github/stslex/workeeper/lint/
│       └── MviArchitectureRules.kt          # Custom MVI architecture rules
├── src/main/resources/
│   ├── lint-suppressions.xml               # XML configuration for suppressions
│   └── META-INF/services/
│       └── io.gitlab.arturbosch.detekt.api.RuleSetProvider
├── detekt.yml                              # Detekt configuration
├── lint.xml                                # Android Lint configuration
├── detekt-baseline.xml                     # Single detekt baseline for all modules
├── lint-baseline.xml                       # Single lint baseline for all modules
├── baseline-manager.sh                     # Baseline management script
└── README.md                               # This file
```

## Configuration Files

### `lint-suppressions.xml`

Main suppressions file organized by categories:

- **Design & UI**: icons, typography, accessibility
- **Android Framework**: Android-specific issues
- **Test-specific**: suppressions only for test files
- **MVI Architecture**: suppressions for architectural patterns
- **Temporary**: temporary suppressions with justification

### `detekt.yml` & `lint.xml`

Core configuration files for linting tools, now centralized in lint-rules module.

## Custom Rules

### MVI Architecture Rules

1. **MviStateImmutabilityRule** - checks state immutability
2. **MviActionNamingRule** - action naming conventions
3. **MviEventNamingRule** - event naming conventions
4. **MviHandlerNamingRule** - handler naming conventions
5. **MviStoreExtensionRule** - validates BaseStore inheritance
6. **KoinScopeRule** - Koin DI rules (disabled - legacy)
7. **HiltScopeRule** - Hilt dependency injection scope validation
8. **ComposableStateRule** - Compose component checks

## Usage

### Adding New Suppressions

1. **Global suppression** (for entire project):

```xml

<issue id="NewIssueId" severity="ignore" />
```

2. **Pattern-based suppression** (for specific files):

```xml

<issue id="IssueId">
    <ignore path="**/specific/pattern/**" />
    <ignore regexp=".*SpecificFile\.kt" />
</issue>
```

### Temporary Suppressions

All temporary suppressions should include justification:

```xml

<issue id="TemporaryIssue">
    <ignore path="**/legacy/**" comment="Will be refactored in v2.0" />
</issue>
```

## Integration

Module is automatically applied via:

- Root `build.gradle.kts`
- `LintConventionPlugin` in build-logic
- `settings.gradle.kts` module inclusion

## Commands

### Basic Linting Commands

```bash
# Build module
./gradlew :lint-rules:build

# Test rules
./gradlew :lint-rules:test

# Apply rules
./gradlew detekt lintDebug
```

### Baseline Management (Simplified)

```bash
# Show baseline files (2 files total)
./lint-rules/baseline-manager.sh list

# Update all baseline files
./lint-rules/baseline-manager.sh update

# Update specific type
./lint-rules/baseline-manager.sh update-lint
./lint-rules/baseline-manager.sh update-detekt

# Show statistics
./lint-rules/baseline-manager.sh stats

# Help
./lint-rules/baseline-manager.sh --help
```

## Simplified Structure

The baseline system has been simplified:

- **Single lint baseline**: `lint-baseline.xml` for all modules
- **Single detekt baseline**: `detekt-baseline.xml` for all modules
- **No per-module files**: easier to manage and maintain