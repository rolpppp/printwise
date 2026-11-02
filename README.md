# Printwise

Printwise is a native Kotlin/Jetpack Compose MVP for arranging cake-decoration artwork on exact-size A4 pages.

The app is intentionally manual-first: it imports one image, supports class and crop correction, stores heights in inches, lets the user control quantities, packs fixed-size rectangles, and exports PDF and DOCX files from the same layout model.

## Build

Open the repository in Android Studio with an Android SDK installed, then run the `app` configuration. The app uses the standard Android Photo Picker contract and does not require credentials.

## Deferred integrations

Gemini class suggestions and optional ML Kit segmentation are isolated as future service integrations. No production API key is embedded. The manual crop and class workflow remains usable offline.
