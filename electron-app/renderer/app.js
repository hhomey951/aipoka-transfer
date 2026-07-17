const downloadPathInput = document.getElementById('downloadPath');
const todayFolderHint = document.getElementById('todayFolderHint');
const browseBtn = document.getElementById('browseBtn');
const serverAddress = document.getElementById('serverAddress');
const tlsFingerprintEl = document.getElementById('tlsFingerprint');
const deviceNameEl = document.getElementById('deviceName');
const pinEl = document.getElementById('pin');
const pinExpiry = document.getElementById('pinExpiry');
const pairingQrEl = document.getElementById('pairingQr');
const deviceListEl = document.getElementById('deviceList');
const uploadListEl = document.getElementById('uploadList');
const registrationListEl = document.getElementById('registrationList');

// Device names, filenames, and registration name/email all originate from the
// phone (and /register is unauthenticated), so treat them as untrusted before
// interpolating into innerHTML.
function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function relativeTime(iso) {
  const diffMs = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diffMs / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

function isOnline(iso) {
  return Date.now() - new Date(iso).getTime() < 2 * 60 * 1000;
}

function render(status) {
  downloadPathInput.value = status.downloadPath;
  todayFolderHint.textContent = `Today's photos go in: ${status.todayFolder}`;
  serverAddress.textContent = status.serverAddress;
  tlsFingerprintEl.textContent = status.tlsFingerprint || '-';
  deviceNameEl.textContent = status.deviceName;
  pinEl.textContent = status.pin || '------';

  if (status.pinExpiresAt) {
    const mins = Math.max(0, Math.round((status.pinExpiresAt - Date.now()) / 60000));
    pinExpiry.textContent = `valid ${mins} min`;
  }

  if (status.pairingQrDataUrl) {
    pairingQrEl.src = status.pairingQrDataUrl;
    pairingQrEl.hidden = false;
  } else {
    pairingQrEl.hidden = true;
  }

  if (!status.devices || status.devices.length === 0) {
    deviceListEl.innerHTML = '<div class="empty">No devices paired yet.</div>';
    return;
  }

  deviceListEl.innerHTML = status.devices
    .map((d) => {
      const online = isOnline(d.lastSeen);
      return `
        <div class="deviceRow">
          <div class="dot ${online ? 'online' : ''}"></div>
          <div class="deviceName">${esc(d.deviceName)}</div>
          <div class="lastSeen">${relativeTime(d.lastSeen)}</div>
        </div>
      `;
    })
    .join('');
}

function renderUploads(uploads) {
  if (!uploads || uploads.length === 0) {
    uploadListEl.innerHTML = '<div class="empty">No photos received yet.</div>';
    return;
  }

  uploadListEl.innerHTML = uploads
    .slice(0, 10)
    .map((u) => `
      <div class="uploadRow">
        <div class="uploadName" title="${esc(u.filename)}">${esc(u.filename)}</div>
        <div class="uploadFrom">${esc(u.deviceName)}</div>
        <div class="uploadTime">${relativeTime(u.receivedAt)}</div>
      </div>
    `)
    .join('');
}

function renderRegistrations(registrations) {
  if (!registrations || registrations.length === 0) {
    registrationListEl.innerHTML = '<div class="empty">No installs yet.</div>';
    return;
  }

  registrationListEl.innerHTML = registrations
    .slice(0, 20)
    .map((r) => `
      <div class="registrationRow">
        <div class="registrationMain">
          <div class="registrationName" title="${esc(r.name)}">${esc(r.name)}</div>
          <div class="registrationMeta" title="${esc(r.email)}${r.deviceName ? ' · ' + esc(r.deviceName) : ''}">${esc(r.email)}${r.deviceName ? ' · ' + esc(r.deviceName) : ''}</div>
        </div>
        <div class="registrationTime">${relativeTime(r.registeredAt)}</div>
      </div>
    `)
    .join('');
}

async function refresh() {
  const status = await window.aipoka.getStatus();
  render(status);
  const uploads = await window.aipoka.getUploads();
  renderUploads(uploads);
  const registrations = await window.aipoka.getRegistrations();
  renderRegistrations(registrations);
}

browseBtn.addEventListener('click', async () => {
  await window.aipoka.chooseFolder();
  refresh();
});

refresh();
setInterval(refresh, 5000);
