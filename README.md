# Tailors Fit

An Android app that drafts sewing patterns from a customer's measurements, so a tailor can
**print the pieces at true size** or **project them straight onto the cloth** and cut along
the lines. There is no manual drafting with chalk and no separate cutting master.

The first category is **saree blouses**, with 16 designs: round, boat, V, sweetheart, square,
deep U back, sleeveless, cap / elbow / ¾ sleeves, front or back opening, and **princess cut**
fronts (two panels joined by a curved seam through the bust point instead of darts).

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

## Accounts and admin

- **Tailors** sign up with a name, an optional shop name, a phone number or e-mail, and a
  password, then log in. Each tailor has their own customer book. The app stays logged in
  until they choose **Log out** in the side menu.
- **Admin**: tap **Admin login** on the login screen. The first time, you choose the admin
  password. The admin can:
  - see tailor accounts: customers, patterns generated, last active; and delete accounts;
  - add **new designs** by combining the drafting options (front/back neck shape and depth,
    neck width, sleeves, opening, princess cut), with a live preview;
  - hide any design from tailors.
- Passwords are salted and hashed (PBKDF2); they are never stored as plain text.
- **Today everything is stored on the phone**, so the admin only sees tailors who signed up
  on the same device. Accounts go through the `AccountStore` interface, so an online backend
  (e.g. Firebase Auth + Firestore) can replace `LocalAccountStore` to see tailors on every
  phone.

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
| Princess cut | the front is split along a curve from the armhole (halfway up) through the bust point to the bottom; the waist-dart width becomes the gap between the two curves at the bottom, and the bust-dart length is taken off the side panel so the side seam matches the back. Both seam edges are the same length |
| Neck | the shape comes from the design, the depth from the customer's measurement (boat / deep designs scale it) |

Default seam allowances: neck 1 cm, shoulder 1.5, armhole 1, side 2.5 (room for alterations),
bottom 2, hook opening 2.5, princess seam 1.5, sleeve cap 1, underarm 1.5, sleeve hem 2. The app also warns
about suspicious measurements (for example, front shorter than back, or an arm round too big
for the armhole).

## Laying pieces on the cloth (nesting)

The layout engine (`pattern-core/.../layout/Nesting.kt`) arranges pieces to use as little
cloth length as possible, and the projector shows that same arrangement.

- Pieces are handled by their real cut outline (on a 0.5 cm grid), not their bounding box, so
  a sleeve cap can tuck under an armhole and a front can nest beside a back.
- Grain always runs along the cloth. Pieces are only flipped or turned upside down, never
  turned sideways. Pieces cut on the fold always keep their centre line on the cloth fold.
- For each piece the engine picks the lowest spot where it fits (bottom-left fill). It
  repeats this for every order of the pieces (all 120 for five pieces) and keeps the
  shortest result.
- Switch on **One-way print or velvet** to stop pieces being turned upside down.

For example, for a size-36 classic blouse on 110 cm folded cloth, simple rows needed 102 cm;
nesting needs 75 cm. The app shows how much of the cloth ends up inside the pieces.

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
- Katori blouses, back princess seams, collars, padded cups.
- Keystone correction for projectors mounted at an angle.
- Save the tailor's own ease and allowance preferences.
- Telugu / Hindi / Tamil translations.
