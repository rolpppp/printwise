# Printwise TODO

## AI integration follow-up

- [x] Prepare a secure Gemini backend endpoint and Cloudflare Workers free deployment path.
- [ ] Deploy the backend and configure the Android build with its HTTPS URL.
- [ ] Add a confirmation UI for Gemini class/group suggestions before changing classes.
- [ ] Map Gemini crop indexes to reviewed `Crop` records and reject invalid indexes.
- [ ] Use ML Kit confidence masks or multi-subject results when the beta API exposes reliable subject bounds.
- [ ] Keep ML Kit model-download state, offline errors, and retry messaging visible to the user.
- [ ] Add a manual fallback whenever either AI service is unavailable.

## Remaining MVP work

- [ ] Add drag handles and visual crop editing instead of numeric-only crop coordinates.
- [ ] Persist projects and source-image access across activity/process restarts.
- [ ] Replace the starter greedy packer with a robust mixed-size MaxRects-style packer.
- [ ] Recalculate capacity using the selected orientation and exact crop set.
- [ ] Show all packed pages and support selecting/repositioning items across pages.
- [ ] Detect and report pieces that cannot fit at their requested physical size.
- [ ] Send generated PDFs through Android's print framework.
- [ ] Complete DOCX package relationships and verify placement in Microsoft Word.
- [ ] Add export progress, error notifications, and safe handling for large source images.
- [ ] Add a real saved-layout model/version so PDF and DOCX exports cannot diverge.
- [ ] Add Android Studio/Gradle wrapper setup and build validation.
