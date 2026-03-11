# AGENTS Guide for QLess

## App Purpose
QLess is a smart shopping app that helps users:
- **Compare prices** across different stores for products (e.g., 5kg rice: Store A €12 vs Store B €9)
- **Reserve & Pay** in-app to hold items at the store
- **Collect within 48 hours** (10% penalty if not picked up, rest refunded)
- **Get ML-powered recommendations** based on viewing/buying behavior

## Scope and Current State
- Single-module Android app (`:app`) defined in `settings.gradle`; no backend/service modules yet.
- Entry point is one launcher activity, `MainActivity`, declared in `app/src/main/AndroidManifest.xml`.
- **Welcome screen implemented** with app branding, motto, and "Get Started" button.
- Next steps: Login/Registration flow, product browsing, price comparison, cart/checkout.

## Architecture and Data Flow
- Runtime flow is: launcher intent -> `MainActivity.onCreate()` -> `setContentView(R.layout.activity_main)` (`app/src/main/java/com/example/qless/MainActivity.java`).
- UI layer is XML-based with a root `ConstraintLayout` in `app/src/main/res/layout/activity_main.xml`.
- Theme is Material 3 DayNight via `Theme.QLess` (`app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml`).
- App-level metadata/integration hooks (icon, backup/data extraction XML, theme) are wired in `app/src/main/AndroidManifest.xml`.
- There is no navigation framework, DI container, repository layer, or network/database integration in the current code.

## Build/Test Workflows (Gradle)
- Use wrapper from repo root (Gradle `9.1.0` in `gradle/wrapper/gradle-wrapper.properties`).
- Common commands:
  - `./gradlew assembleDebug` (build APK)
  - `./gradlew testDebugUnitTest` (runs JVM unit tests in `app/src/test/...`)
  - `./gradlew connectedDebugAndroidTest` (runs instrumented tests in `app/src/androidTest/...`; requires emulator/device)
  - `./gradlew lint` (Android lint)
- Dependency and plugin versions are centralized in `gradle/libs.versions.toml`; prefer editing there over hardcoding versions in module build files.

## Conventions to Preserve
- Java source package is `com.example.qless` and aligns with `namespace`/`applicationId` in `app/build.gradle`.
- Keep user-facing text in `app/src/main/res/values/strings.xml` (currently `app_name` only).
- Keep resource naming Android-conventional (`snake_case` for layout/resource names, e.g., `activity_main.xml`).
- Build config uses Java 11 compatibility in `app/build.gradle`; keep new code/tooling compatible.
- Repository mode is `FAIL_ON_PROJECT_REPOS` (`settings.gradle`), so new repositories must be added centrally, not per-module.

## Integration Points and Guardrails
- AndroidX is enabled (`gradle.properties` -> `android.useAndroidX=true`); use AndroidX artifacts only.
- `android.nonTransitiveRClass=true` is enabled; reference only resources that exist in this module unless explicitly imported.
- Release minification is currently off (`minifyEnabled false`), and `app/proguard-rules.pro` is template-default.
- Backup/data transfer configs are present but largely template placeholders (`app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`).

## Agent Working Notes
- If adding new screens/features, update all three together: manifest (if needed), Java/Kotlin activity/fragment, and XML layout/resources.
- When introducing libraries, update `gradle/libs.versions.toml` aliases first, then consume aliases in `app/build.gradle`.
- Existing tests are smoke templates (`ExampleUnitTest`, `ExampleInstrumentedTest`); extend these locations rather than inventing new test structure unless architecture changes.
- No prior AI instruction files were found by glob scan (`README.md`, `AGENT*.md`, `CLAUDE.md`, Cursor/Windsurf/Clinerules patterns).
