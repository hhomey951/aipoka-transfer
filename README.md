# Aipoka Photo Transfer

Auto-sends phone photos to a folder on your PC over the same WiFi. Pairing is a one-time PIN code shown by the PC server — no Google account needed.

## 1. PC server — desktop app (recommended)

A real one-click app with a folder browse button and live paired-device list, in `electron-app/`. Same server underneath, plus a window.

```
cd electron-app
npm install
npm start
```

Window shows the pairing PIN, LAN address, and a **Browse…** button to pick the save folder (defaults to `Pictures/AipokaTransfer`). Photos land in date-wise subfolders automatically, e.g. `AipokaTransfer/2026-07-17/`.

For a real double-click desktop icon (no terminal at all): `npm run dist` builds a Windows installer via electron-builder — downloads Electron's build tools first time (~150-200MB), then produces a Start Menu + Desktop shortcut.

### 1b. PC server — plain CLI (no window)

```
cd server
npm install
```

Edit `server/config.json` — set `downloadPath` to where you want photos saved, and `port` if 8787 is taken.

```
npm start
```

Terminal prints a 6-digit PIN (refreshes every 10 min) and the PC's LAN address, e.g.:

```
Pairing PIN: 640318  (valid 10 min)
Server address: http://192.168.0.102:8787
```

Photos also save into date-wise subfolders here. Keep this running whenever you want to receive photos — or just use the desktop app above instead.

## 2. Web app (works from any phone browser, manual send)

```
cd web-app
python -m http.server 8788
```

On your phone (same WiFi), open `http://<pc-ip>:8788`, enter the PC's server address and the PIN, tap **Pair with PC**, then pick photos and **Send to PC**.

## 3. Android app (auto background sync)

Requires Android Studio (this repo has no local Android SDK to build from the CLI).

1. Open `android-app/` in Android Studio — let it generate the Gradle wrapper on first sync.
2. Build & install on your phone (USB debugging or `adb install`).
3. Open the app — it auto-discovers the PC server on the same WiFi via mDNS, or type the address manually. Enter the PIN shown by the PC server.
4. Grant photo + notification permissions when prompted.

After pairing, new photos upload automatically:
- Instantly-ish when a new photo appears and you're on WiFi (content-trigger job).
- As a fallback safety net, every ~15 minutes on WiFi (Android's minimum periodic interval — this is an OS limit, not tunable).
- Or tap **Sync Now** on the app's main screen for an immediate manual sync.

## Security model

Pairing = PIN + same WiFi network. No Google account, no cloud. Good for home use; don't pair over public/office WiFi you don't trust.
