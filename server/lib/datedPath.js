const path = require('path');

function todayFolder(baseDir) {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return path.join(baseDir, `${y}-${m}-${d}`);
}

module.exports = { todayFolder };
