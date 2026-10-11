# CheckoutMate — Darts Scorer & Checkout Trainer

A fast, fully offline darts app for Android: X01 scoring (301/501/701), a
verified checkout engine, training games, statistics and a voice caller.
**No account, no server, no ads.** Monetised by one-time in-app purchases.

- 100% Kotlin + Jetpack Compose, single module (`app`).
- Offline-first: a local Room database and Jetpack DataStore; Google Play
  Billing is the only networked piece, and it degrades gracefully when the
  Play Store is absent.
- Built in the cloud by GitHub Actions (see `.github/workflows/android-build.yml`),
  which produces an installable APK and a Play-ready AAB as downloadable
  artifacts.

## Free vs. paid

| Tier | Contents |
|------|----------|
| **Free** | Complete X01 scorer (301/501/701, 1–4 players, legs & sets, double in/out, bust handling, live average, undo, save & resume, manual checkout entry), dartboard **and** keypad entry, German + English. |
| **Checkout Engine** (`unlock_checkout`) | Automatic optimal finishes for every score ≤170, preferred-double setting, bogey-number awareness. |
| **Training Games** (`unlock_training`) | Cricket, Bob's 27, Around the Clock, Shanghai, doubles drill. |
| **Stats & History** (`unlock_stats`) | Match history, 3-dart average over time, checkout %, 180s, highest finish, CSV export. |
| **CheckoutMate Pro** (`unlock_pro`) | All three packs + voice caller (on-device TTS) + extra scoreboard themes (AMOLED, felt). |

The in-app product IDs above must be created **exactly** in the Play Console
(see below).

## Get a build to test

1. Push to the `ccr-c7da2a90-gbh7qj` or `main` branch (or run the
   **Android Build** workflow manually from the Actions tab).
2. Open the finished run in the repository's **Actions** tab.
3. Download the **`app-debug-apk`** artifact, unzip it, and install the
   `.apk` on an Android phone (enable "install from unknown sources").

The debug build is not minified and is the safe build for testing. Because a
side-loaded build has no Play billing connection, the paid screens show a
"store unavailable" note — to try the paid UI, open **Settings → Debug →
"Developer: unlock all features"** (visible only in debug builds).

## Publish to Google Play

1. **Create the app** in the [Play Console](https://play.google.com/console).
   The package name is `com.fabmue.checkoutmate` (set in
   `app/build.gradle.kts`). It is permanent once uploaded — change it there
   first if you want a different one.
2. **Create four in-app products** (one-time, "managed" products) with these
   exact IDs and your prices:
   `unlock_checkout`, `unlock_training`, `unlock_stats`, `unlock_pro`.
3. **Generate an upload keystore** (once):
   ```bash
   keytool -genkeypair -v -keystore upload.keystore -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
4. **Add signing secrets** to the GitHub repository
   (Settings → Secrets and variables → Actions):
   - `ANDROID_KEYSTORE_BASE64` — `base64 -w0 upload.keystore` (the keystore, base64-encoded)
   - `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` (`upload`), `ANDROID_KEY_PASSWORD`
   Re-run the workflow; the release AAB will then be signed with your upload key.
5. Download the **`app-release-aab`** artifact and upload it to a Play
   Console testing track, then production. Enrol in **Play App Signing**
   (recommended) so Google manages the app signing key.

Keystores and passwords are never committed (see `.gitignore`); they live
only in GitHub Actions secrets.

## The checkout table

`app/src/main/assets/checkouts.json` is generated and verified by
`tools/gen_checkouts.py` (pure brute force over the whole board; every entry
is validated to sum correctly, finish on a double, never use a double as a
setup dart, and never leave an odd double when an even one is possible). To
regenerate:

```bash
cd tools
python3 gen_checkouts.py            # writes checkouts.json + prints PASS/FAIL
# then rebuild the compact asset consumed by the app
```

## Build locally (optional)

With Android Studio or a local Android SDK:

```bash
cd checkoutmate
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests (scoring engine + checkout math)
```

## Tech

AGP 8.7.3 · Gradle 8.11.1 · Kotlin 2.0.21 · Compose BOM 2024.12.01 ·
Room 2.6.1 · DataStore · Play Billing 9.1.0 · compileSdk 35 · minSdk 24.
