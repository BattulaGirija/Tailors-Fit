# Tailors Fit

An Android app that drafts sewing patterns from a customer's measurements, so a tailor can
**print the pieces at true size** or **project them straight onto the cloth** and cut along
the lines. There is no manual drafting with chalk and no separate cutting master.

The first category is **saree blouses**, with 126 designs: round, boat, V, sweetheart, square,
deep U back, sleeveless, cap / elbow / ¾ sleeves, front or back opening, and **princess cut**
fronts (two panels joined by a curved seam through the bust point instead of darts), plus
trending styles: puff, bell and frill sleeves, paan (leaf) and pot (matka) necks, keyhole and
dori tie-up backs, and mandarin / high-neck collars. The **3 Dart** tab has the common tailor's
list: basic, boat, close, high and halter necks and bottom waves, each with front (FO) or back
(BO) hooks, plus the Bengaluru model. The **4 Dart** tab has the same necks with or without a
**patti** (WP / WOP), a band across the bottom of the front. The **Princess** tab has the same set
in princess cut, plus net yoke backs, net front inserts, curved bottoms, a princess seam from the
middle of the shoulder and the Bengaluru models.

**Kurtis** (14 designs): straight with side slits (round, V, square, boat neck with puff sleeves,
mandarin collar), A-line, flared, high-low and anarkali (12 or 16 kalis), with short, elbow, ¾,
full, bell or puff sleeves.

**Lehenga & Skirts** (18 designs): 8 / 12 / 16 / 24 kali lehengas, circular, half circle, A-line,
mermaid (fishtail), gathered ghagra and tiered lehengas; A-line, pencil, circle, half circle,
gathered, tiered, box pleated and 6-panel skirts.

## Download (test version)

**https://github.com/BattulaGirija/Tailors-Fit/releases/latest/download/TailorsFit.apk**

<img src="screenshots/download-qr.png" width="180" alt="QR code for the download link">

Open the link (or scan the QR code) on an Android phone (Android 7 or newer), tap the
downloaded file, and allow installing from your browser if asked. Every new build is
published to the same link and installs over the previous version.

## How it works

1. **Pick a design.** Home → *Saree Blouses*. Designs are grouped in tabs (**All, 3 Dart,
   4 Dart, Princess, Katori, Sabyasachi**) with a search box; every sketch is drawn from the
   design's real draft. Or tap **Design your own** to start from a plain blouse.
2. **Customise it**, one window at a time, with a front / back sketch that follows every choice:
   **Front neck** → **Back neck** (shape and depth, including pot, paan, keyhole and dori backs)
   → **Sleeves** → **Blouse details** (type, where the hooks go, **Halter**, **Bottom waves**,
   **Patti**, **Bottom curve**, **Shoulder cut**, and a **net back yoke** or **net front insert**
   with a straight, V, round, scallop or sweetheart edge). **Next** and **Back** move between the
   windows, and the step bar at the top jumps straight to any of them.
3. **Enter measurements** in inches or cm, in the order of a tailor's blouse sheet: Length,
   Upper chest, Center chest, Shoulder width, Sleeve length, Sleeve round, Middle hand round,
   Front neck height, Back neck height, Waist loose, Front dart point, Chest height, Full
   shoulder, Armhole round. The front length (Length + ½" + half of center chest − upper
   chest), the bust points (center chest ÷ 10 from the centre) and the neck width (full
   shoulder ÷ 2 − shoulder width) are worked out from these; katori / sabyasachi belts sit at
   the chest height. The ⓘ next to each field (and *How to measure*) shows where the tape goes on a
   figure. Start from a standard size (S–XXL) and adjust; customers can be saved and reloaded.
   **Customization details** (optional) change the draft itself: neck broad, front / side dart
   width, hook dart distance, front and back arm curve, shoulder drop and armhole depth.
4. **Generate the pattern.** Each piece is shown on its own on inch graph paper with how many to
   cut, its size and notes, plus the drafted values (armhole depth, neck broad, …).
5. **Cut patterns.** All pieces laid on the cloth to waste as little as possible, on a dark
   cutting table with an inch grid and rulers (tap a piece to highlight it), with the cloth
   width / folding options and how much cloth is needed. From here:
   - **Print…**: true-size PDF tiled on A4 / Letter / A3 with a test square and page map;
   - **PDF → Single large sheet** for plotters, and **SVG** (1 unit = 1 cm);
   - **Project onto cloth**: bright lines on black at real size on a connected projector, with
     calibration, panning and a 2" grid.

