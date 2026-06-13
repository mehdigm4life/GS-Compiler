<div align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" alt="GS Compiler Logo" width="120" height="120" />

  <h1>♟️ GS Compiler</h1>
  <p><strong>Standalone Pawn Compiler for Android — Compile .pwn → .amx on-device</strong></p>

  <p>
    <a href="#-features">Features</a> •
    <a href="#-architecture">Architecture</a> •
    <a href="#-getting-started">Getting Started</a> •
    <a href="#-building">Building</a> •
    <a href="#-credits">Credits</a>
  </p>

  <p>
    <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/C/C++-00599C?style=for-the-badge&logo=c&logoColor=white" alt="C/C++" />
    <img src="https://img.shields.io/badge/NDK-34A853?style=for-the-badge&logo=android&logoColor=white" alt="Android NDK" />
    <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
    <img src="https://img.shields.io/badge/API_26+-FF6D00?style=for-the-badge&logo=android&logoColor=white" alt="API 26+" />
  </p>

  <hr />
</div>

## 📋 Overview

**GS Compiler** is a standalone Android application that compiles Pawn (`.pwn`) scripts into AMX bytecode (`.amx`) directly on your device using the Android NDK. Built specifically for SA-MP (San Andreas Multiplayer) developers, it empowers you to write, edit, and compile server scripts on-the-go — no desktop PC required.

> "Compile anywhere, deploy everywhere."

## ✨ Features

### 🧠 Native Compilation Engine
- Full Pawn compiler (C-based) running natively via JNI
- Compiles `.pwn` → `.amx` **in the same directory** as the source file
- Background compilation via Kotlin Coroutines — UI stays responsive
- Support for multiple Pawn compiler versions

### 🔍 Smart Auto-Detect Include System
- Automatically parses the file path and climbs the directory tree
- Locates `pawno/include/`, `include/`, or `includes/` folders
- Dynamically generates `-i` flags — no manual linking required

### 📱 Android 14 Ready
- Uses `MANAGE_EXTERNAL_STORAGE` (All Files Access) for full file system access
- SAF (Storage Access Framework) integration for `.pwn` file picking
- Legacy support via `requestLegacyExternalStorage`

### ✏️ Built-in Pawn Editor
- **Lightweight** — optimized for 2GB–3GB RAM devices
- **Syntax highlighting** — keywords, strings, comments, numbers, functions, preprocessor
- **Line numbers** gutter
- **Toolbar** — Undo / Redo / Save / Open / **Compile**

### 🖥️ Professional Console
- Read-only terminal-style output panel
- **Text selectable** — highlight and copy specific errors
- **Copy All Logs** button
- Real-time streaming of native C `stdout` / `stderr` to the Kotlin terminal
- Color-coded output (green for success, red for errors)

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│                  GS Compiler                     │
├──────────────────────┬──────────────────────────┤
│    Kotlin / Compose   │     C / C++ (NDK)        │
│                      │                          │
│  ┌────────────────┐  │  ┌────────────────────┐  │
│  │  PawnEditor    │  │  │  sc.c (Compiler)   │  │
│  │  (Syntax HL)   │  │  │  ┌─ Lexer          │  │
│  ├────────────────┤  │  │  ├─ Parser         │  │
│  │  ConsoleView   │◄─┼──┼──┤  └─ Code Gen    │  │
│  │  (Terminal)    │  │  │  └────────────────────┘  │
│  ├────────────────┤  │  │  ┌────────────────────┐  │
│  │ IncludeDetector│  │  │  │  jni_bridge.cpp   │  │
│  │ (Auto-Detect)  │  │  │  │ (stdout→Kotlin)   │  │
│  ├────────────────┤  │  │  └────────────────────┘  │
│  │ FileManager    │  │  │  ┌────────────────────┐  │
│  │ (SAF + SAF)    │  │  │  │  AMX Header       │  │
│  └────────────────┘  │  │  │  (Binary Format)   │  │
│                      │  │  └────────────────────┘  │
└──────────────────────┴──────────────────────────┘
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose + Material 3 |
| **State** | ViewModel + StateFlow |
| **Async** | Kotlin Coroutines (Dispatchers.IO) |
| **Native** | C (Pawn Compiler) + C++ (JNI Bridge) |
| **Build** | CMake 3.22.1 + Gradle KTS |
| **Storage** | MANAGE_EXTERNAL_STORAGE + SAF |
| **Min SDK** | API 26 (Android 8.0) |
| **Target SDK** | API 34 (Android 14) |

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- NDK 25.2.9519653
- CMake 3.22.1
- Java 17+

