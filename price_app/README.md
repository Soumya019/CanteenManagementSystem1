# Garia Electric Prices — voice price lookup app

Android app for the shop counter: tap the mic, say an item name
("9 watt bulb", "gitti", "heera extension"), and the selling price appears
instantly. Related items show as a list with their prices — say "switch"
and every switch in stock is listed. Typing in the search box works the
same way. The price list is bundled inside the app (135 items from the
counter sales manual), so **no internet is needed** after install — voice
recognition uses the phone's built-in Google voice input.

Since v1.1:

- **Item photos** — every result row shows the product photo (the same
  ones as the PDF manual). Tap a row to see the photo full-screen, scaled
  to fill the display at its natural aspect ratio; tap again to close.
- **Add your own items** — the green ＋ button opens a form: name,
  selling price, unit, category, and an optional photo from the gallery.
  Saved items live on the phone (`user_items.json` in app storage), are
  searchable like any stock item, and can be removed by long-pressing
  the row.

Since v1.7:

- **Add what the camera saw** — every camera result now offers a green
  *Add this item to my list* button, so an item that isn't stocked in the
  list yet can be added straight from the shot. The form arrives
  pre-filled: the name comes from the words on the carton, the photo is
  the picture just taken, and the price is read off the label —
  a pencilled `@ 03/=` counter rate is used as-is, while a printed MRP is
  divided by the pack count and reduced by the usual 10%. When the label
  shows no price at all the app says so and asks what to charge.
- **Upload several photos for a new item** — *Upload Photos* now accepts a
  multi-selection from the gallery, and photos can be added on top of the
  one taken by the camera. Extra photos belong to the item being added;
  the item list itself still shows one picture per row.
- Camera captures are now full-resolution files (via FileProvider) rather
  than the small preview thumbnail, which is what makes the text on a
  carton readable.

Since v1.5:

- **Camera lookup** — tap 📷, photograph a carton, and the app reads the
  printed words on it (ML Kit's bundled Latin text recogniser, fully
  on-device) and lists the matching items with their prices. Aim at the
  brand and the size, e.g. "AJONTA 6 NANO" or "AAVTAR PLAST 8 Modular".
  If no text is readable it falls back to matching the photo's colours
  against the stored product pictures and shows the closest dozen to tap.

## Building

The camera feature needs the ML Kit dependency, so the app is now built
with Gradle:

```bash
export ANDROID_HOME=/opt/android-sdk
gradle :app:assembleRelease          # -> app/build/outputs/apk/release/
```

`build_apk.sh` still works for a dependency-free build, but the APK it
produces has no camera OCR.

Since v1.3:

- **46 new items** from the 26-Jul-2026 shelf photos (Ajonta PVC switch
  boards, Aavtar modular surface gang boxes, casing-capping joints, metal
  concealed boxes, Chamak flex pipes, heavy copper wire coils, HDPE pipe
  and new counter stock). Where the carton prints an MRP the rate is that
  MRP less 10%; the rest are market estimates shown with an orange
  *"estimated rate — please confirm"* note.
- **Edit any price** — long-press an item and choose *Edit price*. The
  dialog always shows the app's default price, and a *Reset to original*
  button restores it. Edited items show a green *"your price (default was
  ₹X)"* note, so the original is never lost. Prices are stored on the
  phone and survive app updates.

Since v1.2:

- **Automatic photo for new items** — if you save a new item without
  attaching a photo, the app searches the internet (DuckDuckGo image
  search, no API key) for that item and attaches the best photo it finds.
  A "Find Photo Online" button also lets you preview the photo first and
  tap again to cycle through alternatives. This is the app's only use of
  the internet; if the phone is offline the item simply saves without a
  photo.

## Install (the easy way)

1. Copy `GariaElectricPrices.apk` to the phone (WhatsApp to yourself,
   USB cable, or download from this repository).
2. Tap the file. Allow "Install unknown apps" if the phone asks.
3. Open **Garia Electric Prices**, tap the red 🎤 button, and speak.

Works on any Android 7.0+ phone. The mic button uses Google voice typing
(present on practically every Android phone in India); if voice is
missing, typing still works.

## When prices change

The app's price list comes from the same source as the PDF manual
(`shelf_manual/generate_manual.py`). After editing prices there:

```bash
export ANDROID_HOME=/opt/android-sdk   # path to an Android SDK
./build_apk.sh                          # rebuilds GariaElectricPrices.apk
```

Then install the new APK over the old one. Note: `debug.keystore` (created
on first build, not committed) signs the APK — keep it if you want updates
to install over the existing app; without it Android requires
uninstalling the old app first.

## How it works

- `export_items.py` — reads item names + prices from the manual generator
  and writes `app/assets/items.json` with spoken-word keywords per item
  (wattage variants, Hindi/Bengali shop words like *gitti*, *taar*).
- `app/src/.../MainActivity.java` — single-screen app: speech via
  `RecognizerIntent`, token-scored fuzzy search, results in a ListView.
  Pure Android framework — no Gradle, no libraries.
- `build_apk.sh` — direct SDK toolchain build:
  aapt2 → javac → d8 → zipalign → apksigner.
