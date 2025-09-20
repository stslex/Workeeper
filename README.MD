[![Android CI/CD](https://github.com/stslex/Workeeper/actions/workflows/android_build.yml/badge.svg)](https://github.com/stslex/Workeeper/actions/workflows/android_build.yml)

<div align="center">

# 💪 Workeeper

**The Ultimate Strength Training Companion**

*Track workouts, monitor progress, achieve your fitness goals*

---

## 📱 Download Now

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=io.github.stslex.workeeper">
    <img src="https://img.shields.io/badge/Google_Play-414141?style=for-the-badge&logo=google-play&logoColor=white" alt="Get it on Google Play" height="60"/>
  </a>
  &nbsp;&nbsp;&nbsp;
  <a href="https://github.com/stslex/Workeeper/releases">
    <img src="https://img.shields.io/badge/GitHub_Releases-181717?style=for-the-badge&logo=github&logoColor=white" alt="Download from GitHub" height="60"/>
  </a>
</p>

---

</div>

**Workeeper** is a modern, modular Android application built with Kotlin and Jetpack Compose. It's designed to help you log and track strength workouts with a clean, intuitive interface and powerful features for monitoring your fitness progress.

## ✨ Key Features

- **📊 All Trainings**: Browse and manage past and scheduled workouts
- **🏋️ Exercise Library**: Comprehensive exercise database with search and filtering
- **📝 Training Editor**: Create and customize individual training sessions
- **⚡ Exercise Editor**: Quick input for sets, reps, and weights with intuitive UI
- **📈 Progress Charts**: Visualize your strength trends and improvements over time
- **🔒 Privacy-First**: All data stored locally with Room database
- **🎨 Modern UI**: Built with Jetpack Compose and Material Design 3

## 🏗️ Architecture

This project showcases modern Android development practices with:

- **🏛️ Modular Architecture**: Clean separation between `app`, `core`, and `feature` modules
- **🔄 MVI Pattern**: Unidirectional data flow with State, Actions, and Events
- **⚡ Handler System**: Dedicated action processors (`ClickHandler`, `InputHandler`, `PagingHandler`)
- **💉 Dependency Injection**: Koin with annotation processing and feature-scoped modules
- **🧪 Comprehensive Testing**: Unit tests with JUnit 5, Robolectric, and MockK

## 📂 Project Structure

```
├── app/              # Application modules
│   ├── app/          # Shared application code
│   ├── dev/          # Development build variant
│   └── store/        # Production build variant
├── core/             # Core functionality modules
│   ├── core/         # Base utilities, logging, coroutines
│   ├── ui/           # UI components (kit, navigation, mvi)
│   ├── database/     # Room database implementation
│   ├── dataStore/    # Preferences and settings storage
│   └── exercise/     # Exercise domain logic
├── feature/          # Feature modules
│   ├── all-trainings/    # Training list and management
│   ├── all-exercises/    # Exercise library and search
│   ├── single-training/  # Individual training details
│   ├── exercise/         # Exercise editor and sets management
│   └── charts/           # Progress visualization
├── build-logic/      # Convention plugins for consistent builds
└── lint-rules/       # Custom linting rules and configuration
```

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVI (Model-View-Intent)
- **Database**: Room with SQLite
- **Dependency Injection**: Koin with KSP
- **Async**: Kotlin Coroutines and Flow
- **Navigation**: Custom navigation with shared element transitions
- **Analytics**: Firebase Analytics and Crashlytics
- **Build**: Gradle with Kotlin DSL
- **Testing**: JUnit 5, Robolectric, MockK, Compose UI Test

## 🚀 Getting Started

### Prerequisites
- Android Studio (latest stable)
- Java 21 (JDK 21)
- Android SDK with minimum API 28
- Device or emulator for testing

### Building the Project

```bash
# Clone the repository
git clone https://github.com/stslex/Workeeper.git
cd Workeeper

# Build debug version
./gradlew :app:dev:assembleDebug

# Install on connected device
./gradlew :app:dev:installDebug

# Build release version
./gradlew :app:store:assembleRelease
```

## 🧪 Testing & Quality

### Running Tests
```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew :app:dev:connectedDebugAndroidTest

# Run all tests
./gradlew test
```

### Code Quality & Linting
```bash
# Run detekt (Kotlin static analysis)
./gradlew detekt

# Run Android Lint
./gradlew lintDebug

# Auto-fix detekt issues
./gradlew detektFormat

# Run all linting
./gradlew detekt lintDebug
```

### Custom Architecture Rules
The project includes custom linting rules that enforce:
- MVI pattern compliance (State immutability, Action naming, Handler patterns)
- Proper Koin dependency injection usage
- Compose best practices
- Code style consistency

## 🔧 Configuration

### Required Files
- `google-services.json` in both `app/dev/` and `app/store/` directories
- `keystore.properties` for release builds
- Environment variables or `local.properties` for build configuration

### Firebase Setup
The app uses Firebase for analytics and crash reporting. Ensure you have the proper `google-services.json` files configured for both build variants.

## 📋 Development Guidelines

### Module Conventions
- **Core modules** (`core/*`): Shared, reusable functionality
- **Feature modules** (`feature/*`): Self-contained user-facing features
- **App modules** (`app/*`): Application targets and shared app code
- Avoid circular dependencies between modules

### MVI Data Flow
1. **UI** triggers `Actions` (Click, Input, Navigation)
2. **Handlers** process actions and interact with repositories
3. **Store** updates `State` and emits one-time `Events`
4. **UI** recomposes based on new state and handles events

### Testing Strategy
- **Unit Tests**: Business logic, handlers, repositories (`src/test/`)
- **Integration Tests**: Database operations, API interactions
- **UI Tests**: Compose UI components (`src/androidTest/`)
- **Handler Tests**: MVI action processing and state updates

## 🤝 Contributing

We welcome contributions! Please read our guidelines:

- [Contributing Guidelines](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)

### Commit Convention
Follow [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` New features
- `fix:` Bug fixes
- `refactor:` Code refactoring
- `test:` Adding or updating tests
- `docs:` Documentation updates
- `chore:` Maintenance tasks

## 📊 CI/CD

GitHub Actions pipeline automatically:
- Builds and tests on every PR
- Runs static analysis (detekt, lint)
- Generates APKs for releases
- Validates code quality and architecture

See [`.github/workflows/android_build.yml`](.github/workflows/android_build.yml) for details.

## 📝 License

This project is licensed under the terms of the [LICENSE](LICENSE) file.

## 🔗 Links

- **📱 [Google Play Store](https://play.google.com/store/apps/details?id=io.github.stslex.workeeper)**
- **📦 [GitHub Releases](https://github.com/stslex/Workeeper/releases)**
- **📋 [Release Notes](RELEASE_NOTES.md)**
- **🐛 [Report Issues](https://github.com/stslex/Workeeper/issues)**
- **📚 [Documentation](docs)**

---

<div align="center">

**Built with ❤️ for the fitness community**

[⭐ Star this repository](https://github.com/stslex/Workeeper) if it helped you!

</div>