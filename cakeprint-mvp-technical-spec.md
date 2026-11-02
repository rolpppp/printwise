# CakePrint MVP — Technical Specification

**Status:** Proposed  
**Platform assumption:** Android phone first.  
**Product goal:** Turn a decoration-sheet image into correctly sized, efficiently packed A4 print pages with minimal setup.

## 1. MVP journey

1. **Import image** — Choose one JPG or PNG from the phone.
2. **Confirm classes** — The app suggests visual classes such as hats, figures, and letters. The user can rename, merge, split, add, or remove classes.
3. **Set strict sizes** — Enter a required height in inches for each class. Width is derived from each crop’s aspect ratio. Continue stays disabled until every class has a valid size.
4. **Choose quantities** — Show an approximate “pieces per A4 page” for each class, then let the user set the number of prints. Counts are user-controlled; the AI does not decide how many to print.
5. **Arrange pages** — Pack the requested cutouts across as many A4 pages as needed. Compare portrait and landscape, then allow manual repositioning.
6. **Export** — Save a print-ready PDF and a Word document. PDF is the reference output for physical sizing.

## 2. Important distinction: class vs. crop vs. quantity

These are three different things in the implementation:

- **Class:** the suggested group label, such as “hat.”
- **Crop:** the actual artwork image to print, with its background removed or cropped. The user reviews/assigns crops to classes; the app may suggest crops but must allow manual crop, split, and correction.
- **Quantity:** the number of copies the user wants printed. This is always user-editable and is never inferred from the number of detected crops.

Class recognition alone cannot produce printable pieces. The crop review step is needed so the layout has actual image assets and measured aspect ratios. On-device subject segmentation is a convenience for suggesting cutouts, not a source of truth: it can merge touching objects and may struggle with small outlined art on a white sheet.

## 3. Simple screen requirements

- One primary action per screen; plain text labels and native inputs.
- Keep the small phone-first layout from the wireframe.
- Size entry comes before quantity entry.
- A size is stored in inches and stays locked during layout unless the user goes back to the size screen.
- Capacity estimate appears only after all sizes are valid.
- Quantity edits update the predicted page count before layout.
- Manual crop/class corrections remain available; no automatic background or image enhancement that could alter the artwork.

## 4. Technical approach

### Android app

- **Language/UI:** Kotlin + Jetpack Compose.
- **Image import:** Android Photo Picker / Storage Access Framework; preserve the original file and decode a working copy for previews.
- **Local image processing:** Google ML Kit Subject Segmentation as an optional first-pass crop assistant. It runs on-device and can return masks for multiple subjects, but the Android API is currently beta and may merge touching subjects. Always provide manual crop correction and a fallback path.
- **Class suggestions:** Gemini vision through a small backend endpoint. Send one image/contact sheet only when the user asks for suggestions; request class names and crop-to-class grouping, not print quantities. Validate the returned JSON and ask the user to confirm it. Never embed a production Gemini key in the mobile app.
- **Offline path:** If segmentation is unavailable or the user is offline, allow manual crop/class setup. Imported images and layout remain local.

### Image data flow

1. Keep the imported source image unchanged.
2. Make a lower-resolution preview for screen interaction; retain full-resolution pixels for export.
3. Store each crop as a source rectangle plus optional alpha mask. Keep source coordinates so edits do not accumulate resampling blur.
4. Store the crop’s aspect ratio. For a class height `H` inches, each crop width is `H × aspect ratio`.
5. Render crops at export resolution from the original image and mask.

### A4 sizing and packing

- A4 page: **8.27 × 11.69 in**. Use a configurable safe margin (default 0.25 in) and gap (default 0.10 in).
- Convert physical dimensions directly: PDF uses **72 points per inch**; DOCX drawing dimensions use **914,400 EMU per inch**.
- Capacity shown before quantity is an estimate per class **if that class were printed by itself**. Mixed classes compete for the same page area, so after quantities are entered the app runs the real mixed-item packer and shows a total page estimate.
- Use a rectangle packing algorithm (MaxRects or equivalent) on each crop’s fixed physical width and height. Try both portrait and landscape and select the layout with fewer pages / less wasted space; let the user override orientation.
- Never scale an item below its entered height to make it fit. Add pages when the selected quantities do not fit.
- Manual layout changes may increase page count, which the preview must recalculate.

### Export

- **PDF:** Generate each A4 page locally with Android `PdfDocument`, then pass it to Android’s print framework. Place the original-resolution crop at its exact physical rectangle. Include a “print at 100% / actual size” reminder.
- **Word (.docx):** Generate a minimal OOXML document with A4 section settings and fixed-size image placements from the same saved layout model. Keep page breaks explicit. Verify the result in Microsoft Word; PDF remains the authoritative print file if Word reflows content.
- Keep the layout model as the single source of truth for both exports.

## 5. Minimal data model

```text
Project
  sourceImageUri, sourceWidthPx, sourceHeightPx
  classes[]
  pageSize = A4
  marginIn, gapIn, orientationPreference

Class
  id, label, heightIn, requestedCopies
  crops[]

Crop
  id, sourceRectPx, optionalMask, aspectRatio, classId

PlacedItem
  cropId, pageIndex, xIn, yIn, widthIn, heightIn, rotation
```

## 6. MVP boundaries

**Included:** single-image import; class suggestions and correction; crop review/manual correction; exact class height; user-entered copies; approximate and mixed-page capacity; A4 portrait/landscape packing; manual adjustment; PDF and DOCX export.

**Deferred:** multi-image jobs, account/cloud storage, automatic cutline generation, background removal beyond segmentation masks, templates/brand presets, batch processing, and iOS port.

## 7. Main implementation risk

The riskiest piece is extracting separate clean crops from dense sticker sheets—not page layout or PDF creation. ML Kit offers on-device multi-subject masks, but it is beta and touching items can merge. Gemini can suggest labels and segmentation masks, but cloud output also needs user review. The MVP should ship with manual crop correction and be evaluated against a small set of the actual images used by the cake business before relying on automation.

## 8. Build sequence

1. Build import, crop editor, and manual class setup.
2. Implement inches-based sizing, A4 preview, packing, and PDF export.
3. Add class suggestions and optional on-device cutout suggestions; keep manual correction.
4. Add DOCX export from the same layout model.
5. Try real cake-decoration sheets and refine spacing, crop correction, and estimates.

## References

- [ML Kit subject segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation) — on-device masks for multiple subjects; currently beta and may combine touching subjects.
- [Gemini image understanding](https://ai.google.dev/gemini-api/docs/image-understanding) — vision can return labels, boxes, and segmentation masks.
- [Gemini structured output](https://ai.google.dev/gemini-api/docs/structured-output) — constrain suggestions to a validated JSON shape.
- [Android custom document printing](https://developer.android.com/training/printing/custom-docs) — Android print flow consumes a generated PDF.
- [Gemini API key guidance](https://ai.google.dev/gemini-api/docs/api-key) — production mobile apps should not expose API keys in client code.
