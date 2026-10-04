# OmniAgent AI — Project Summary

> Auto-generated repository analysis for compliance, onboarding, and store/distribution review.
> Generated from source in `/workspaces/OmniAgent` (branch `main`).

---

## 1. App Overview

| Field | Value |
|---|---|
| **App Name** | OmniAgent AI |
| **Application ID / Package** | `com.prodev.omniagent` |
| **Namespace** | `com.prodev.omniagent` |
| **Version** | 1.0 (`versionCode = 1`) |
| **Platform** | Android (native) |
| **Author / Vendor** | ProDev |
| **Repository** | https://github.com/PROGAMERYT-op/OmniAgent |

### Core Purpose
OmniAgent AI is an **autonomous floating AI assistant** for Android that runs as a **system overlay HUD** on top of any app. It combines **multimodal LLM reasoning** (Google Gemini and/or OpenRouter models) with **Android Accessibility automation** so the agent can:

- **See the screen** — on-demand screen capture fed to a multimodal model for visual context.
- **Understand voice** — hands-free speech input for commands.
- **Act on the user's behalf** — taps, scrolls, and navigates UI elements via the Accessibility Service.
- **Run Routines** — create and schedule repeatable AI-driven tasks (stored locally in Room).
- **Enforce privacy guardrails** — a Privacy Engine screens/redacts sensitive data before it is sent to remote models.

It targets "phone agent" use cases where an on-screen assistant observes context and performs multi-step tasks.

---

## 2. Tech Stack

| Category | Technology | Version |
|---|---|---|
| **Language** | Kotlin | 2.2.10 |
| **UI Framework** | Jetpack Compose (Material 3) | Compose BOM `2024.09.00` |
| **Build System** | Gradle | 9.3.1 (wrapper) |
| **Android Gradle Plugin** | AGP | 9.1.1 |
| **Annotation Processing** | KSP (Kotlin Symbol Processing) | 2.3.5 |
| **Java Compatibility** | Java / JVM target | 11 |
| **compileSdk** | Android 16 | API 36 (`minorApiLevel = 1`, i.e. 36.1) |
| **minSdk** | Android 7.0 | API 24 |
| **targetSdk** | Android 16 | API 36 |
| **DI** | Manual (no DI framework — repositories instantiated in `MainViewModel`) | — |
| **Async** | Kotlin Coroutines + Flow | 1.10.2 |
| **Serialization** | Moshi (Kotlin + codegen) and `org.json` | 1.15.2 |
| **Networking** | OkHttp + Retrofit + Logging Interceptor | 4.10.0 / 2.12.0 |
| **Local Storage** | Room (+ KSP compiler) | 2.7.0 |
| **Testing** | JUnit4, Robolectric, Compose UI Test, Espresso, androidx.test | — |

### Build Tooling / Plugins
- `com.android.application` (AGP 9.1.1)
- `org.jetbrains.kotlin.plugin.compose` (Compose compiler, Kotlin 2.2.10)
- `com.google.devtools.ksp` (2.3.5)
- `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` (2.0.1) — open-source (Apache-2.0) build-time plugin that reads secrets from `.env` / `.env.example`

