# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Aipoka Photo Transfer — sends phone photos to a folder on a PC over local WiFi. Pairing is a
one-time PIN shown by the PC server, no cloud/Google account involved. Four components share one
protocol (`POST /pair`, `POST /upload`, `GET /status`):

- `electron-app/` — Windows desktop app (recommended PC receiver). Wraps the same Express server
  as `server/` plus a GUI (folder picker, live PIN, paired-device list, recent-uploads log).
- `server/` — headless CLI version of the same receiver, no window. Same route logic, duplicated
  (not shared as a package) between `server/` and `electron-app/`. **When fixing a bug in
  `routes/`, `lib/deviceStore.js`, or `lib/datedPath.js`, check whether the same file exists under
  both `electron-app/` and `server/` and needs the same fix in both places** — they've drifted
  before (e.g. `uploadLog.js` currently only exists in `electron-app/lib/`, not `server/lib/`).
- `android-app/` — Kotlin app, auto-syncs new phone photos in the background via WorkManager.
- `web-app/` — static HTML/JS, manual send from any phone browser, no install, no auto-sync.
- `release-package/` — pre-built handoff bundle (installer exe, signed APK, web-app copy, server
  CLI copy, README) for distributing to a non-technical end user. Rebuild artifacts and copy them
  here after any change that should ship; see "Building for distribution" below.

## Commands

### Electron app (PC receiver with GUI)
```
cd electron-app
npm install
npm start                # run in dev
npm run dist             # build Windows installer (electron-builder, output in electron-app/dist/)
```

### Server (headless PC receiver)
```
cd server
npm install
npm start                 # edit config.json first: port, downloadPath, pinTtlMinutes, deviceName
```
`downloadPath` in `config.json` should normally be left as `""` — the server falls back to
`os.homedir()/Pictures/AipokaTransfer` when empty. Do not hardcode an absolute path here; a
previous hardcoded `C:/Users/<name>/...` path broke on any other machine/drive.

### Web app
```
cd web-app
python -m http.server 8788
```

### Android app
```
cd android-app
./gradlew.bat assembleDebug      # debug APK, installs fine for sideload testing
./gradlew.bat assembleRelease    # signed release APK, needs app/aipoka-release.keystore present
```
No local Android SDK is guaranteed to be configured — `local.properties` (`sdk.dir`) is
machine-specific, gitignore it, never hardcode/commit it. Opening the project in Android Studio
regenerates it. Release signing config lives in `android-app/app/build.gradle.kts`
(`signingConfigs { create("release") { ... } }`), pointing at a relative `aipoka-release.keystore`
file inside `app/` — keep that keystore; losing it means you can't ship updates users can install
over an existing install (Android checks signature match).

