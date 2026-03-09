// Modal para importação em massa de usuários e ativos via CSV
import { useState, type FormEvent, type ChangeEvent } from 'react';
import { X, Download, Upload, AlertCircle, CheckCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import { importCsv } from '../../services/api';

interface BulkImportModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

interface ImportResult {
  success: boolean;
  usersCreated: number;
  sectorsCreated: number;
  assetsCreated: number;
  categoriesCreated: number;
  errors: string[];
}

export default function BulkImportModal({
  isOpen,
  onClose,
  onSuccess,
}: BulkImportModalProps) {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<ImportResult | null>(null);

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) {
      if (!file.name.toLowerCase().endsWith('.csv')) {
        toast.error('Apenas arquivos CSV são aceitos.');
        return;
      }
      setSelectedFile(file);
      setResult(null);
    }
  }

  function downloadTemplate() {
    const csvContent = 
      'UserName,UserEmail,UserRole,SectorName,AssetName,AssetCategory,PatrimonyCode,AssetSpecs\n' +
      'João Silva,joao.silva@empresa.com,USER,TI,Notebook Dell,Laptop,NB-001,Intel i5 8GB RAM\n' +
      'Maria Santos,maria.santos@empresa.com,TECHNICIAN,Suporte,Desktop HP,Desktop,DT-002,Intel i7 16GB RAM';
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    
    link.setAttribute('href', url);
    link.setAttribute('download', 'template-importacao.csv');
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    
    toast.success('Template baixado com sucesso!');
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    
    if (!selectedFile) {
      toast.error('Selecione um arquivo CSV primeiro.');
      return;
    }

    setSubmitting(true);
    setResult(null);

    try {
      const importResult = await importCsv(selectedFile);
      setResult(importResult);
      
      if (importResult.success) {
        toast.success(`Importação concluída! ${importResult.usersCreated} usuário(s) criado(s). Senha padrão: Mudar@123`);
        onSuccess();
        setTimeout(() => {
          handleClose();
        }, 3000);
      } else {
        toast.warning('Importação concluída com erros. Verifique os detalhes.');
      }
    } catch (error) {
      const apiError = error as { response?: { data?: { message?: string } } };
      toast.error(apiError.response?.data?.message || 'Erro ao importar arquivo CSV.');
      setResult({
        success: false,
        usersCreated: 0,
        sectorsCreated: 0,
        assetsCreated: 0,
        categoriesCreated: 0,
        errors: [apiError.response?.data?.message || 'Erro desconhecido'],
      });
    } finally {
      setSubmitting(false);
    }
  }

  function handleClose() {
    setSelectedFile(null);
    setResult(null);
    onClose();
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 sticky top-0 bg-white">
          <h2 className="text-lg font-bold text-slate-800">
            Importação em Massa via CSV
          </h2>
          <button
            onClick={handleClose}
            className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="p-6 flex flex-col gap-6">
          {/* Instructions */}
          <div className="bg-brand-secondary border border-brand-primary rounded-lg p-4">
            <h3 className="font-semibold text-slate-900 mb-2">📋 Instruções</h3>
            <ul className="text-sm text-slate-700 space-y-1 list-disc list-inside">
              <li>Baixe o template CSV e preencha com seus dados</li>
              <li>Formato: UserName, UserEmail, UserRole, SectorName, AssetName, AssetCategory, PatrimonyCode, AssetSpecs</li>
              <li>UserRole aceita: ADMIN, TECHNICIAN, USER (padrão: USER)</li>
              <li>Senha padrão para todos os usuários: <strong>Mudar@123</strong></li>
              <li>Colunas de ativos são opcionais (deixe vazias se não tiver ativos)</li>
            </ul>
          </div>

          {/* Download Template Button */}
          <button
            type="button"
            onClick={downloadTemplate}
            className="flex items-center justify-center gap-2 px-4 py-2.5 bg-brand-primary hover:bg-brand-primary-dark text-white font-medium rounded-lg transition-colors"
          >
            <Download size={18} />
            Baixar Template CSV
          </button>

          {/* File Input */}
          <div className="flex flex-col gap-2">
            <label className="text-sm font-medium text-slate-700">
              Arquivo CSV *
            </label>
            <div className="relative">
              <input
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                className="block w-full text-sm text-slate-600
                  file:mr-4 file:py-2 file:px-4
                  file:rounded-lg file:border-0
                  file:text-sm file:font-medium
                  file:bg-brand-secondary file:text-slate-700
                  hover:file:bg-orange-200
                  file:cursor-pointer cursor-pointer"
              />
            </div>
            {selectedFile && (
              <p className="text-xs text-slate-600 mt-1">
                📎 Arquivo selecionado: <strong>{selectedFile.name}</strong> ({(selectedFile.size / 1024).toFixed(2)} KB)
              </p>
            )}
          </div>

          {/* Import Result */}
          {result && (
            <div className={`rounded-lg p-4 border ${
              result.success 
                ? 'bg-brand-secondary border-brand-secondary' 
                : 'bg-yellow-50 border-yellow-200'
            }`}>
              <div className="flex items-center gap-2 mb-3">
                {result.success ? (
                  <>
                    <CheckCircle size={20} className="text-orange-700" />
                    <h3 className="font-semibold text-orange-900">Importação Concluída!</h3>
                  </>
                ) : (
                  <>
                    <AlertCircle size={20} className="text-yellow-600" />
                    <h3 className="font-semibold text-yellow-900">Importação com Avisos</h3>
                  </>
                )}
              </div>
              
              <div className="grid grid-cols-2 gap-2 text-sm mb-3">
                <div className="text-slate-700">
                  ✅ <strong>{result.usersCreated}</strong> usuário(s) criado(s)
                </div>
                <div className="text-slate-700">
                  📂 <strong>{result.sectorsCreated}</strong> setor(es) criado(s)
                </div>
                <div className="text-slate-700">
                  💻 <strong>{result.assetsCreated}</strong> ativo(s) criado(s)
                </div>
                <div className="text-slate-700">
                  🏷️ <strong>{result.categoriesCreated}</strong> categoria(s) criada(s)
                </div>
              </div>

              {result.errors.length > 0 && (
                <div className="mt-3 border-t border-yellow-300 pt-3">
                  <p className="text-sm font-medium text-yellow-900 mb-2">Erros encontrados:</p>
                  <ul className="text-xs text-yellow-800 space-y-1 max-h-32 overflow-y-auto">
                    {result.errors.map((error, idx) => (
                      <li key={idx} className="bg-yellow-100 rounded px-2 py-1">
                        {error}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}

          {/* Submit Button */}
          <div className="flex gap-3 justify-end pt-4 border-t border-slate-200">
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2 text-slate-700 hover:bg-slate-100 rounded-lg font-medium transition-colors"
              disabled={submitting}
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={!selectedFile || submitting}
              className="px-4 py-2.5 bg-brand-primary hover:bg-brand-primary-dark text-white font-medium rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
            >
              {submitting ? (
                <>
                  <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  Importando...
                </>
              ) : (
                <>
                  <Upload size={18} />
                  Importar Dados
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
