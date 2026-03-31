import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

// Service Worker: verificar atualizações e solicitar skipWaiting para ativar o novo SW imediatamente.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', async () => {
    try {
      const reg = await navigator.serviceWorker.getRegistration();
      if (reg) {
        // If a waiting worker is present, ask it to skip waiting
        if (reg.waiting) {
          try {
            reg.waiting.postMessage({ type: 'SKIP_WAITING' });
          } catch {
            // ignorar
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
                } catch {
                  // ignorar
                }
              }
            });
          }
        });
      }
    } catch (e) {
      // o registro pode falhar em alguns ambientes
      // manter o app funcionando sem forçar atualização do SW
      console.warn('Falha na verificação de registro do SW', e);
    }

    // Quando um novo SW assumir o controle, recarregar para garantir uso dos novos assets
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      window.location.reload();
    });
  });
}
