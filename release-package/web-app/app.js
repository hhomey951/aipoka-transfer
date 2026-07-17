const pairSection = document.getElementById('pairSection');
const uploadSection = document.getElementById('uploadSection');
const pairedInfo = document.getElementById('pairedInfo');
const serverUrlInput = document.getElementById('serverUrl');
const pinInput = document.getElementById('pinInput');
const pairBtn = document.getElementById('pairBtn');
const fileInput = document.getElementById('fileInput');
const uploadBtn = document.getElementById('uploadBtn');
const forgetBtn = document.getElementById('forgetBtn');
const logEl = document.getElementById('log');

function log(msg, cls) {
  const line = document.createElement('div');
  if (cls) line.className = cls;
  line.textContent = msg;
  logEl.prepend(line);
}

function getSaved() {
  return {
    serverUrl: localStorage.getItem('aipoka_serverUrl'),
    token: localStorage.getItem('aipoka_token'),
    deviceName: localStorage.getItem('aipoka_deviceName')
  };
}

function saveSaved(serverUrl, token, deviceName) {
  localStorage.setItem('aipoka_serverUrl', serverUrl);
  localStorage.setItem('aipoka_token', token);
  localStorage.setItem('aipoka_deviceName', deviceName);
}

function clearSaved() {
  localStorage.removeItem('aipoka_serverUrl');
  localStorage.removeItem('aipoka_token');
  localStorage.removeItem('aipoka_deviceName');
}

function refreshUi() {
  const { serverUrl, token } = getSaved();
  if (serverUrl && token) {
    pairSection.style.display = 'none';
    uploadSection.style.display = 'block';
    pairedInfo.textContent = `Paired with ${serverUrl}`;
  } else {
    pairSection.style.display = 'block';
    uploadSection.style.display = 'none';
  }
}

pairBtn.addEventListener('click', async () => {
  const serverUrl = serverUrlInput.value.trim().replace(/\/$/, '');
  const pin = pinInput.value.trim();

  if (!serverUrl || !pin) {
    log('Enter server address and PIN first.', 'err');
    return;
  }

  const deviceName = `web-${navigator.platform || 'browser'}`;

  try {
    const res = await fetch(`${serverUrl}/pair`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pin, deviceName })
    });
    const data = await res.json();
    if (!res.ok) {
      log(`Pairing failed: ${data.error || res.status}`, 'err');
      return;
    }
    saveSaved(serverUrl, data.token, deviceName);
    log('Paired successfully.', 'ok');
    refreshUi();
  } catch (e) {
    log(`Could not reach server: ${e.message}. If this is the first time pairing with this PC, open ${serverUrl} directly in a new tab first and accept the certificate warning, then retry.`, 'err');
  }
});

uploadBtn.addEventListener('click', async () => {
  const { serverUrl, token } = getSaved();
  if (!serverUrl || !token) {
    log('Not paired.', 'err');
    return;
  }
  const files = fileInput.files;
  if (!files || files.length === 0) {
    log('Select at least one photo first.', 'err');
    return;
  }

  for (const file of files) {
    try {
      const form = new FormData();
      form.append('photo', file, file.name);
      const res = await fetch(`${serverUrl}/upload`, {
        method: 'POST',
        headers: {
          'X-Device-Token': token,
          'X-File-Size': String(file.size)
        },
        body: form
      });
      const data = await res.json();
      if (!res.ok) {
        log(`${file.name}: failed - ${data.error || res.status}`, 'err');
      } else {
        log(`${file.name}: sent (${data.duplicate ? 'duplicate skipped' : 'saved as ' + data.filename})`, 'ok');
      }
    } catch (e) {
      log(`${file.name}: error - ${e.message}`, 'err');
    }
  }
});

forgetBtn.addEventListener('click', () => {
  clearSaved();
  refreshUi();
  log('Pairing forgotten.');
});

refreshUi();
