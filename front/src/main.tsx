import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import { loadAppConfig } from './api/appConfig';
import { getMaxWebApp } from './api/maxBridge';
import { getTelegramWebApp } from './api/telegramBridge';
import { getNativeDarkMode } from './api/native';

const maxWebApp = getMaxWebApp();
const telegramWebApp = getTelegramWebApp();
if (maxWebApp?.initData) {
  maxWebApp.ready?.();
} else if (telegramWebApp?.initData) {
  telegramWebApp.ready?.();
  telegramWebApp.expand?.();
}

// Fire-and-forget — bot username is needed once for QR deep links etc.
loadAppConfig().catch(() => {});

function render() {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}

// Android app: learn the system night mode before the first paint so the theme
// doesn't flash light → dark (the native splash covers this short wait).
let rendered = false;
function renderOnce() {
  if (rendered) return;
  rendered = true;
  render();
}
getNativeDarkMode()
  .then((dark) => {
    if (dark !== null) (window as { __JOIN_NATIVE_DARK__?: boolean }).__JOIN_NATIVE_DARK__ = dark;
  })
  .catch(() => {})
  .finally(renderOnce);
// Never keep the user on the splash if the native call is slow.
setTimeout(renderOnce, 1500);
