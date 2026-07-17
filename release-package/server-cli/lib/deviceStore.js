const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

let DATA_FILE = path.join(__dirname, '..', 'data', 'paired-devices.json');
let KEY_FILE = path.join(__dirname, '..', 'data', '.devicekey');

// Headless server has no OS keychain binding available (no Electron safeStorage),
// so this key just makes paired-devices.json opaque to a casual file copy/backup
// leak. Anyone with code-execution on this machine can still read KEY_FILE.
function getOrCreateKey() {
  fs.mkdirSync(path.dirname(KEY_FILE), { recursive: true });
  if (fs.existsSync(KEY_FILE)) return fs.readFileSync(KEY_FILE);
  const key = crypto.randomBytes(32);
  fs.writeFileSync(KEY_FILE, key, { mode: 0o600 });
  return key;
}

function encrypt(json) {
  const key = getOrCreateKey();
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ciphertext = Buffer.concat([cipher.update(json, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return JSON.stringify({ iv: iv.toString('hex'), tag: tag.toString('hex'), data: ciphertext.toString('hex') });
}

function decrypt(blob) {
  const key = getOrCreateKey();
  const { iv, tag, data } = JSON.parse(blob);
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, Buffer.from(iv, 'hex'));
  decipher.setAuthTag(Buffer.from(tag, 'hex'));
  const plaintext = Buffer.concat([decipher.update(Buffer.from(data, 'hex')), decipher.final()]);
  return plaintext.toString('utf8');
}

function setDataFile(filePath) {
  DATA_FILE = filePath;
  KEY_FILE = path.join(path.dirname(filePath), '.devicekey');
}

function loadDevices() {
  if (!fs.existsSync(DATA_FILE)) return {};
  try {
    return JSON.parse(decrypt(fs.readFileSync(DATA_FILE, 'utf8')));
  } catch {
    return {};
  }
}

function saveDevices(devices) {
  fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
  fs.writeFileSync(DATA_FILE, encrypt(JSON.stringify(devices, null, 2)), { mode: 0o600 });
}

const TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days, sliding (refreshed on use)
const MAX_PIN_ATTEMPTS = 5;
const LOCKOUT_MS = 5 * 60 * 1000; // 5 min

let currentPin = null;
let pinExpiresAt = 0;
let failedAttempts = 0;
let lockedUntil = 0;

function generatePin(ttlMinutes) {
  currentPin = String(crypto.randomInt(0, 1000000)).padStart(6, '0');
  pinExpiresAt = Date.now() + ttlMinutes * 60 * 1000;
  failedAttempts = 0;
  lockedUntil = 0;
  return currentPin;
}

function getPin() {
  return { pin: currentPin, expiresAt: pinExpiresAt, expired: Date.now() > pinExpiresAt };
}

function timingSafeEqual(a, b) {
  const bufA = Buffer.from(String(a));
  const bufB = Buffer.from(String(b));
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function isLockedOut() {
  return Date.now() < lockedUntil;
}

function verifyPin(pin) {
  if (isLockedOut()) return false;
  const ok = currentPin !== null && typeof pin === 'string' && timingSafeEqual(pin, currentPin) && Date.now() <= pinExpiresAt;
  if (!ok) {
    failedAttempts += 1;
    if (failedAttempts >= MAX_PIN_ATTEMPTS) lockedUntil = Date.now() + LOCKOUT_MS;
    return false;
  }
  failedAttempts = 0;
  return true;
}

function pruneExpired(devices) {
  const now = Date.now();
  for (const [token, entry] of Object.entries(devices)) {
    if (entry.expiresAt && now > entry.expiresAt) delete devices[token];
  }
  return devices;
}

// maxDevices caps how many phones may hold a valid token at once (default 1 =
// strict one-to-one). Re-pairing from the same deviceName replaces its old
// token instead of counting as a second device — that path is still gated by
// the current PIN shown on the PC screen. Returns null when the cap is hit.
function issueToken(deviceName, maxDevices = 1) {
  const devices = pruneExpired(loadDevices());
  for (const [existing, entry] of Object.entries(devices)) {
    if (entry.deviceName === deviceName) delete devices[existing];
  }
  if (maxDevices > 0 && Object.keys(devices).length >= maxDevices) return null;
  const token = crypto.randomBytes(32).toString('hex');
  const now = Date.now();
  devices[token] = {
    deviceName,
    pairedAt: new Date(now).toISOString(),
    lastSeen: new Date(now).toISOString(),
    expiresAt: now + TOKEN_TTL_MS
  };
  saveDevices(devices);
  return token;
}

function verifyToken(token) {
  if (!token) return false;
  const devices = loadDevices();
  const entry = devices[token];
  if (!entry) return false;
  if (entry.expiresAt && Date.now() > entry.expiresAt) return false;
  return true;
}

function touchToken(token) {
  const devices = loadDevices();
  if (devices[token]) {
    devices[token].lastSeen = new Date().toISOString();
    devices[token].expiresAt = Date.now() + TOKEN_TTL_MS; // sliding renewal on use
    saveDevices(devices);
  }
}

function revokeToken(token) {
  const devices = loadDevices();
  if (devices[token]) {
    delete devices[token];
    saveDevices(devices);
    return true;
  }
  return false;
}

function listDevices() {
  return pruneExpired(loadDevices());
}

module.exports = {
  setDataFile,
  generatePin,
  getPin,
  verifyPin,
  isLockedOut,
  issueToken,
  verifyToken,
  touchToken,
  revokeToken,
  listDevices
};
