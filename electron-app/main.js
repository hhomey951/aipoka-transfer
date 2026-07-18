const fs = require('fs');
const path = require('path');
const os = require('os');
const https = require('https');
const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const express = require('express');
const cors = require('cors');
const { Bonjour } = require('bonjour-service');
const QRCode = require('qrcode');

const deviceStore = require('./lib/deviceStore');
const uploadLog = require('./lib/uploadLog');
const registrationLog = require('./lib/registrationLog');
const buildPairRouter = require('./routes/pair');
const buildUploadRouter = require('./routes/upload');
const buildRegisterRouter = require('./routes/register');
const { todayFolder } = require('./lib/datedPath');
const tlsCert = require('./lib/tlsCert');

deviceStore.setDataFile(path.join(app.getPath('userData'), 'paired-devices.json'));
uploadLog.setDataFile(path.join(app.getPath('userData'), 'upload-log.json'));
registrationLog.setDataFile(path.join(app.getPath('userData'), 'registrations.json'));
tlsCert.setCertDir(app.getPath('userData'));

const SETTINGS_PATH = path.join(app.getPath('userData'), 'settings.json');

const DEFAULT_SETTINGS = {
  port: 8787,
  downloadPath: path.join(app.getPath('pictures'), 'AipokaTransfer'),
  pinTtlMinutes: 10,
  deviceName: os.hostname(),
  maxPairedDevices: 3,
  discoverable: false
};

function loadSettings() {
  if (!fs.existsSync(SETTINGS_PATH)) return { ...DEFAULT_SETTINGS };
  try {
    return { ...DEFAULT_SETTINGS, ...JSON.parse(fs.readFileSync(SETTINGS_PATH, 'utf8')) };
  } catch {
    return { ...DEFAULT_SETTINGS };
  }
}

function saveSettings(settings) {
  fs.mkdirSync(path.dirname(SETTINGS_PATH), { recursive: true });
  fs.writeFileSync(SETTINGS_PATH, JSON.stringify(settings, null, 2));
}

let settings = loadSettings();
let mainWindow = null;
let tlsFingerprint = null;

function getBaseDownloadPath() {
  return settings.downloadPath;
}

function getDownloadPath() {
  return todayFolder(getBaseDownloadPath());
}

function getMaxDevices() {
  const n = Number(settings.maxPairedDevices);
  return Number.isFinite(n) && n > 0 ? n : 1;
}

// Browsers only need CORS for the web-app, which is always served from a
// LAN/localhost address. Public internet origins (a malicious site open in a
// browser on this network) get no CORS grant, so they can't read responses or
// send preflighted requests to this server.
const PRIVATE_HOSTNAME = /^(localhost|127(\.\d{1,3}){3}|10(\.\d{1,3}){3}|192\.168(\.\d{1,3}){2}|172\.(1[6-9]|2\d|3[01])(\.\d{1,3}){2}|169\.254(\.\d{1,3}){2}|\[::1\])$/;

function lanOnlyCors() {
  return cors({
    origin(origin, cb) {
      if (!origin) return cb(null, true); // native apps / curl / same-origin
      try {
        return cb(null, PRIVATE_HOSTNAME.test(new URL(origin).hostname));
      } catch {
        return cb(null, false);
      }
    }
  });
}

// Virtual adapters (VPN clients, Hyper-V, VMware/VirtualBox, Docker) commonly
// enumerate before the real WiFi/Ethernet adapter in os.networkInterfaces(),
// and their address is unreachable from the phone's LAN. Skip known virtual
// adapter names and prefer the first plain private-LAN address instead.
const VIRTUAL_ADAPTER_NAME = /\b(vethernet|virtualbox|vmware|hyper-v|docker|tailscale|zerotier|wsl|loopback|tap-|tun|vpn)\b/i;

function localIp() {
  const nets = os.networkInterfaces();
  const candidates = [];
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        candidates.push({ name, address: net.address });
      }
    }
  }
  const real = candidates.find(c => !VIRTUAL_ADAPTER_NAME.test(c.name));
  return (real || candidates[0] || { address: '127.0.0.1' }).address;
}

