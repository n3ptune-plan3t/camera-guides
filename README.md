# Camera Guides

Android camera app (CameraX, Kotlin) with composition-guide overlays: Rule of Thirds,
Golden Ratio spiral, Symmetry, Diagonals, Leading Lines, S-Curve, Frame in Frame,
Repetition, Juxtaposition.

Features: live preview, photo capture to `Pictures/CameraGuides`, front/back switch,
flash (off/auto/on), tap to focus, guides on/off. Targets Android 15 (API 35), min API 29.

## Build

Push to `main` (or run the workflow manually); the APK is the `camera-guides-apk`
artifact of the "Build Android APK" action. Locally: `gradle assembleDebug`
(Gradle 8.10+, JDK 17, Android SDK 35).

The debug APK is signed with the debug key, so it installs directly
(allow "install unknown apps" for your file manager/browser).
