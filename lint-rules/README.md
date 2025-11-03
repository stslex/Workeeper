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

All custom rules are implemented in `src/main/kotlin/io/github/stslex/workeeper/lint_rules/`:

1. **MviStateImmutabilityRule** - Ensures State classes are immutable data classes with `val` properties
2. **MviActionNamingRule** - Validates action naming conventions (Click*, Load*, Save*, Update*, etc.)
3. **MviEventNamingRule** - Validates event naming conventions (*Success, *Error, *Completed, Haptic, Snackbar, etc.)
4. **MviHandlerNamingRule** - Ensures handler classes follow proper naming patterns (*Handler)
5. **MviStoreExtensionRule** - Validates that Store classes extend BaseStore
6. **MviHandlerConstructorRule** - Validates handler constructor parameters and patterns
7. **MviStoreStateRule** - Validates store state management and state class usage
8. **HiltScopeRule** - Validates Hilt dependency injection annotations (@ViewModelScoped, @Singleton)
9. **ComposableStateRule** - Validates Composable component state handling
10. **KoinScopeRule** - Legacy Koin DI rules (disabled, kept for reference)

### Android Lint Configuration

The `lint.xml` file includes comprehensive rules organized by category:

#### Security Rules (Error Severity)
- SSL/TLS security checks
- File permission validation
- Dynamic code loading detection
- JavaScript interface safety
- Vulnerable dependency detection

#### Performance Rules (Error Severity)
- Unused resource detection
- Layout optimization
- ViewHolder pattern enforcement
- Wakelock usage validation

#### Code Quality Rules (Error Severity)
- ID consistency checks
- String format validation
- Resource consistency
- Gradle dependency management

See [LINT_IMPROVEMENTS.md](LINT_IMPROVEMENTS.md) for complete documentation of all Android Lint rules.

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

## Documentation

- **[LINT_IMPROVEMENTS.md](LINT_IMPROVEMENTS.md)** - Detailed documentation of all Android Lint rules and their categories
- **[SUMMARY.md](SUMMARY.md)** - Architecture overview and solution benefits
- **[README.md](README.md)** - This file, usage instructions and module structure