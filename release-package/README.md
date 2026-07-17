# Aipoka Photo Transfer — Setup Package

Sends phone photos to a folder on your PC over WiFi. No Google account, no cloud.

This package works no matter which drive (C:, D:, etc.) you install to — the app
auto-detects your own Pictures folder, it does not use anyone else's saved path.

## 1. PC app (install this first)

Folder: `windows/`

Double-click **`Aipoka Transfer Setup 1.0.0.exe`** and install normally (Next → Next → Install).
It installs to your user profile automatically — works the same on C: or D:, you don't need to
pick anything.

Open the app after install. It shows:
- A 6-digit pairing PIN (refreshes every 10 min, locks out for 5 min after 5 wrong tries)
- Your PC's LAN address (now `https://`, not `http://`)
- A **TLS fingerprint** — a short code identifying this PC's security certificate
- A **Browse…** button to choose your save folder (defaults to `Pictures\AipokaTransfer`)

Keep the app open (or minimized) whenever you want to receive photos.

**Note on security**: all traffic (PIN, pairing, photos) is now encrypted over HTTPS using a
certificate generated on first run. Since it's self-signed (no public certificate authority
involved — normal for a private LAN app like this), your phone/browser can't verify it against a
trusted root automatically. The Android app pins the certificate the first time you pair (a
one-time trust-on-first-use step, backed by the PIN itself), so a fake server later on the same
WiFi can't impersonate the real PC. The web-app option needs a manual step — see below.

## 2. Phone app

Folder: `android/`

Copy **`AipokaTransfer.apk`** to your phone and install it (allow "install unknown apps" for
your file manager/browser when prompted — this is a normal signed app, not from Play Store).

Open it, enter the PC's address and PIN shown by the Windows app, grant photo + notification
permissions. After pairing, it uploads your **entire existing photo gallery first**, then keeps
syncing new photos automatically in the background — or tap **Sync Now** anytime. The app shows a
"Recently Synced" list of what it's sent.

## 3. No-install alternative (phone browser only)

Folder: `web-app/`

If you don't want to install the APK: on the PC, open a terminal in this `web-app` folder and run:
```
python -m http.server 8788
```
Then on your phone browser (same WiFi) open `http://<pc-ip>:8788`, enter the PC address (now
`https://<pc-ip>:8787`) + PIN, pick photos, and send manually. No auto-sync, no background upload
— one-time/manual only.

**First time only**: before pairing, open `https://<pc-ip>:8787` directly in a new browser tab —
your phone browser will show a "not private" / certificate warning (expected, it's the self-signed
cert). Tap "Advanced" → "Proceed" once. After that, pairing and uploads from the web-app will work
normally.

## 4. Advanced: raw server (no window, for scripting/always-on PCs)

Folder: `server-cli/`

Alternative to the Windows installer — same server, just no GUI window. Requires Node.js installed.
```
cd server-cli
npm install
npm start
```
Edit `config.json` first if you want a specific save folder or port (leave `downloadPath` empty
to auto-use your own Pictures folder — this is safe on any drive).

## Notes

- Photos save into date-wise subfolders automatically, e.g. `AipokaTransfer\2026-07-17\` — this
  applies no matter which folder you pick with **Browse…**, the date subfolder is always added
  underneath it.
- Pairing = PIN + same WiFi, now over HTTPS. A paired device's access token expires automatically
  after 30 days of inactivity (renewed on each use), so a stolen phone that stops syncing eventually
  loses access on its own.
- Don't pair over public/office WiFi you don't trust — the cert warning/fingerprint checks reduce
  MITM risk but the server is still only as private as the WiFi it's on.
- To update the PC save folder later, reopen the Windows app and use **Browse…**.
- First sync after pairing on the phone can take a while / use noticeable data if the gallery is
  large, since it sends everything, not just new photos.
- The Windows app has a "Recently received" list showing which phone sent what and when.
