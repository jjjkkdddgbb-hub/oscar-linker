# Oscar Linker

Oscar Linker is an Android application built with Kotlin and Android Jetpack. The app opens the Quotex sign-in flow inside a WebView, captures the authenticated session data (such as `ssid`, `session`, or `token`), and sends it to a backend API together with the provided license key.

This repository is a native mobile client for the account linking flow used by the OSCAR service.

## Overview

The app is a single-activity Android project with:

- a custom WebView-based authentication flow
- JavaScript interception for session values exposed through WebSocket messages or `localStorage`
- a bridge between JavaScript and Kotlin using `@JavascriptInterface`
- HTTP submission of the captured session and cookie data to a remote API
- a simple status UI for the user while linking is in progress

## Project structure

```text
.
├── app/
│   ├── build.gradle
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/oscar/linker/
│       │   │   ├── CandleBackgroundView.kt
│       │   │   ├── MainActivity.kt
│       │   │   └── SoundFx.kt
│       │   └── res/
│           └── ...
├── build.gradle
├── gradle.properties
├── settings.gradle
└── README.md
