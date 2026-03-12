import { Paperclip, Video, X } from 'lucide-react';

interface AttachmentPreviewState {
  itemId: string;
  url: string;
  mimeType: string;
}

interface Props {
  preview: AttachmentPreviewState | null;
  onClose: () => void;
}

export default function VaultAttachmentPreview({ preview, onClose }: Props) {
  if (!preview) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center px-4">
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm w-full max-w-4xl p-4">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-slate-800">Pré-visualização do Anexo</h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
            <X size={20} />
          </button>
        </div>

        <div className="rounded-xl border border-slate-200 bg-slate-50 p-3 min-h-[320px] flex items-center justify-center">
          {preview.mimeType.startsWith('image/') ? (
            <img
              src={preview.url}
              alt="Pré-visualização do anexo do cofre"
              className="max-h-[70vh] rounded-lg"
            />
          ) : preview.mimeType.startsWith('video/') ? (
            <video src={preview.url} controls className="w-full max-h-[70vh] rounded-lg" />
          ) : (
            <div className="text-center">
              <Video className="mx-auto mb-2 text-slate-400" size={24} />
              <p className="text-sm text-slate-600 mb-3">
                Este tipo de arquivo não possui pré-visualização embutida.
              </p>
              <a
                href={preview.url}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 rounded-lg bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-3 py-2"
              >
                <Paperclip size={14} />
                Abrir arquivo
              </a>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
