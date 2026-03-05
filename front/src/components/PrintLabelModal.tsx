import { X, Printer } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import type { Asset } from '../services/api';

interface PrintLabelModalProps {
  isOpen: boolean;
  onClose: () => void;
  asset: Asset;
}

export default function PrintLabelModal({ isOpen, onClose, asset }: PrintLabelModalProps) {
  if (!isOpen) return null;

  const qrCodeValue = `${window.location.origin}/assets/${asset.id}`;

  function handlePrint() {
    window.print();
  }

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        {/* Header - esconder na impressão */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 print:hidden">
          <h2 className="text-lg font-bold text-slate-800">Etiqueta do Ativo</h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Conteúdo da etiqueta - centralizar na impressão */}
        <div className="p-8 flex flex-col items-center print:p-0 print:m-0 print:h-screen print:justify-center">
          {/* Etiqueta */}
          <div
            id="label-content"
            className="border-4 border-slate-800 rounded-lg p-8 bg-white flex flex-col items-center gap-4 shadow-sm print:shadow-none print:border-2"
            style={{ width: '320px' }}
          >
            {/* Cabeçalho */}
            <div className="text-center">
              <h3 className="text-lg font-bold text-slate-900 uppercase tracking-wide">
                Inovare TI
              </h3>
              <p className="text-xs text-slate-600 font-medium mt-0.5">Patrimônio</p>
            </div>

            {/* QR Code */}
            <div className="bg-white p-3 rounded-lg border border-slate-200">
              <QRCodeSVG value={qrCodeValue} size={128} level="H" />
            </div>

            {/* Dados do Ativo */}
            <div className="text-center w-full">
              <p className="text-base font-bold text-slate-900 mb-1 break-words">
                {asset.name}
              </p>
              <p className="text-sm font-semibold text-slate-700 bg-slate-100 px-3 py-1.5 rounded-md inline-block">
                {asset.patrimonyCode}
              </p>
            </div>
          </div>

          {/* Botão imprimir - esconder na impressão */}
          <button
            onClick={handlePrint}
            className="mt-6 flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors print:hidden"
          >
            <Printer size={16} />
            Imprimir Etiqueta
          </button>
        </div>
      </div>

      {/* Estilos de impressão */}
      <style>{`
        @media print {
          body * {
            visibility: hidden;
          }
          #label-content,
          #label-content * {
            visibility: visible;
          }
          #label-content {
            position: fixed;
            left: 50%;
            top: 50%;
            transform: translate(-50%, -50%);
          }
          @page {
            size: auto;
            margin: 20mm;
          }
        }
      `}</style>
    </div>
  );
}
