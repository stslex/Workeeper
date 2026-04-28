[![Android CI/CD](https://github.com/stslex/Workeeper/actions/workflows/android_build_unified.yml/badge.svg)](https://github.com/stslex/Workeeper/actions/workflows/android_build_unified.yml)
[![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

<div align="center">

# Workeeper

**The Ultimate Strength Training Companion**

*Track workouts, monitor progress, achieve your fitness goals.*

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=io.github.stslex.workeeper">
    <img src="https://img.shields.io/badge/Google_Play-414141?style=for-the-badge&logo=google-play&logoColor=white" alt="Get it on Google Play" height="60"/>
  </a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://github.com/stslex/Workeeper/releases">
    <img src="https://img.shields.io/badge/GitHub_Releases-181717?style=for-the-badge&logo=github&logoColor=white" alt="Download from GitHub" height="60"/>
  </a>
</p>

</div>

Workeeper is a modular Android application written in Kotlin and Jetpack Compose. It logs and
tracks strength workouts: trainings, exercises, sets, and progress charts. Data is stored
locally with Room.

## Highlights

- Modular structure: `app/`, `core/*`, `feature/*`, `build-logic/`, `lint-rules/`.
- MVI with State / Action / Event / Handler — see
  [documentation/architecture.md](documentation/architecture.md).
- Hilt dependency injection with `@HiltViewModel` + assisted factories per feature.
- Room database with exported schemas under `core/database/schemas/`.
- Compose Material 3 UI with shared element transitions across the navigation graph.
- Custom Detekt rules that enforce the MVI contract — see
  [documentation/lint-rules.md](documentation/lint-rules.md).

## Requirements

- Android Studio (latest stable).
- JDK 21.
- Android SDK with Build Tools matching the pinned `compileSdk` in
  `gradle/libs.versions.toml`.
- A device or emulator targeting `minSdk` 28 or newer.

For release builds, place a `keystore.properties` at the repo root and per-variant
`google-services.json` files at `app/dev/` and `app/store/`. Both are intentionally untracked.

## Quickstart

```bash
git clone https://github.com/stslex/Workeeper.git
cd Workeeper

# Install the dev variant
./gradlew :app:dev:installDebug

# Build the release variant
./gradlew :app:store:assembleRelease

# Unit tests
./gradlew testDebugUnitTest

# Static analysis
./gradlew detekt lintDebug
```

UI tests live under each module's `src/androidTest/...` and are categorized with `@Smoke` /
`@Regression`. Run them locally with `./gradlew connectedDebugAndroidTest`. They are optional
in CI; see [documentation/testing.md](documentation/testing.md) and
[documentation/ci-cd.md](documentation/ci-cd.md).

## Documentation

| Topic | Where |
|---|---|
| Modules, MVI contract, DI, data flow, navigation, build conventions | [documentation/architecture.md](documentation/architecture.md) |
| Per-feature behaviour and MVI surface | [documentation/features.md](documentation/features.md) |
| Unit tests, UI tests, smoke/regression strategy, gradle commands | [documentation/testing.md](documentation/testing.md) |
| GitHub Actions workflows, release pipeline, secrets, branch model | [documentation/ci-cd.md](documentation/ci-cd.md) |
| Custom Detekt rules, Android Lint config, suppressions, baselines | [documentation/lint-rules.md](documentation/lint-rules.md) |
| Contributing workflow and commit format | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Code of conduct | [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) |
| Release notes | [RELEASE_NOTES.md](RELEASE_NOTES.md) |
| Privacy policy (GitHub Pages) | [docs/index.md](docs/index.md) |

## Links

- [Google Play Store](https://play.google.com/store/apps/details?id=io.github.stslex.workeeper)
- [GitHub Releases](https://github.com/stslex/Workeeper/releases)
- [Issue tracker](https://github.com/stslex/Workeeper/issues)

## License

This project is licensed under the GNU General Public License v3.0. See the
[LICENSE](LICENSE) file for the full text.
