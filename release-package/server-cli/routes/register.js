const express = require('express');
const rateLimit = require('express-rate-limit');
const registrationLog = require('../lib/registrationLog');

// Generous cap: this is a one-time call per app install/open, not a per-upload
// hot path, but still rate-limited so a stray script can't flood the log.
const registerLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 20,
  standardHeaders: true,
  legacyHeaders: false
});

const MAX_FIELD_LENGTH = 200;
const EMAIL_RE = /^[^\s@]+@[^\s@]+$/; // loose check only: contains an @, no whitespace

function buildRegisterRouter() {
  const router = express.Router();

  // NOT authenticated: this is a pre-pairing, one-time install-tracking log,
  // not a real account system. LAN-only, own machine, no external transmission.
  router.post('/register', registerLimiter, (req, res) => {
    const { name, email, deviceName } = req.body || {};

    if (typeof name !== 'string' || !name.trim() || name.length > MAX_FIELD_LENGTH) {
      return res.status(400).json({ error: 'name is required' });
    }
    if (typeof email !== 'string' || !email.trim() || email.length > MAX_FIELD_LENGTH || !EMAIL_RE.test(email.trim())) {
      return res.status(400).json({ error: 'a valid email is required' });
    }
    if (deviceName !== undefined && deviceName !== null && (typeof deviceName !== 'string' || deviceName.length > MAX_FIELD_LENGTH)) {
      return res.status(400).json({ error: 'deviceName is invalid' });
    }

    registrationLog.addEntry({
      name: name.trim(),
      email: email.trim(),
      deviceName: deviceName ? deviceName.trim() : null
    });

    res.json({ ok: true });
  });

  return router;
}

module.exports = buildRegisterRouter;
