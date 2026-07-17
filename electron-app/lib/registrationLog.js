const fs = require('fs');
const path = require('path');

const MAX_ENTRIES = 500;

let DATA_FILE = path.join(__dirname, '..', 'data', 'registrations.json');

function setDataFile(filePath) {
  DATA_FILE = filePath;
}

function loadEntries() {
  if (!fs.existsSync(DATA_FILE)) return [];
  try {
    return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  } catch {
    return [];
  }
}

function saveEntries(entries) {
  fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
  fs.writeFileSync(DATA_FILE, JSON.stringify(entries, null, 2));
}

function addEntry({ name, email, deviceName }) {
  const entries = loadEntries();
  entries.unshift({
    name,
    email,
    deviceName: deviceName || null,
    registeredAt: new Date().toISOString()
  });
  saveEntries(entries.slice(0, MAX_ENTRIES));
}

function listEntries() {
  return loadEntries();
}

module.exports = { setDataFile, addEntry, listEntries };