### Clone & Build

```bash
git clone https://github.com/mehdigm4life/GS-Compiler.git
cd GS-Compiler
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install & Run

1. Enable **Install from unknown apps** on your Android device
2. Transfer the APK or use Android Studio's built-in deployment
3. Grant **Files and media** (All files access) permission when prompted
4. Open a `.pwn` file → the include paths are auto-detected
5. Click **Compile** → your `.amx` appears next to the source file

## 📁 Project Structure

```
GS-Compiler/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       └── java/com/mehdigm/compiler/
│           ├── MainActivity.kt
│           ├── compiler/         # JNI + compilation result
│           ├── include/          # Auto-detect include paths
│           ├── storage/          # Android 14 file access
│           ├── model/            # Data models
│           └── ui/
│               ├── theme/        # Dark theme
│               ├── editor/       # Code editor + syntax HL
│               └── console/      # Terminal + ViewModel
├── compiler/
│   ├── CMakeLists.txt
│   └── src/
│       ├── sc.c                 # Compiler engine (~1300 LOC)
│       ├── jni_bridge.cpp       # JNI bridge
│       ├── pawncc.c             # CLI entry
│       └── ...                  # Support files
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 🛠️ Building from Source

### With Android Studio

1. Open `GS-Compiler/` as an existing project
2. Wait for Gradle sync and NDK CMake configuration
3. Press **Run** ▶️

### With Command Line

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing)
./gradlew assembleRelease

# Build specifically for arm64
./gradlew assembleDebug -PtargetArch=arm64-v8a
```

### CI/CD

The repository includes a GitHub Actions workflow (`.github/workflows/android-build.yml`) that:
- Builds native libraries for `arm64-v8a`, `armeabi-v7a`, and `x86_64`
- Generates a unified APK containing all architectures
- Uploads build artifacts

## 📜 License

This project is licensed under the **Apache License 2.0**.

The Pawn compiler source code is based on the original work by **CompuPhase** (ITB CompuPhase) and is used under the terms of the Apache 2.0 license.

---

## 👨‍💻 Credits

<div align="center">
  <br />
  <table>
    <tr>
      <td align="center">
        <h3>Mehdi GM (GS 4 LIFE)</h3>
        <p><strong>Founder & Lead Developer</strong></p>
        <p>
          <a href="https://github.com/mehdigm4life">GitHub</a> •
          <em>mehdigm4life</em>
        </p>
        <p>
          <em>"Code is chess — every move matters."</em> ♟️
        </p>
        <p>
          <sub>Pawn compiler implementation, JNI bridge, Android architecture,<br>
          UI/UX design, include detector algorithm, build system.</sub>
        </p>
      </td>
    </tr>
  </table>

  <br />

  <h3>🙏 Special Thanks</h3>
  <table>
    <tr>
      <td align="center"><strong>CompuPhase</strong><br><sub>Original Pawn compiler & AMX format</sub></td>
      <td align="center"><strong>SA-MP Team</strong><br><sub>San Andreas Multiplayer platform</sub></td>
      <td align="center"><strong>OpenCode AI</strong><br><sub>AI-assisted development</sub></td>
    </tr>
  </table>

  <br />
  <hr />
  <p>
    Made with ❤️ by <strong>Mehdi GM (GS 4 LIFE)</strong>
    <br />
    <sub>GS-Compiler © 2026 — Compile anywhere, deploy everywhere.</sub>
  </p>
</div>
