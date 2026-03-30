import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Service Worker: check for updates and request skipWaiting to activate new SW immediately.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      if (reg) {
        // If a waiting worker is present, ask it to skip waiting
        if (reg.waiting) {
          try {
            reg.waiting.postMessage({ type: 'SKIP_WAITING' });
          } catch (e) {
            // ignore
          }
        }

        // Listen for a new worker being installed and request skipWaiting when ready
        reg.addEventListener('updatefound', () => {
          const newWorker = reg.installing;
          if (newWorker) {
            newWorker.addEventListener('statechange', () => {
              if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                try {
                  newWorker.postMessage({ type: 'SKIP_WAITING' });
                } catch (e) {
                  // ignore
                }
              }
            });
          }
        });
      }
    } catch (e) {
      // registration may fail in some environments
      // keep app running without SW update enforcement
      // eslint-disable-next-line no-console
      console.warn('SW registration check failed', e);
    }

    // When a new SW takes control, reload to ensure clients use the new assets
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      window.location.reload();
    });
  });
}
