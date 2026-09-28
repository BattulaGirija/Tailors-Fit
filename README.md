# Tailors Fit

An Android app that drafts sewing patterns from a customer's measurements, so a tailor can
**print the pieces at true size** or **project them straight onto the cloth** and cut along
the lines. There is no manual drafting with chalk and no separate cutting master.

The first category is **saree blouses**, with 12 designs: round, boat, V, sweetheart, square,
deep U back, sleeveless, cap / elbow / ¾ sleeves, and front or back opening.

## How it works

1. **Pick a design.** Home → *Saree Blouses*, then tap a design. Each thumbnail is drawn from
   that design's real draft.
2. **Enter measurements** in inches or cm. You can start from a standard size (S–XXL) and
   adjust. Customers can be saved and reloaded later.
3. **Generate the pattern.** The app drafts the front, back and sleeve with darts, notches,
   grain lines, fold marks and seam allowances. It lays them out on folded cloth and shows how
   much cloth is needed.
4. **Cut**, using one of these outputs:
   - **Print…**: true-size PDF tiled on A4 / Letter / A3. It includes a cover page with a 10 cm
     test square and a page map; pages overlap by 1 cm and have ⊕ marks for joining.
   - **PDF → Single large sheet**: one page as big as the layout, for plotters and print shops.
   - **SVG**: a true-scale vector file (1 unit = 1 cm).
   - **Project onto cloth**: bright lines on black at real size. If a projector is connected
     (HDMI / USB-C adapter / wireless display), the pattern goes to the projector and the phone
     becomes the remote. You can pan in 5 cm steps, jump to a piece, or show a 10 cm grid.
     **Calibrate** once per projector: measure the projected square with a tape and enter the
     numbers. The scale is saved.

## Drafting method (blouse)

The draft is on the stitching line, in centimetres, with half pieces placed on the fold. It
lives in `pattern-core/.../blouse/BlouseDrafter.kt`.

| Part | Rule |
|---|---|
| Bust | bust/4 + 1.5 front, bust/4 + 0.5 back (4 cm total ease) |
| Waist | waist/4 + 1 front, waist/4 + 0.5 back (3 cm ease); the difference is split between side seam (≈⅓) and waist dart |
| Armhole depth | armhole round / 2 from the shoulder-at-neck level |
| Shoulder | shoulder/2 with a 3 cm slope; front and back seams are equal length |
| Bust point | apex-to-apex / 2 across, apex length (corrected for the diagonal) down |
| Side dart | takes up front length − back length, so front and back side seams match when sewn |
| Sleeve | biceps + 2.5 cm ease; the cap height is **solved** so the cap is 1 cm longer than the drafted armhole |
| Neck | the shape comes from the design, the depth from the customer's measurement (boat / deep designs scale it) |

Default seam allowances: neck 1 cm, shoulder 1.5, armhole 1, side 2.5 (room for alterations),
bottom 2, hook opening 2.5, sleeve cap 1, underarm 1.5, sleeve hem 2. The app also warns
about suspicious measurements (for example, front shorter than back, or an arm round too big
for the armhole).

## Project layout

```
pattern-core/   Pure Kotlin (no Android): geometry, measurements, blouse drafting,
                seam allowance offsetting, cloth layout, page tiling, SVG export.
                Covered by unit tests.
app/            Android app (Jetpack Compose): catalogue, measurement forms,
                customers, zoomable preview, PDF/print/share, projector mode.
```

To add a new garment, implement `GarmentModel` in `pattern-core` and add it to `Catalog`;
the screens work from the model's `requiredMeasurements` and the pieces it drafts.

## Building

Requirements: JDK 17+ and the Android SDK (Android Studio Ladybug or newer).

```bash
./gradlew :pattern-core:test      # pattern engine tests
./gradlew :app:assembleDebug      # APK in app/build/outputs/apk/debug/
```

GitHub Actions runs both on every push and uploads the debug APK as a build artifact.

## Roadmap ideas

- More categories: kurti, salwar, lehenga, petticoat (entries are already in the catalogue).
- Princess-cut / katori blouses, collars, padded cups.
- Keystone correction for projectors mounted at an angle.
- Save the tailor's own ease and allowance preferences.
- Telugu / Hindi / Tamil translations.
