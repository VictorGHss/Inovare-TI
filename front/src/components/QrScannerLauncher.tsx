import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Html5Qrcode } from 'html5-qrcode';
import { Camera, QrCode, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { logQrScan } from '../services/api';

function resolveInternalPath(decodedText: string): string | null {
  const normalizedText = decodedText.trim();

  if (!normalizedText) {
    return null;
  }

  if (normalizedText.startsWith('/')) {
    return normalizedText;
  }

  try {
    const url = new URL(normalizedText, window.location.origin);
    if (url.origin !== window.location.origin) {
      return null;
    }

    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return null;
  }
}

interface Props {
  buttonLabel?: string;
}

export default function QrScannerLauncher({ buttonLabel = 'Ler QR Code' }: Props) {
  const navigate = useNavigate();
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const hasHandledResultRef = useRef(false);
  const [isOpen, setIsOpen] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const regionId = useMemo(() => `qr-reader-${Math.random().toString(36).slice(2)}`, []);

  const stopScanner = useCallback(async () => {
    const scanner = scannerRef.current;
    scannerRef.current = null;

    if (!scanner) {
      return;
    }

    try {
      if (scanner.isScanning) {
        await scanner.stop();
      }
    } catch {
      // Ignora falhas ao interromper a câmera durante o fechamento do modal.
    }

    try {
      await scanner.clear();
    } catch {
      // Ignora falhas de limpeza do DOM do scanner.
    }
  }, []);

  const handleClose = useCallback(async () => {
    await stopScanner();
    setIsOpen(false);
    setIsStarting(false);
    setCameraError(null);
  }, [stopScanner]);

  useEffect(() => {
    if (!isOpen) {
      return undefined;
    }

    let cancelled = false;
    hasHandledResultRef.current = false;

    async function startScanner() {
      setIsStarting(true);
      setCameraError(null);

      try {
        const { Html5Qrcode } = await import('html5-qrcode');
        if (cancelled) {
          return;
        }

        const scanner = new Html5Qrcode(regionId);
        scannerRef.current = scanner;

        await scanner.start(
          { facingMode: 'environment' },
          {
            fps: 10,
            qrbox: { width: 220, height: 220 },
            aspectRatio: 1,
          },
          (decodedText) => {
            if (hasHandledResultRef.current) {
              return;
            }

            hasHandledResultRef.current = true;
            const internalPath = resolveInternalPath(decodedText);

            void (async () => {
              await stopScanner();
              setIsOpen(false);

              if (internalPath) {
                toast.success('QR Code reconhecido. Abrindo conteúdo no app.');
                void logQrScan(internalPath).catch(() => {});
                navigate(internalPath);
                return;
              }

              toast.info('QR Code lido, mas ele não corresponde a uma rota interna do sistema.');
            })();
          },
          () => {
            // O callback de erro por frame é ruidoso; ignoramos para não poluir a UX.
          },
        );
      } catch {
        if (!cancelled) {
          setCameraError('Não foi possível acessar a câmera. Verifique as permissões do navegador e use HTTPS/PWA instalado.');
        }
      } finally {
        if (!cancelled) {
          setIsStarting(false);
        }
      }
    }

    void startScanner();

    return () => {
      cancelled = true;
      void stopScanner();
    };
  }, [isOpen, navigate, regionId, stopScanner]);

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="fixed bottom-6 right-6 z-30 inline-flex items-center gap-2 rounded-full bg-brand-primary px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-brand-primary/25 transition-transform duration-200 hover:scale-[1.02] hover:bg-primary-hover"
      >
        <QrCode size={18} />
        <span className="hidden sm:inline">{buttonLabel}</span>
      </button>

      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 px-4 backdrop-blur-sm">
          <div className="w-full max-w-md overflow-hidden rounded-[28px] border border-brand-secondary bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-slate-200 bg-gradient-to-r from-brand-secondary via-white to-white px-5 py-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brand-primary">Scanner PWA</p>
                <h2 className="text-lg font-semibold text-slate-800">Ler QR Code</h2>
              </div>
              <button
                onClick={() => void handleClose()}
                className="inline-flex items-center justify-center rounded-full p-2 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-800"
                aria-label="Fechar scanner"
              >
                <X size={18} />
              </button>
            </div>

            <div className="space-y-4 p-5">
              <div className="rounded-3xl border border-slate-200 bg-slate-950 p-3">
                <div id={regionId} className="min-h-[280px] overflow-hidden rounded-2xl bg-black" />
              </div>

              <div className="rounded-2xl border border-brand-secondary/70 bg-brand-secondary/35 px-4 py-3 text-sm text-slate-700">
                Aponte a câmera para um QR Code gerado pelo sistema. Links internos como
                {' '}
                <strong>/assets/uuid</strong>
                {' '}
                serão abertos sem recarregar a aplicação.
              </div>

              {isStarting && (
                <div className="inline-flex items-center gap-2 text-sm font-medium text-slate-500">
                  <Camera size={16} className="text-brand-primary" />
                  Inicializando câmera...
                </div>
              )}

              {cameraError && (
                <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                  {cameraError}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
