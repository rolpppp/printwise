# Printwise TODO

## AI integration follow-up

- [x] Prepare a secure Gemini backend endpoint and Cloudflare Workers free deployment path.
- [ ] Deploy the backend and configure the Android build with its HTTPS URL.
- [ ] Add a confirmation UI for Gemini class/group suggestions before changing classes.
- [ ] Map Gemini crop indexes to reviewed `Crop` records and reject invalid indexes.
- [ ] Use ML Kit confidence masks or multi-subject results when the beta API exposes reliable subject bounds.
- [ ] Keep ML Kit model-download state, offline errors, and retry messaging visible to the user.
- [ ] Add a manual fallback whenever either AI service is unavailable.

## Dual theme system

Theme A, "Creative Precision", is implemented as the default (`ui/theme/Theme.kt`: `CreativePrecision`, with
Printwise-only tokens in `PrintwiseColors`, read as `MaterialTheme.printwise`). Still to do:

- [ ] Theme B, "Playful Productivity", as a second `PrintwiseThemeSpec`:
  - Primary `#7454E8` (pressed `#6242D2`), secondary `#45D6A2` (pressed `#2DBB87`), accent `#FFD447`.
  - Background `#FAF9FF`, surface `#FFFFFF`, borders `#E9E6F2`.
  - Text `#25243A` / `#77758B` / `#AAA8BA` (disabled).
  - Success `#20A66B`, warning `#D99A23`, error `#E05252`, info `#7454E8`.
  - Check AA like Theme A: `#77758B` secondary text and `#E05252` error text likely need darker variants. Mint, yellow and accent fills need dark text (`#25243A`), never white.
- [ ] Settings screen, reachable from the app, with a theme selector:
  - Options: Creative Precision (default), Playful Productivity, and System Default if dark mode is added.
  - Each option shows a preview card with primary/secondary/accent swatches, the theme name and a one-line description, and the selected one is clearly marked (not by colour alone).
- [ ] Apply theme changes immediately, without a restart, by hoisting the active `PrintwiseThemeSpec` into state above `PrintwiseTheme`. Switching must not reset the picture, pieces, sizes, layout or export settings.
- [ ] Persist the selected theme (DataStore Preferences, or SharedPreferences to avoid a new dependency) and restore it on launch.
- [ ] Dark variants of both themes. The app is light-only for now: `values-night/styles.xml` was removed and the system bars are forced light in `MainActivity`.
- [ ] Canvas rules not yet used: accent border for hovered elements (stylus/mouse hover), `#64748B` measurement guides and `#E2E8F0` grid lines in the editor. The tokens already exist in `PrintwiseColors`.
- [ ] Use the pressed tokens (`primaryPressed`, `secondaryPressed`) for button pressed states if the default ripple doesn't read strongly enough.
- [ ] Verify both themes on a device: WCAG AA contrast, plus disabled, pressed, focused and selected states.
- [ ] Confirm PDF/DOCX exports stay identical across themes. They don't read theme tokens today, so keep it that way.

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
