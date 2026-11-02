# Printwise

Printwise is a native Kotlin/Jetpack Compose MVP for arranging cake-decoration artwork on exact-size A4 pages.

The app is intentionally manual-first: it imports one image, supports class and crop correction, stores heights in inches, lets the user control quantities, packs fixed-size rectangles, and exports PDF and DOCX files from the same layout model.

## Build

Open the repository in Android Studio with an Android SDK installed, then run the `app` configuration. The app uses the standard Android Photo Picker contract and does not require credentials.

## AI integration

The replaceable AI interfaces live under `app/src/main/java/com/printwise/ai/`.

- `MlKitCropSuggestionService` uses the beta on-device subject-segmentation dependency and returns editable crop suggestions. It does not replace manual review.
- `GeminiClassSuggestionService` sends an image only to a configured backend endpoint. It expects validated JSON containing class labels and crop indexes. The mobile app contains no Gemini API key.

The backend endpoint is intentionally not configured in this repository. The manual crop and class workflow remains usable offline. The recommended free deployment is Cloudflare Workers; remaining work is tracked in [TODO.md](./TODO.md).

The backend implementation is in [backend/](./backend/). For a deployed Android build, pass the HTTPS service URL without committing it:

```powershell
.\gradlew.bat assembleDebug -PprintwiseGeminiBackendUrl=https://YOUR_WORKER.workers.dev
```
