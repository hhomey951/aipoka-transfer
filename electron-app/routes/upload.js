const express = require('express');
const fs = require('fs');
const path = require('path');
const multer = require('multer');
const rateLimit = require('express-rate-limit');
const deviceStore = require('../lib/deviceStore');

const router = express.Router();

const uploadLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 60, // generous for legit burst-upload of a gallery, still caps abuse from a leaked token
  standardHeaders: true,
  legacyHeaders: false
});

const ALLOWED_MIME = new Set(['image/jpeg', 'image/png', 'image/heic', 'image/heif', 'image/webp', 'image/gif']);

function requireToken(req, res, next) {
  const token = req.header('X-Device-Token');
  if (!deviceStore.verifyToken(token)) {
    return res.status(401).json({ error: 'Invalid or missing device token' });
  }
  deviceStore.touchToken(token);
  req.deviceToken = token;
  const entry = deviceStore.listDevices()[token];
  req.deviceName = (entry && entry.deviceName) || 'unknown';
  next();
}

function sanitizeFilename(name) {
  const base = path.basename(name).replace(/[^a-zA-Z0-9._-]/g, '_');
  return base || `photo_${Date.now()}.jpg`;
}

// Folder names come from the phone percent-encoded (header values must be
// ASCII). After decoding, strip anything that could escape the base directory
// (path separators, leading dots) or is illegal in a Windows filename.
function sanitizeFolderName(raw) {
  if (!raw) return null;
  let name;
  try {
    name = decodeURIComponent(String(raw));
  } catch {
    return null;
  }
  const cleaned = path.basename(name)
    .replace(/[<>:"/\\|?*\x00-\x1f]/g, '_')
    .replace(/^\.+/, '')
    .trim();
  if (!cleaned) return null;
  return cleaned.slice(0, 64);
}

function makeUpload(getDownloadPath, getBasePath) {
  // X-Target-Folder saves into <base>/<folder> instead of the dated folder.
  const resolveDir = (req) => {
    const folder = sanitizeFolderName(req.header('X-Target-Folder'));
    return folder ? path.join(getBasePath(), folder) : getDownloadPath();
  };

  const storage = multer.diskStorage({
    destination: (req, file, cb) => {
      const dir = resolveDir(req);
      fs.mkdirSync(dir, { recursive: true });
      cb(null, dir);
    },
    filename: (req, file, cb) => {
      const dir = resolveDir(req);
      let safeName = sanitizeFilename(file.originalname);
      const dest = path.join(dir, safeName);

      if (fs.existsSync(dest)) {
        const existingSize = fs.statSync(dest).size;
        const incomingSize = Number(req.header('X-File-Size') || 0);
        if (incomingSize && existingSize === incomingSize) {
          req.duplicateSkip = safeName;
        } else {
          const ext = path.extname(safeName);
          const stem = path.basename(safeName, ext);
          safeName = `${stem}_${Date.now()}${ext}`;
        }
      }
      cb(null, safeName);
    }
  });

  const fileFilter = (req, file, cb) => {
    if (!ALLOWED_MIME.has(file.mimetype)) {
      return cb(new Error('Only image uploads are allowed'));
    }
    cb(null, true);
  };

  return multer({ storage, fileFilter, limits: { fileSize: 200 * 1024 * 1024 } });
}

// onSaved (optional) is called after each successfully stored file — the
// Electron app uses it to feed the "Recently received" upload log; the
// headless CLI passes nothing.
function buildUploadRouter(getDownloadPath, getBasePath, onSaved) {
  const upload = makeUpload(getDownloadPath, getBasePath);

  router.post('/upload', uploadLimiter, requireToken, (req, res, next) => {
    upload.single('photo')(req, res, (err) => {
      if (err) return res.status(400).json({ error: err.message });
      next();
    });
  }, (req, res) => {
    if (!req.file) {
      return res.status(400).json({ error: 'No file uploaded (field name must be "photo")' });
    }
    if (typeof onSaved === 'function' && !req.duplicateSkip) {
      try {
        onSaved({
          filename: req.file.filename,
          deviceName: req.deviceName,
          size: req.file.size,
          savedPath: req.file.path
        });
      } catch {} // log failure must never fail the upload itself
    }
    res.json({
      ok: true,
      filename: req.file.filename,
      size: req.file.size,
      duplicate: Boolean(req.duplicateSkip)
    });
  });

  return router;
}

module.exports = buildUploadRouter;