### Blouse types

| Type | Front |
|---|---|
| 3 Dart | side dart, dart under the bust and a small dart near the hooks |
| 4 Dart | as 3 dart, with the side shaping split into two side darts |
| Princess | two panels joined by a curved seam from the armhole through the bust point to the bottom |
| Katori | cup panels joined from the armhole through the bust point, and a separate belt below |
| Sabyasachi | cup panels joined from the middle of the shoulder through the bust point, and a belt |

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
| Armhole (arm round) | leaves the shoulder end straight down, then curves out to meet the chest line level at the underarm (the back a little flatter); as deep as needed for front + back curves to be the arm round (about 6" for a size 36). The arm-curve adjustments cut it further in near the shoulder |
| Bust point | apex-to-apex / 2 across, apex length (corrected for the slant) down |
| Front length | the side dart takes up to 1¼" of the extra front length; any more lifts the bottom of the front at the side, so the front bottom curves down to the centre and both side seams match |
| Sleeve | underarm width = arm round + 2"; the cap is ½" longer than the blouse armhole and eased into it, which gives the low cap of a blouse sleeve (about 4") |
| Princess cut | follows the tailor's princess drafting chart (sizes 32–38): shoulder end 1½" inside full shoulder / 2 (5½" for a 36), front chest line chest / 4 + ½", bust point chest / 10 across, front = Length + 1½" at the centre. The seam leaves the arm round where a 45° line up from the corner (shoulder end, chest line) meets it, runs through the bust point, and at the bottom its left leg is ½" inside the bust point with the dart as the gap; the bottom is waist / 4 plus the dart. **Shoulder cut** starts the seam at the middle of the shoulder instead |
| Halter | the shoulder ends 1¾" from the neck point, front and back; no sleeve |
| Bottom waves | the bottom edge is cut in a whole number of scallops about 3" long and ⅖" deep; the darts still open on it |
| Patti | the darted front is cut 1½" above the bottom; the darts end on the patti seam and the band is one piece with the dart widths closed (so it is that much shorter than the seam) |
| Princess patti | both panels are cut along one straight line 1½" above the bottom; the band is as long as the two cut edges together |
| Bottom curve | the front bottom drops in a smooth curve to 1" lower at the centre; the side seam is unchanged |
| Shoulder cut | the princess seam starts at the middle of the shoulder instead of on the arm round |
| Net back yoke | the back is cut along the chosen edge, ¾ of the armhole depth down (lower at the centre for V / round), into a net yoke and the back |
| Net front insert | the centre front is cut along the chosen edge, 2½" below the front neck at the centre, rising to the shoulder just past the neck point and staying at least ¾" below the neckline |
| Neck | the shape comes from the design, the depth from the customer's measurement. Pot (matka): narrow below the shoulder, a round belly wider than the neck, round at the bottom |

Seam allowances: neck and armhole ⅜", shoulder ⅝", side 1" (room to let out), bottom and sleeve
hem ¾", hook overlap 1", princess seam ⅝". The app also warns about suspicious measurements (for
example, front shorter than back, or an arm round too big for the armhole).

## Drafting method (kurti, lehenga, skirt)

| Part | Rule |
|---|---|
| Kurti body | chest / 4 + 1", natural waist / 4 + 1", hip / 4 + ¾"; ¾" shoulder slope; the armhole as deep as needed for front + back curves = armhole round + 1"; a 1" side dart on the front pointing at the bust point (the front is 1" longer below it so the side seams match) |
| Kurti hem | straight: hip + ½"; A-line and high-low: hip + 3"; flared: hip + 6"; high-low back 3" longer at the centre; slits open from 2" below the hip |
| Anarkali | bodice to the waist with a dart under the bust; kalis from the waist (waist + 4") over the hip (hip + 4") to a hem about four times the waist |
| Skirt | waist + 1", hip + 2", length below a 1½" waistband; A-line and pencil have a dart each side; kalis and circle sectors have curved hems that stay level once joined; circles are cut as 16 (full) or 8 (half) sectors so they fit normal cloth |
| Gathered, tiered, pleated | straight panels up to 100 cm wide: gathered 2–3× the hip, tiers 1.3× the hip then 1.5× each, box pleats 3× the waist |
| Closing | lehengas: drawstring casing and a 9" side placket; skirts: waistband with a 2" overlap and a zip |

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
