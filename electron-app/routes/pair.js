const express = require('express');
const rateLimit = require('express-rate-limit');
const deviceStore = require('../lib/deviceStore');

const pairLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 10,
  standardHeaders: true,
  legacyHeaders: false
});

// getMaxDevices() is read per-request so a settings change takes effect
// without restarting the server, matching how getDownloadPath() works.
function buildPairRouter(getMaxDevices) {
  const router = express.Router();

  router.post('/pair', pairLimiter, (req, res) => {
    const { pin, deviceName } = req.body || {};

    if (!pin || !deviceName) {
      return res.status(400).json({ error: 'pin and deviceName are required' });
    }

    if (deviceStore.isLockedOut()) {
      return res.status(429).json({ error: 'Too many failed attempts. Try again later.' });
    }

    if (!deviceStore.verifyPin(pin)) {
      return res.status(401).json({ error: 'Invalid or expired PIN' });
    }

    const token = deviceStore.issueToken(deviceName, getMaxDevices());
    if (!token) {
      return res.status(403).json({
        error: 'This receiver is already paired with another device. Remove the existing pairing on the PC first.'
      });
    }
    res.json({ token });
  });

  return router;
}

module.exports = buildPairRouter;
