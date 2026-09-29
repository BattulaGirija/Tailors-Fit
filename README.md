# Tailors Fit

An Android app that drafts sewing patterns from a customer's measurements, so a tailor can
**print the pieces at true size** or **project them straight onto the cloth** and cut along
the lines. There is no manual drafting with chalk and no separate cutting master.

The first category is **saree blouses**, with 28 designs: round, boat, V, sweetheart, square,
deep U back, sleeveless, cap / elbow / ¾ sleeves, front or back opening, and **princess cut**
fronts (two panels joined by a curved seam through the bust point instead of darts), plus
trending styles: puff, bell and frill sleeves, paan (leaf) and pot (matka) necks, keyhole and
dori tie-up backs, and mandarin / high-neck collars.

## Download (test version)

**https://github.com/BattulaGirija/Tailors-Fit/releases/latest/download/TailorsFit.apk**

<img src="screenshots/download-qr.png" width="180" alt="QR code for the download link">

Open the link (or scan the QR code) on an Android phone (Android 7 or newer), tap the
downloaded file, and allow installing from your browser if asked. Every new build is
published to the same link and installs over the previous version.

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

## Languages

The app is in **English, Hindi (हिन्दी) and Telugu (తెలుగు)**. You can switch language on
the login screen or under **Language** in the side menu. On first launch it follows the
phone's language. Everything is translated: screens, measurement names and how-to-measure
help, design names, warnings, and the labels printed on patterns and shown on the projector.

- All texts live in `pattern-core/.../i18n/` (`StringsEn.kt`, `StringsHi.kt`,
  `StringsTe.kt`). Tests check that every language has every text with the same
  placeholders.
- To add a language (e.g. Tamil or Kannada), add a `Language` entry and a table with the
  same keys.
- Tailoring terms that tailors usually say in English (blouse, armhole, apex, dart …) are
  written as loanwords. The translations should be reviewed by native speakers.

## Accounts and admin

- **Tailors** sign up with a name, an optional shop name and phone number, their **e-mail** and
  a **4-digit PIN**, then log in with e-mail + PIN. The login screen comes filled in with the
  e-mail used last time, and the app stays logged in until they choose **Log out**.
- **Forgot PIN?** sends an e-mail with a link. The link opens a small page
  ([`docs/reset.html`](docs/reset.html)) where the tailor chooses a new PIN.
- **Online (Firebase)**: accounts, each tailor's customers and the admin's designs are stored
  online, so a tailor can log in on any phone and the admin sees every tailor. Customers saved
  offline are uploaded when the phone is back online.
- **Admin** (tap **Admin login** on the login screen) can:
  - see tailor accounts: phone, customers, patterns generated, last active;
  - send a tailor a PIN reset link, or remove a tailor (they can no longer log in);
  - add **new designs** by combining the drafting options, with a live preview, and hide any
    design. Designs reach every tailor's phone.
- A build without the Firebase config keeps everything on the phone (the first admin log in
  chooses the admin password there, and the admin sets forgotten PINs by hand).

A 4-digit PIN is easy to remember but only has 10,000 combinations. Firebase slows down and
blocks repeated wrong attempts, so keep **e-mail enumeration protection** switched on in
Firebase (it is on by default).

### Setting up Firebase (once)

1. Go to <https://console.firebase.google.com>, **Create a project** (e.g. "Tailors Fit").
   Google Analytics is not needed. The free **Spark** plan is enough.
2. **Add app → Android**, package name `com.tailorsfit.app`, then **Download
   google-services.json**. (The SHA-1 is not needed.)
3. **Build → Authentication → Get started → Sign-in method → Email/Password → Enable**.
4. **Build → Firestore Database → Create database** (production mode, a location near you,
   e.g. `asia-south1` Mumbai). Open the **Rules** tab, replace everything with the contents of
   [`firestore.rules`](firestore.rules), and **Publish**.
5. **PIN reset page**: on GitHub open the repository **Settings → Pages**, choose **Deploy from a
   branch**, branch `claude/wizardly-franklin-ooznvg` (or `main` once merged), folder `/docs`, and
   save. Then in Firebase **Authentication → Templates → Password reset → ✏️ → Customize action
   URL**, enter `https://battulagirija.github.io/Tailors-Fit/reset.html` and save. You can also
   change the e-mail's wording there (say "PIN" instead of "password").
6. **Admin account**: in **Authentication → Users → Add user**, enter the admin's e-mail and a
   strong password. Copy the new user's **User UID**. In **Firestore Database → Start collection**,
   collection ID `admins`, document ID = that UID, add any field (e.g. `name` = `Admin`), and save.
7. **Give the config to the build**: on GitHub open **Settings → Secrets and variables →
   Actions → New repository secret**, name `GOOGLE_SERVICES_JSON`, paste the whole contents of
   `google-services.json`, and save. Then re-run the "Test release" workflow (or push a change).
   The build log shows `Firebase: on`.

To build locally with Firebase, put `google-services.json` in `app/` (it is git-ignored).

## Drafting method (blouse)

The traditional Indian method, drafted in inches (as tailors mark it with chalk); patterns are
labelled in inches (to the nearest ¼") unless the tailor switches to cm. The code lives in
`pattern-core/.../blouse/BlouseDrafter.kt`; half pieces are drafted on the stitching line and
cut on the fold.

| Part | Rule |
|---|---|
| Chest | chest / 4 + 1" for each of front and back (4" ease in all) |
| Waist | waist / 4 + 1"; the difference from the chest is split between the side seam (about ⅓) and a dart under the bust (at most 1¾") |
| Shoulder | shoulder / 2 from the centre, ½" slope; neck width about 2½" |
| Armhole | as deep as needed for the front + back armhole curves to be the armhole round + 1" (for a size 36 with a 12" shoulder this is the usual 6" deep armhole). The front is scooped 1" in from the shoulder tip, the back ½" |
| Bust point | apex-to-apex / 2 across, apex length (corrected for the slant) down |
| Front length | the side dart takes up to 1¼" of the extra front length; any more lifts the bottom of the front at the side, so the front bottom curves down to the centre and both side seams match |
| Sleeve | underarm width = arm round + 2"; the cap is ½" shorter than the blouse armhole (the armhole is eased onto it), which gives the low cap of a blouse sleeve (about 4") |
| Princess cut | the front is split along a curve from the armhole through the bust point to the bottom; the dart under the bust becomes the gap between the two curves at the bottom |
| Neck | the shape comes from the design, the depth from the customer's measurement. Pot (matka): narrow below the shoulder, a round belly wider than the neck, round at the bottom |

Seam allowances: neck and armhole ⅜", shoulder ⅝", side 1" (room to let out), bottom and sleeve
hem ¾", hook overlap 1", princess seam ⅝". The app also warns about suspicious measurements (for
example, front shorter than back, or an arm round too big for the armhole).

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
- More languages (Tamil, Kannada, Marathi …).