### Build Configuration Notes
- `buildConfig = true`; `compose = true`.
- Release build is **not minified** (`isMinifyEnabled = false`).
- Release signing reads `keystore.properties` (or `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars); debug uses a debug keystore.
- `dependenciesInfo { includeInApk = false; includeInBundle = true }`.

---

## 3. Dependencies & Trackers Check

### 3.1 Major Active Dependencies (from `app/build.gradle.kts` + `gradle/libs.versions.toml`)

**AndroidX / UI**
- `androidx.core:core-ktx` 1.18.0
- `androidx.activity:activity-compose` 1.10.1
- `androidx.compose:compose-bom` 2024.09.00, `compose-ui`, `ui-graphics`, `ui-tooling-preview`, `material3`, `material-icons-core`, `material-icons-extended`
- `androidx.lifecycle:lifecycle-runtime-ktx`, `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` 2.8.7

**Data / Networking**
- `androidx.room:room-runtime`, `room-ktx` 2.7.0 (+ `room-compiler` via KSP)
- `com.squareup.retrofit2:retrofit` 2.12.0, `converter-moshi` 2.12.0
- `com.squareup.okhttp3:okhttp` 4.10.0, `logging-interceptor` 4.10.0
- `com.squareup.moshi:moshi-kotlin` 1.15.2 (+ `moshi-kotlin-codegen` via KSP)
- `org.jetbrains.kotlinx:kotlinx-coroutines-android/-core` 1.10.2

**Test**
- `junit:junit` 4.13.2, `androidx.test.ext:junit` 1.3.0, `androidx.test.espresso:espresso-core` 3.7.0
- `org.robolectric:robolectric` 4.16.1, `kotlinx-coroutines-test` 1.10.2
- Compose UI test (`ui-test-junit4`, `ui-test-manifest`), `androidx.test:core` 1.6.1, `androidx.test:runner` 1.6.2

### 3.2 Declared but Currently Commented-Out (in `app/build.gradle.kts`)
These are present in the version catalog but **not enabled** in the build:
- `com.google.android.gms:play-services-location` — **Google Play Services** (declared in the catalog, but the usage line is commented out and NOT compiled)
- `androidx.navigation:navigation-compose`, `androidx.datastore:datastore-preferences`, `io.coil-kt:coil-compose`, `androidx.camera:*`, `com.google.accompanist:accompanist-permissions`

### 3.3 Proprietary SDKs / Trackers / Google Play Services (F-Droid Compliance)

> **Status after the F-Droid cleanup:** the previously-bundled Firebase SDKs and the `com.google.gms.google-services` plugin have been **REMOVED**.

| Concern | Status | Details |
|---|---|---|
| **Firebase SDK (proprietary)** | ✅ **REMOVED** | `firebase-bom`, `firebase-ai`, `firebase-appcheck-recaptcha`, `firebase-appcheck-debug` (and unused `firebase-firestore`/`firebase-auth` catalog entries) were deleted. |
| **`com.google.gms.google-services` Gradle plugin** | ✅ **REMOVED** | Removed from `app/build.gradle.kts`, `build.gradle.kts`, and the version catalog. |
| **Google Play Services** | ✅ **NOT BUNDLED** | No active dependency references Play Services. `play-services-location` remains a **commented-out** catalog entry only (not compiled). |
| **Firebase Analytics** | ✅ **NOT PRESENT** | No analytics dependency or calls. |
| **Crash reporting (Crashlytics / Sentry / App Center / Bugsnag)** | ✅ **NOT PRESENT** | No crash-reporter SDKs detected. |
| **Third-party analytics / marketing trackers** | ✅ **NOT PRESENT** | No Amplitude, Mixpanel, Segment, Facebook SDK, OneSignal, AppsFlyer, etc. |
| **Advertising SDKs / AdMob** | ✅ **NOT PRESENT** | None detected. |
| **Non-free network services** | ⚠️ **PRESENT** | The app calls `generativelanguage.googleapis.com` (Google Gemini REST) and `openrouter.ai` (OpenRouter) → F-Droid `NonFreeNet` anti-feature. |

**AI provider implementation:** The AI calls are implemented as **direct REST requests over OkHttp** (no Firebase runtime SDK):
- Gemini → `https://generativelanguage.googleapis.com/v1beta/models/...` (`GeminiAgentRepository.kt`)
- OpenRouter → `https://openrouter.ai/api/v1/...` (`OpenRouterRepository.kt`)

**Remaining F-Droid consideration:** The main functionality relies on remote AI services that are not libre/self-hostable in-app (Google Gemini API, OpenRouter). This qualifies for the `NonFreeNet` anti-feature. The Gemini API key is user-supplied (runtime Settings) or injected at build time via `BuildConfig.GEMINI_API_KEY` (from `.env`). To remove the anti-feature entirely, the provider endpoints would need to be fully user-configurable to libre services.


---

## 4. Project Structure

Single-module Android app (`:app`).

```
OmniAgent/
├── app/
│   ├── build.gradle.kts                  # App module build config (appId, SDKs, deps)
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Permissions, Activity, 2 Services
│       │   ├── java/com/prodev/omniagent/
│       │   │   ├── MainActivity.kt            # Entry point (launcher Activity, Compose)
│       │   │   ├── ai/                        # LLM provider integration
│       │   │   │   ├── GeminiAgentRepository.kt   # Gemini REST client
│       │   │   │   ├── OpenRouterRepository.kt    # OpenRouter REST client
│       │   │   │   ├── GeminiModels.kt            # Request/response models
│       │   │   │   └── ModelTypes.kt              # Shared AvailableModel type
│       │   │   ├── engine/                    # Core agent logic
│       │   │   │   ├── AgentExecutionController.kt # Multi-step agent loop
│       │   │   │   ├── ScreenCaptureManager.kt     # MediaProjection capture
│       │   │   │   ├── SpeechInputManager.kt       # Voice input
│       │   │   │   └── PrivacyEngine.kt            # Sensitive-data guardrails
│       │   │   ├── service/                   # Android services
│       │   │   │   ├── FloatingOverlayService.kt    # HUD overlay (foreground svc)
│       │   │   │   ├── AgentAccessibilityService.kt # UI automation
│       │   │   │   └── ServiceLifecycleOwner.kt     # Compose-in-service plumbing
│       │   │   ├── ui/                        # Presentation layer
│       │   │   │   ├── MainViewModel.kt             # Single ViewModel (state + providers)
│       │   │   │   ├── screens/                     # Dashboard, Routines, Permissions,
│       │   │   │   │                                  Settings, OnboardingDialog
│       │   │   │   └── theme/                       # Color, Type, Theme
│       │   │   └── data/                      # Persistence
│       │   │       ├── db/                          # Room: AppDatabase, Entities, RoutineDao
│       │   │       └── prefs/AgentPreferences.kt    # Settings & API keys (SharedPreferences)
│       │   └── res/                        # drawable, mipmap (launcher icons), values, xml
│       ├── test/java/...                   # Unit tests (JUnit, Robolectric)
│       └── androidTest/java/...            # Instrumented tests (Espresso)
├── fastlane/metadata/android/en-US/        # F-Droid/Fastlane store metadata
│   ├── title.txt                           # "OmniAgent AI"
│   └── short_description.txt               # "Autonomous floating AI assistant for Android"
├── gradle/
│   ├── libs.versions.toml                 # Version catalog (all deps/plugins)
│   └── wrapper/                            # Gradle 9.3.1 wrapper
├── build.gradle.kts                       # Root build (plugins declared, apply false)
├── settings.gradle.kts                    # Repos (google(), mavenCentral()), module :app
├── gradle.properties                      # JVM args, configuration-cache, workers
├── metadata.json                          # App metadata (name, description, capabilities)
├── LICENSE                                # GNU AGPL-3.0
├── README.md
├── app_summary.md                         # This file
├── .env / .env.example                    # GEMINI_API_KEY (secrets; .env gitignored)
└── keystore.properties / *.keystore       # Signing (gitignored)
```

### Key Entry Points
- **`MainActivity`** (`com.prodev.omniagent.MainActivity`) — launcher `ComponentActivity`; hosts the Compose `MainAppScaffold` with a 4-tab bottom nav (Dashboard, Routines, Permissions, Settings) and an onboarding dialog.
- **`FloatingOverlayService`** — foreground service (`specialUse|mediaProjection`) that renders the floating AI HUD/overlay.
- **`AgentAccessibilityService`** — `BIND_ACCESSIBILITY_SERVICE`-protected service for reading the screen hierarchy and performing gestures.

### Declared Permissions (from `AndroidManifest.xml`)
`SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, `INTERNET`, `ACCESS_NETWORK_STATE`, `RECORD_AUDIO`, `VIBRATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (+ accessibility service binding).

> Note: the Accessibility Service and Overlay permissions are powerful/sensitive; some app stores restrict `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and Accessibility-Service usage as policy concerns.


---

## 5. License Status

| Field | Value |
|---|---|
| **License** | **GNU Affero General Public License v3.0 (AGPL-3.0)** |
| **LICENSE file** | ✅ Present at `/workspaces/OmniAgent/LICENSE` (full AGPL-3.0 text, 661 lines) |
| **README reference** | ✅ README declares AGPL-3.0 with a license badge |
| **SPDX identifier** | `AGPL-3.0-only` |

**Status:** A libre license is already in place — **no new license file is required**. AGPL-3.0 is an OSI-approved free-software license and satisfies F-Droid's licensing requirement.

> Note: the requested commit message references "GPL-3.0"; the actual project license is **AGPL-3.0** (a GPL-family copyleft license). The existing `LICENSE` and `README` both declare AGPL-3.0 and were intentionally set to AGPL in the commit `9816e54 ("docs: fix license to GNU AGPL v3 in README")`, so the file was preserved rather than replaced.

---

## 6. Quick Compliance Checklist (F-Droid)

| Item | Result |
|---|---|
| Free/libre license present | ✅ AGPL-3.0 |
| Source code available | ✅ (public repo) |
| Builds from source without proprietary binaries | ✅ Verified (`./gradlew assembleDebug` → BUILD SUCCESSFUL) |
| No proprietary Firebase / Play Services | ✅ Removed (no Firebase; no Play Services compiled) |
| No proprietary ad / analytics / crash SDKs | ✅ None found |
| No tracking | ✅ No trackers detected |
| Networks to non-free services documented | ⚠️ Gemini + OpenRouter (`NonFreeNet` anti-feature) |
| Reproducible / unminified release | ✅ Release not minified (`isMinifyEnabled = false`) |

---

## 7. Notes & Caveats

- This summary reflects the working tree after the F-Droid cleanup (Firebase SDKs and the Google Services plugin removed) and a verified `./gradlew assembleDebug`.
- Build verification: **BUILD SUCCESSFUL in ~6m 53s**; the debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk` (~20 MB). A non-fatal KSP "AWT-EventQueue" warning may appear on some JDKs (JDK 25); it does not fail the build.
- `play-services-location` remains as a **commented-out** catalog entry (not compiled). It can be deleted for full cleanliness.
- Secrets/config are correctly **gitignored**: `.env`, `keystore.properties`, `*.keystore`, `google-services.json`, `local.properties`.
- `metadata.json` advertises `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` and declares no frame permissions.
- Versions such as AGP `9.1.1`, Gradle `9.3.1`, and `compileSdk` 36.1 are as declared in the repository build files.

