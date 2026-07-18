const fs = require('fs');
const path = require('path');
const os = require('os');
const https = require('https');
const express = require('express');
const cors = require('cors');
const { Bonjour } = require('bonjour-service');
const QRCode = require('qrcode');

const deviceStore = require('./lib/deviceStore');
const buildPairRouter = require('./routes/pair');
const buildUploadRouter = require('./routes/upload');
const buildRegisterRouter = require('./routes/register');
const { todayFolder } = require('./lib/datedPath');
const tlsCert = require('./lib/tlsCert');

const configPath = path.join(__dirname, 'config.json');
const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));

tlsCert.setCertDir(path.join(__dirname, 'data'));

function getBaseDownloadPath() {
  const fresh = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  if (fresh.downloadPath && fresh.downloadPath.trim()) return fresh.downloadPath;
  return path.join(os.homedir(), 'Pictures', 'AipokaTransfer');
}

function getDownloadPath() {
  return todayFolder(getBaseDownloadPath());
}

function getMaxDevices() {
  const n = Number(config.maxPairedDevices);
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

async function main() {
  const { key, cert, fingerprint } = await tlsCert.getOrCreateCert(config.deviceName);

  const app = express();
  app.use(lanOnlyCors());
  app.use(express.json({ limit: '16kb' }));

  // Deliberately anonymous: anyone port-scanning the WiFi shouldn't learn the
  // PC's name from here. The fingerprint is already public via the TLS handshake.
  app.get('/status', (req, res) => {
    res.json({ ok: true, tlsFingerprint: fingerprint });
  });

  app.use(buildPairRouter(getMaxDevices));
  app.use(buildUploadRouter(getDownloadPath, getBaseDownloadPath));
  app.use(buildRegisterRouter());

  // Headless CLI has no window to show a QR image in, so the pairing QR is
  // rendered as scannable ASCII/unicode art straight to the terminal. Falls
  // back to printing the raw JSON payload if terminal rendering ever throws
  // (e.g. piped/non-TTY output) so pairing info is never silently lost.
  async function printPin() {
    const { pin, expiresAt } = deviceStore.getPin();
    const mins = Math.round((expiresAt - Date.now()) / 60000);
    const address = `https://${localIp()}:${config.port}`;
    const payload = JSON.stringify({ url: address, pin });

    console.log(`\nPairing PIN: ${pin}  (valid ${mins} min)`);
    console.log(`Server address: ${address}`);
    console.log(`TLS cert fingerprint (verify on phone if prompted): ${fingerprint}\n`);

    try {
      const qr = await QRCode.toString(payload, { type: 'terminal', small: true });
      console.log('Scan to pair:');
      console.log(qr);
    } catch (err) {
      console.log(`Pairing QR payload (paste into a QR generator if needed): ${payload}\n`);
    }
  }

  const server = https.createServer({ key, cert }, app);

  server.listen(config.port, () => {
    fs.mkdirSync(getDownloadPath(), { recursive: true });
    deviceStore.generatePin(config.pinTtlMinutes);
    console.log(`aipoka server running on port ${config.port} (HTTPS)`);
    console.log(`Saving photos to: ${getDownloadPath()}`);
    printPin();

    setInterval(() => {
      deviceStore.generatePin(config.pinTtlMinutes);
      printPin();
    }, config.pinTtlMinutes * 60 * 1000);

    // mDNS broadcast announces this server (and its name) to every device on
    // the WiFi. Off by default; the phone can always pair by typing the
    // address printed above. Set "discoverable": true in config.json to re-enable.
    if (config.discoverable === true) {
      const bonjour = new Bonjour();
      bonjour.publish({
        name: config.deviceName,
        type: 'aipoka',
        port: config.port
      });
      console.log('Advertising on local network via mDNS (_aipoka._tcp)');
    } else {
      console.log('mDNS discovery OFF (stealth). Enter the server address manually on the phone.');
    }
  });
}

main();
