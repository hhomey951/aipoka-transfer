const fs = require('fs');
const path = require('path');

const MAX_ENTRIES = 100;

let DATA_FILE = path.join(__dirname, '..', 'data', 'upload-log.json');

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

function record({ filename, deviceName, size, savedPath }) {
  const entries = loadEntries();
  entries.unshift({
    filename,
    deviceName,
    size,
    savedPath,
    receivedAt: new Date().toISOString()
  });
  saveEntries(entries.slice(0, MAX_ENTRIES));
}

function listEntries() {
  return loadEntries();
}

function clearEntries() {
  saveEntries([]);
}

module.exports = { setDataFile, record, listEntries, clearEntries };
