# Running Workeeper on iOS

Open `iosApp/Workeeper.xcodeproj`, select the shared **Workeeper** scheme and choose an arm64 iPhone simulator. Run builds the static `WorkeeperKit` framework through the app target's first build phase. The simulator uses the development identifier `io.github.stslex.workeeper.dev`. The app targets iOS 18.5. The linked ICU data object requires that minimum.

Use an Apple silicon Mac with Xcode, an installed iOS simulator runtime, a JDK 21 visible to Gradle, and the Android SDK specified in the repository's version catalog. Set `JAVA_HOME` and `ANDROID_HOME` or configure the SDK path in `local.properties`. Hosted acceptance uses Xcode 26.6. Xcode Run uses incremental Gradle builds. Passing `WORKKEEPER_VERIFY_GRADLE=YES` to `xcodebuild` forces the embed build to execute with both Gradle caches disabled.

Gradle configures Android signing even when Xcode only requests the Apple framework. Use existing developer signing configuration where available. In a disposable checkout with neither signing file present, run this from the repository root.

```bash
set -eu
test ! -e keystore.properties && test ! -e ci-smoke.jks || exit 1
keytool -genkeypair -alias ci-smoke -keyalg RSA -keysize 2048 -validity 1 \
    -storepass ci-smoke-pass -keypass ci-smoke-pass \
    -dname "CN=kmp-ios-kit-smoke" -keystore ci-smoke.jks
cat > keystore.properties <<'PROPERTIES'
storeFile=ci-smoke.jks
keyAlias=ci-smoke
storePassword=ci-smoke-pass
keyPassword=ci-smoke-pass
PROPERTIES
```

For the installed suite, boot a fresh, dedicated iPhone simulator. Add the two-color test photo with `python3 .github/scripts/ios_app_test_photo.py "$RUN_DIR/picker.png"` and `xcrun simctl addmedia "$APP_UDID" "$RUN_DIR/picker.png"`. Use the full scheme and the two result views in the [Phase 8 acceptance commands](../documentation/feature-specs/kmp-phase-8-permanent-ios-app.md#reproducible-commands-and-ci-ownership). The system picker reads that simulator's library. Persistence uses a run-unique exercise and template entered through the same screens as a normal session.
