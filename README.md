<div align="center">

<img width="120" height="120" alt="OmniAgent Icon" src="src/assets/images/omni_agent_icon_1790699468686.jpg" style="border-radius: 24px;" />

# OmniAgent AI

**Autonomous Floating AI Assistant powered by Gemini**

[![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin)](https://kotlinlang.org)
[![Gemini](https://img.shields.io/badge/AI-Gemini-blue?logo=google)](https://ai.google.dev)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange)](LICENSE)
[![Package](https://img.shields.io/badge/Package-com.prodev.omniagent-blue)](app/build.gradle.kts)

</div>

---

## 🤖 What is OmniAgent AI?

**OmniAgent AI** is an autonomous AI assistant for Android that runs as a floating overlay on your screen. It uses Google's **Gemini multimodal AI** to see your screen, understand your voice commands, and take actions on your behalf — like a real AI agent living on your phone.

### ✨ Features

- 🪟 **Floating Overlay HUD** — Always-on AI bubble that hovers over any app
- 🧠 **Gemini Multimodal Reasoning** — Understands your screen content visually
- 🎙️ **Voice Input** — Hands-free voice commands via speech recognition
- 📱 **Accessibility Automation** — Taps, scrolls, and navigates UI elements on your behalf
- 🔁 **Routines** — Create and schedule repeatable AI tasks
- 🛡️ **Privacy Engine** — Safety guardrails to prevent sensitive data exposure
- 📷 **Screen Capture** — Secure, on-demand screen reading for AI context
- 🌙 **Dark-first UI** — Futuristic dark mode with cyan/indigo theme

---

## 📋 Requirements

| Requirement | Version |
|---|---|
| Android OS | 7.0+ (API 24+) |
| Target SDK | Android 16 (API 36) |
| Kotlin | 2.2.10 |
| Gradle | 9.3.1 |
| Build Tools | 36.1.0 |

---

## 🚀 Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug or newer recommended)
- A [Google AI Studio](https://aistudio.google.com) account to get a **Gemini API Key**

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/PROGAMERYT-op/OmniAgent.git
   cd OmniAgent
   ```

2. **Add your Gemini API Key**

   Create a `.env` file in the project root (see `.env.example`):
   ```env
   GEMINI_API_KEY=your_gemini_api_key_here
   ```

3. **Open in Android Studio**
   - Select **File → Open** and choose the project folder
   - Let Gradle sync complete automatically

4. **Run the app**
   - Connect a physical device or start an emulator (API 24+)
   - Click **Run ▶** or press `Shift+F10`

> **Note:** The app requires Accessibility Service and Overlay permissions to function. Grant them when prompted on first launch.

---

## 🔐 Permissions Required

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Floating overlay HUD |
| `BIND_ACCESSIBILITY_SERVICE` | UI automation & screen reading |
| `RECORD_AUDIO` | Voice input |
| `FOREGROUND_SERVICE` | Background agent execution |
| `INTERNET` | Gemini API calls |

---

## 🏗️ Project Structure

```
OmniAgent/
├── app/src/main/java/com/prodev/omniagent/
│   ├── MainActivity.kt              # App entry point
│   ├── ai/                          # Gemini AI integration
│   │   ├── GeminiAgentRepository.kt
│   │   └── GeminiModels.kt
│   ├── engine/                      # Core agent logic
│   │   ├── AgentExecutionController.kt
│   │   ├── ScreenCaptureManager.kt
│   │   ├── SpeechInputManager.kt
│   │   └── PrivacyEngine.kt
│   ├── service/                     # Android services
│   │   ├── FloatingOverlayService.kt
│   │   ├── AgentAccessibilityService.kt
│   │   └── ServiceLifecycleOwner.kt
│   ├── ui/                          # Jetpack Compose UI
│   │   ├── MainViewModel.kt
│   │   ├── screens/
│   │   └── theme/
│   └── data/                        # Room DB & preferences
│       ├── db/
│       └── prefs/
```

---

## 🔨 Building a Release APK

1. **Create a keystore** (or use your existing one)
   ```bash
   keytool -genkeypair -v -keystore release.keystore \
     -alias your_alias -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Create `keystore.properties`** in the project root:
   ```properties
   storeFile=/path/to/release.keystore
   storePassword=your_store_password
   keyAlias=your_alias
   keyPassword=your_key_password
   ```

3. **Build the signed release APK**:
   ```bash
   ./gradlew assembleRelease
   ```

   Output: `app/build/outputs/apk/release/app-release.apk`

---

## 🛠️ Tech Stack

| Technology | Usage |
|---|---|
| **Kotlin** | Primary language |
| **Jetpack Compose** | UI framework |
| **Firebase AI (Gemini)** | Multimodal AI |
| **Room Database** | Local storage (routines, logs) |
| **Moshi** | JSON serialization |
| **OkHttp + Retrofit** | Networking |
| **KSP** | Annotation processing |
| **Coroutines + Flow** | Async & reactive streams |

---

## 📄 License

```
Copyright 2026 ProDev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

<div align="center">
  <strong>Built with ❤️ by ProDev India</strong><br/>
  <sub>Package: <code>com.prodev.omniagent</code></sub>
</div>
