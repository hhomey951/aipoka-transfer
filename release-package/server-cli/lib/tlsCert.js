const fs = require('fs');
const path = require('path');
const os = require('os');
const selfsigned = require('selfsigned');

let CERT_DIR = path.join(__dirname, '..', 'data');

function setCertDir(dir) {
  CERT_DIR = dir;
}

function localIps() {
  const ips = [];
  const nets = os.networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) ips.push(net.address);
    }
  }
  return ips;
}

// Cert is persisted so it stays stable across restarts — Android TOFU pinning
// and any browser exception the user clicked through would otherwise break
// on every app launch.
async function getOrCreateCert(deviceName) {
  const keyPath = path.join(CERT_DIR, 'tls-key.pem');
  const certPath = path.join(CERT_DIR, 'tls-cert.pem');
  const fpPath = path.join(CERT_DIR, 'tls-fingerprint.txt');

  if (fs.existsSync(keyPath) && fs.existsSync(certPath) && fs.existsSync(fpPath)) {
    return {
      key: fs.readFileSync(keyPath, 'utf8'),
      cert: fs.readFileSync(certPath, 'utf8'),
      fingerprint: fs.readFileSync(fpPath, 'utf8').trim()
    };
  }

  const attrs = [{ name: 'commonName', value: deviceName || 'aipoka-local' }];
  const altNames = [
    { type: 2, value: 'localhost' },
    { type: 7, ip: '127.0.0.1' },
    ...localIps().map((ip) => ({ type: 7, ip }))
  ];
  const pems = await selfsigned.generate(attrs, {
    keySize: 2048,
    algorithm: 'sha256',
    notAfterDate: new Date(Date.now() + 3650 * 24 * 60 * 60 * 1000),
    extensions: [{ name: 'subjectAltName', altNames }]
  });

  fs.mkdirSync(CERT_DIR, { recursive: true });
  fs.writeFileSync(keyPath, pems.private, { mode: 0o600 });
  fs.writeFileSync(certPath, pems.cert, { mode: 0o600 });
  fs.writeFileSync(fpPath, pems.fingerprint, { mode: 0o600 });

  return { key: pems.private, cert: pems.cert, fingerprint: pems.fingerprint };
}

module.exports = { setCertDir, getOrCreateCert };
