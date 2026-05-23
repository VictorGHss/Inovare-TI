import { useEffect, useState } from 'react';
import { Loader2, RefreshCw, Trash2, Download, Database, AlertTriangle } from 'lucide-react';
import { toast } from 'react-toastify';
import api from '../../services/api';

interface BackupInfo {
  filename: string;
  sizeBytes: number;
  lastModified: string;
}

export default function BackupsSection() {
  const [backups, setBackups] = useState<BackupInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [triggering, setTriggering] = useState(false);
  const [actionId, setActionId] = useState<string | null>(null);

  const loadBackups = async () => {
    try {
      setLoading(true);
      const { data } = await api.get<BackupInfo[]>('/admin/backups');
      setBackups(Array.isArray(data) ? data : []);
    } catch {
      toast.error('Erro ao carregar lista de backups.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadBackups();
  }, []);

  async function handleTriggerBackup() {
    setTriggering(true);
    try {
      await api.post('/admin/backups/trigger');
      toast.success('Backup gerado com sucesso.');
      await loadBackups();
    } catch (err) {
      const error = err as { response?: { data?: { detail?: string } } };
      const msg = error.response?.data?.detail || 'Erro ao gerar backup de banco de dados.';
      toast.error(msg);
    } finally {
      setTriggering(false);
    }
  }

  async function handleDownload(filename: string) {
    setActionId(filename);
    try {
      const response = await api.get(`/admin/backups/download/${filename}`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      toast.success('Download iniciado.');
    } catch {
      toast.error('Falha ao baixar arquivo de backup.');
    } finally {
      setActionId(null);
    }
  }

  async function handleDelete(filename: string) {
    if (!confirm(`Confirma a exclusão permanente do backup "${filename}" do servidor?`)) return;

    setActionId(filename);
    try {
      await api.delete(`/admin/backups/${filename}`);
      setBackups((prev) => prev.filter((b) => b.filename !== filename));
      toast.success('Backup excluído com sucesso.');
    } catch {
      toast.error('Falha ao excluir arquivo de backup.');
    } finally {
      setActionId(null);
    }
  }

  function formatBytes(bytes: number) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  function formatTimestamp(isoString: string) {
    try {
      const date = new Date(isoString);
      return date.toLocaleString('pt-BR');
    } catch {
      return isoString;
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
      <header className="px-6 py-5 border-b border-slate-100 bg-slate-50/50 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
            <Database size={18} className="text-brand-primary" />
            Gerenciamento de Backups
          </h2>
          <p className="text-xs text-slate-500 mt-0.5">
            Crie backups imediatos do banco de dados, faça download de backups físicos em ZIP e delete arquivos antigos.
          </p>
        </div>

        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => {
              void loadBackups();
            }}
            disabled={loading || triggering}
            className="p-2.5 rounded-xl border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-all disabled:opacity-50"
            title="Atualizar Lista"
          >
            <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
          </button>

          <button
            type="button"
            onClick={() => {
              void handleTriggerBackup();
            }}
            disabled={triggering || loading}
            className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:opacity-60"
          >
            {triggering ? <Loader2 size={16} className="animate-spin" /> : <Database size={16} />}
            {triggering ? 'Gerando Backup...' : 'Fazer Backup Agora'}
          </button>
        </div>
      </header>

      <div className="p-6">
        {loading && backups.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-slate-400 gap-2">
            <Loader2 size={24} className="animate-spin text-brand-primary" />
            <p className="text-sm">Carregando histórico de backups do servidor...</p>
          </div>
        ) : backups.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 border-2 border-dashed border-slate-100 rounded-2xl bg-slate-50/50">
            <Database size={36} className="text-slate-300 mb-2" />
            <p className="text-sm font-semibold text-slate-600">Nenhum backup físico localizado</p>
            <p className="text-xs text-slate-400 mt-1 max-w-sm text-center">
              Você pode clicar em &quot;Fazer Backup Agora&quot; acima para forçar a criação imediata de um snapshot ZIP.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-slate-150">
            <table className="min-w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                    Nome do Arquivo
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                    Tamanho Compactado
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                    Data de Criação
                  </th>
                  <th className="px-4 py-3 text-center text-xs font-semibold uppercase tracking-wider text-slate-500 w-[200px]">
                    Ações
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {backups.map((row) => (
                  <tr key={row.filename} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-4 py-3.5 font-mono text-xs text-slate-800 font-medium">
                      {row.filename}
                    </td>
                    <td className="px-4 py-3.5 text-slate-600 font-semibold">
                      {formatBytes(row.sizeBytes)}
                    </td>
                    <td className="px-4 py-3.5 text-slate-500">
                      {formatTimestamp(row.lastModified)}
                    </td>
                    <td className="px-4 py-3.5 text-center">
                      <div className="flex justify-center gap-2">
                        <button
                          type="button"
                          onClick={() => void handleDownload(row.filename)}
                          disabled={actionId === row.filename}
                          className="inline-flex items-center gap-1 rounded-lg bg-indigo-50 px-2.5 py-1.5 text-xs font-semibold text-indigo-700 hover:bg-indigo-100 transition-colors disabled:opacity-50"
                        >
                          {actionId === row.filename ? (
                            <Loader2 size={13} className="animate-spin" />
                          ) : (
                            <Download size={13} />
                          )}
                          Baixar
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleDelete(row.filename)}
                          disabled={actionId === row.filename}
                          className="inline-flex items-center gap-1 rounded-lg bg-red-50 px-2.5 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-100 transition-colors disabled:opacity-50"
                        >
                          <Trash2 size={13} />
                          Excluir
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="mt-6 rounded-2xl border border-amber-100 bg-amber-50/30 p-4 flex gap-3">
          <AlertTriangle size={18} className="text-amber-500 shrink-0 mt-0.5" />
          <div>
            <h4 className="text-xs font-bold text-amber-800">Atenção e Segurança</h4>
            <p className="text-xs text-amber-700/90 mt-0.5 leading-relaxed">
              O diretório de backups (<code>/app/backups</code>) está mapeado para um armazenamento persistente e seguro. No entanto, é recomendável manter uma rotatividade regular e não acumular arquivos excessivos desnecessariamente. Sempre baixe e guarde os backups em um local seguro antes de excluí-los permanentemente do servidor.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