async function startServer() {
  const expressApp = express();
  expressApp.use(lanOnlyCors());
  expressApp.use(express.json({ limit: '16kb' }));

  const { key, cert, fingerprint } = await tlsCert.getOrCreateCert(settings.deviceName);
  tlsFingerprint = fingerprint;

  // Deliberately anonymous: anyone port-scanning the WiFi shouldn't learn the
  // PC's name from here. The fingerprint is already public via the TLS handshake.
  expressApp.get('/status', (req, res) => {
    res.json({ ok: true, tlsFingerprint });
  });

  expressApp.use(buildPairRouter(getMaxDevices));
  expressApp.use(buildUploadRouter(getDownloadPath, getBaseDownloadPath, (entry) => uploadLog.record(entry)));
  expressApp.use(buildRegisterRouter());

  const server = https.createServer({ key, cert }, expressApp);

  server.on('error', (err) => {
    const msg = err.code === 'EADDRINUSE'
      ? `Port ${settings.port} is already in use — another Aipoka receiver (or other program) is running. Close it and restart this app.`
      : `Server failed to start: ${err.message}`;
    dialog.showErrorBox('Aipoka Transfer', msg);
  });

  server.listen(settings.port, () => {
    fs.mkdirSync(getDownloadPath(), { recursive: true });
    deviceStore.generatePin(settings.pinTtlMinutes);
    console.log(`aipoka desktop server running on port ${settings.port} (HTTPS)`);
    console.log(`TLS cert fingerprint: ${tlsFingerprint}`);

    setInterval(() => {
      deviceStore.generatePin(settings.pinTtlMinutes);
    }, settings.pinTtlMinutes * 60 * 1000);

    // mDNS broadcast announces this server (and its name) to every device on
    // the WiFi. Off by default; the phone pairs with the address shown in the
    // app window. Set "discoverable": true in settings.json to re-enable.
    if (settings.discoverable === true) {
      const bonjour = new Bonjour();
      bonjour.publish({
        name: settings.deviceName,
        type: 'aipoka',
        port: settings.port
      });
    }
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 480,
    height: 860,
    resizable: false,
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  mainWindow.setMenuBarVisibility(false);
  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

ipcMain.handle('choose-folder', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    properties: ['openDirectory', 'createDirectory']
  });
  if (result.canceled || result.filePaths.length === 0) {
    return settings.downloadPath;
  }
  settings.downloadPath = result.filePaths[0];
  saveSettings(settings);
  return settings.downloadPath;
});

ipcMain.handle('get-status', async () => {
  const { pin, expiresAt } = deviceStore.getPin();
  const devices = deviceStore.listDevices();
  const deviceList = Object.entries(devices).map(([token, info]) => ({
    token,
    deviceName: info.deviceName,
    lastSeen: info.lastSeen,
    pairedAt: info.pairedAt
  }));
  const maxDevices = getMaxDevices();

  const serverAddress = `https://${localIp()}:${settings.port}`;

  // Regenerated on every call (rather than cached) since the PIN rotates
  // every pinTtlMinutes — the QR must always reflect the currently valid PIN.
  let pairingQrDataUrl = null;
  if (pin) {
    try {
      pairingQrDataUrl = await QRCode.toDataURL(JSON.stringify({ url: serverAddress, pin }));
    } catch {
      pairingQrDataUrl = null;
    }
  }

  return {
    pin,
    pinExpiresAt: expiresAt,
    serverAddress,
    deviceName: settings.deviceName,
    downloadPath: settings.downloadPath,
    todayFolder: getDownloadPath(),
    tlsFingerprint,
    devices: deviceList,
    maxDevices,
    pairingQrDataUrl
  };
});

ipcMain.handle('remove-device', (event, token) => {
  return deviceStore.revokeToken(token);
});

ipcMain.handle('get-uploads', () => {
  return uploadLog.listEntries();
});

ipcMain.handle('clear-uploads', () => {
  uploadLog.clearEntries();
});

ipcMain.handle('get-registrations', () => {
  return registrationLog.listEntries();
});

ipcMain.handle('clear-registrations', () => {
  registrationLog.clearEntries();
});

app.whenReady().then(() => {
  startServer();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
