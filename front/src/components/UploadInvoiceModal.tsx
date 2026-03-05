import { useState } from 'react';
import { X, Upload, AlertCircle } from 'lucide-react';
import { toast } from 'react-toastify';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onUpload: (file: File) => Promise<void>;
  entityName: string; // "Ativo" ou "Lote" para mensagens
  entityId: string; // ID para referência nas mensagens
}

const ACCEPTED_FORMATS = 'application/pdf,image/png,image/jpeg,image/jpg';
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

export default function UploadInvoiceModal({
  isOpen,
  onClose,
  onUpload,
  entityName,
  entityId,
}: Props) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);

  if (!isOpen) return null;

  function handleFileSelect(file: File | null) {
    if (!file) {
      setSelectedFile(null);
      return;
    }

    // Validações
    if (file.size > MAX_FILE_SIZE) {
      toast.error('Arquivo excede o tamanho máximo de 5MB.');
      return;
    }

    const validTypes = ['application/pdf', 'image/png', 'image/jpeg', 'image/jpg'];
    if (!validTypes.includes(file.type)) {
      toast.error('Tipo de arquivo não permitido. Aceitos: PDF, PNG, JPG.');
      return;
    }

    setSelectedFile(file);
  }

  function handleInputChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    handleFileSelect(file || null);
  }

  function handleDragOver(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(true);
  }

  function handleDragLeave(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);

    const file = e.dataTransfer.files?.[0];
    handleFileSelect(file || null);
  }

  async function handleSubmit() {
    if (!selectedFile) {
      toast.error('Selecione um arquivo para fazer o upload.');
      return;
    }

    setUploading(true);
    try {
      await onUpload(selectedFile);
      toast.success(`Nota fiscal anexada com sucesso ao ${entityName.toLowerCase()}!`);
      setSelectedFile(null);
      onClose();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Erro desconhecido';
      toast.error(`Erro ao fazer upload: ${message}`);
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
      <div className="bg-white rounded-xl shadow-lg w-full max-w-md mx-4">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <h2 className="text-lg font-bold text-slate-800">Anexar Nota Fiscal</h2>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 transition-colors"
            aria-label="Fechar modal"
          >
            <X size={20} />
          </button>
        </div>

        {/* Body */}
        <div className="px-6 py-6">
          <p className="text-sm text-slate-600 mb-4">
            Anexe a nota fiscal (PDF ou imagem) para o {entityName.toLowerCase()} ID:{' '}
            <span className="font-semibold text-slate-800">{entityId}</span>
          </p>

          {/* Alert Info */}
          <div className="flex gap-3 bg-blue-50 border border-blue-200 rounded-lg p-3 mb-6">
            <AlertCircle size={18} className="text-blue-600 flex-shrink-0 mt-0.5" />
            <p className="text-xs text-blue-700">
              Máximo de 5MB. Formatos aceitos: PDF, PNG, JPG.
            </p>
          </div>

          {/* Drag & Drop Area */}
          <div
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            className={`border-2 border-dashed rounded-lg p-6 text-center transition-colors cursor-pointer ${
              isDragOver
                ? 'border-primary bg-primary bg-opacity-5'
                : 'border-slate-300 hover:border-primary hover:bg-slate-50'
            }`}
          >
            <div className="flex flex-col items-center gap-2">
              <Upload
                size={32}
                className={isDragOver ? 'text-primary' : 'text-slate-400'}
              />
              <div>
                <p className="text-sm font-medium text-slate-800">
                  Arraste o arquivo aqui
                </p>
                <p className="text-xs text-slate-500 mt-1">ou clique para procurar</p>
              </div>
            </div>
            <input
              type="file"
              accept={ACCEPTED_FORMATS}
              onChange={handleInputChange}
              className="absolute inset-0 opacity-0 cursor-pointer"
              aria-label="Selecionar arquivo"
            />
          </div>

          {/* Selected File */}
          {selectedFile && (
            <div className="mt-4 p-3 bg-green-50 border border-green-200 rounded-lg">
              <p className="text-xs text-green-700 font-medium">Arquivo selecionado:</p>
              <p className="text-sm text-green-800 font-semibold mt-1 break-words">
                {selectedFile.name}
              </p>
              <p className="text-xs text-green-600 mt-1">
                {(selectedFile.size / 1024).toFixed(2)} KB
              </p>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 border-t border-slate-200 px-6 py-4">
          <button
            onClick={onClose}
            disabled={uploading}
            className="px-4 py-2 text-sm font-medium text-slate-600 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
          >
            Cancelar
          </button>
          <button
            onClick={handleSubmit}
            disabled={!selectedFile || uploading}
            className="px-4 py-2 text-sm font-semibold text-white bg-primary hover:bg-primary-hover rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {uploading ? 'Enviando...' : 'Anexar'}
          </button>
        </div>
      </div>
    </div>
  );
}