On the current dev machine the SDK is at `D:\AndroidDev\Sdk` (adb =
`D:\AndroidDev\Sdk\platform-tools\adb.exe`, not on PATH) and Gradle CLI builds work fine.
The test phone carries a **release-signed** install, so `adb install -r` of a debug APK fails
with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` — build `assembleRelease` and install that instead;
it upgrades in place and keeps the app's pairing data.

No test suite exists in any of the four components today.

## Architecture

### Pairing + auth flow
PC server generates a 6-digit PIN on startup and rotates it every `pinTtlMinutes` (10 by default).
Phone calls `POST /pair` with `{pin, deviceName}`; server validates the PIN via
`deviceStore.verifyPin()` and returns a random 32-byte hex token, persisted in
`paired-devices.json` (electron: under Electron's `userData`; server: `server/data/`). Every
subsequent request (`POST /upload`) sends that token in the `X-Device-Token` header;
`requireToken` middleware in `routes/upload.js` validates it via `deviceStore.verifyToken()`.
Tokens expire after 30 days of non-use (sliding renewal on each upload); expired entries are
pruned from the store on pair/list.

**One-to-one pairing (default):** `maxPairedDevices` (config.json / Electron settings, default 1)
caps how many devices may hold a valid token. `/pair` returns 403 once the cap is reached;
re-pairing with the same `deviceName` replaces that device's old token instead of counting as a
second device. `routes/pair.js` is a factory (`buildPairRouter(getMaxDevices)`), read per-request
so a settings change needs no restart.

**Network exposure:** mDNS advertising (`_aipoka._tcp`) is gated behind `discoverable`
(default `false`) — with it off, nothing announces the server to the WiFi and the phone pairs by
typing the address shown on the PC. `GET /status` deliberately returns only
`{ok, tlsFingerprint}` (no deviceName) so port-scanners learn nothing. CORS is restricted to
private-LAN/localhost origins (`lanOnlyCors()` in both `server/index.js` and
`electron-app/main.js`); requests with no Origin header (native apps, curl) always pass.

### Upload path resolution
`getDownloadPath()` (in both `electron-app/main.js` and `server/index.js`) = the configured base
folder + `todayFolder()` (`lib/datedPath.js`), which appends a `YYYY-MM-DD` subfolder. This is
recomputed on every request (not cached), so a folder changed mid-session (via the Electron
Browse… button, which fires the `choose-folder` IPC handler) takes effect on the next upload
without restarting the server. Duplicate uploads (same filename + matching `X-File-Size` header)
are detected in `routes/upload.js`'s multer `filename` callback and reported back as
`duplicate: true` rather than skipped outright — the size check exists to allow legitimately
different files that happen to share a name.

**Upload MIME contract:** `routes/upload.js` has a multer `fileFilter` that only accepts the
concrete types in `ALLOWED_MIME` (`image/jpeg`, `image/png`, `image/heic`, `image/heif`,
`image/webp`, `image/gif`). A literal `image/*` part content-type is rejected with 400 "Only
image uploads are allowed" — this once broke *all* Android sync silently (the phone shows no
error; `SyncWorker` swallows failures). The Android side maps file extension → concrete MIME in
`Uploader.mimeTypeFor()`; keep both lists in agreement when adding a format.

**Upload rate limit:** `uploadLimiter` in `routes/upload.js` caps at 60 uploads/min per IP.
A first full-gallery sync (thousands of photos) hits this hard: the phone burns through the rest
of the batch as fast 429 failures, the watermark only advances past successful uploads, and the
backlog drains at roughly 60 photos per 15-min periodic run. Known limitation, not a bug —
raising the cap requires rebuilding/reinstalling the receiver.

### Android sync model
No hardcoded sync-start date. `Prefs.getLastSync()`/`setLastSync()` (EncryptedSharedPreferences)
track a watermark timestamp; `SyncWorker.queryNewPhotos()` queries MediaStore for
`DATE_ADDED > lastSync` (note: MediaStore stores seconds, `Prefs` stores millis — conversion
happens at the query boundary, see `SyncWorker.kt`). The watermark starts at `0L` and is *not*
reset to "now" at pairing time — first sync after pairing uploads the entire existing
gallery/selected folder, then the watermark advances forward from there. Do not reintroduce a
"set lastSync to now at pairing" step; that was deliberately removed. `Prefs.getSyncFolder()`
optionally scopes the MediaStore query to a single `BUCKET_DISPLAY_NAME` (album), null = all
photos. **Changing the sync folder resets the watermark to 0** (in `MainActivity`'s folder
picker) — the old watermark belongs to the previous folder's timeline and keeping it would
silently skip everything older in the new selection; the PC's name+size duplicate check makes
the resulting re-uploads safe. Two trigger paths: `Scheduler.kt` schedules a ~15-min periodic
WorkManager job (Android's floor for periodic work, not tunable) plus a content-observer
`JobScheduler` trigger (`SyncTriggerJobService.kt`) for near-instant sync when a new photo
appears, and a manual "Sync Now" button. Locally-tracked send history (for the "Recently Synced"
UI list, not authoritative, just a local record) lives in
`Prefs.addUploadHistory()`/`getUploadHistory()`, capped at 100 entries, cleared on unpair.

**Stop Sync / pause model:** the main-screen button toggles Sync Now ↔ Stop Sync
(`Scheduler.syncRunning()` LiveData watches both unique work names). Stopping cancels both work
chains AND sets `Prefs.setSyncPaused(true)` — the flag is essential because the content-observer
job and a freshly re-enqueued periodic job would otherwise restart sync within seconds
(`stopSync` re-arms the periodic job with a one-period initial delay for the same reason; a new
periodic request with satisfied constraints runs immediately). While paused,
`Scheduler.triggerNow()` drops background triggers and `SyncWorker` no-ops. The pause clears only
on an explicit user action: Sync Now press (`triggerNow(fromUser = true)`) or picking a sync
folder. `triggerNow` enqueues with `ExistingWorkPolicy.REPLACE` — `KEEP` used to swallow taps
whenever stale work sat in the queue. `SyncWorker`'s upload loop checks `isStopped` between
photos (uploads are blocking OkHttp calls; coroutine cancellation alone can't interrupt them)
and persists the watermark on the way out, so a stopped sync loses no progress.

### PC-side upload log
`lib/uploadLog.js` (electron-app only currently) persists the last 100 received files
(`filename`, `deviceName`, `size`, `receivedAt`) to `upload-log.json` in userData, written from
`routes/upload.js` after a successful save. Exposed to the renderer via the `get-uploads` IPC
handler / `window.aipoka.getUploads()`. This is a local receive log, not a manifest reconciled
against what the phone actually sent — don't treat it as a source of truth for "is this photo
fully synced," just as a UI convenience.

### Electron process split
`main.js` (main process) owns the Express server, `BrowserWindow`, and all Node/filesystem access.
`preload.js` bridges a narrow `window.aipoka` API (`chooseFolder`, `getStatus`, `getUploads`) into
the renderer via `contextBridge` with `contextIsolation: true` / `nodeIntegration: false` — the
renderer (`renderer/app.js`, `renderer/index.html`, `renderer/style.css`) has no direct Node
access and polls `getStatus()`/`getUploads()` every 5s rather than using push events.

### Building for distribution
After changing anything user-facing (Electron installer, Android APK, web-app, or server-cli),
rebuild and refresh `release-package/`:
```
cd electron-app && npm run dist
cd android-app && ./gradlew.bat assembleRelease
```
then copy `electron-app/dist/Aipoka Transfer Setup 1.0.0.exe` → `release-package/windows/`, and
`android-app/app/build/outputs/apk/release/app-release.apk` → `release-package/android/`. Keep
`release-package/server-cli/config.json`'s `deviceName`/`downloadPath` generic (not the dev
machine's real name/path) and strip any personal `paired-devices.json`/log files before copying
`server/` into `release-package/server-cli/` — this bundle goes to a different physical machine.
