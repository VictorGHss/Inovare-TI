// Página de Logs do Sistema — exclusiva para ADMIN
import { useEffect, useState, useCallback } from 'react';
import { Shield } from 'lucide-react';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import axios from 'axios';
import {
  getAuditLogs,
  getUsers,
  type AuditLog,
  type AuditAction,
  type AuditLogPage,
  type User,
} from '../../services/api';
import SkeletonTable from '../../components/SkeletonTable';

const ACTION_LABELS: Record<AuditAction, string> = {
  VAULT_LOGIN_SUCCESS: 'Cofre: Login 2FA com Sucesso',
  VAULT_LOGIN_FAILURE: 'Cofre: Falha de Login 2FA',
  VAULT_SECRET_VIEW: 'Visualizou Segredo',
  VAULT_FILE_VIEW: 'Visualizou Arquivo',
  VAULT_ITEM_CREATE: 'Criou Item no Cofre',
  VAULT_ITEM_EDIT: 'Editou Item no Cofre',
  VAULT_ITEM_DELETE: 'Removeu Item no Cofre',
  LOGIN_SUCCESS: 'Login com Sucesso',
  LOGIN_FAILURE: 'Falha de Login',
  TWO_FACTOR_RESET: 'Reset 2FA (próprio)',
  TWO_FACTOR_ADMIN_RESET: 'Reset 2FA (Admin)',
  TICKET_OPEN: 'Chamado Aberto',
  TICKET_ASSIGN: 'Chamado Atribuído',
  TICKET_TRANSFER: 'Chamado Transferido',
  TICKET_RESOLVE: 'Chamado Resolvido',
  INVENTORY_BATCH_ENTRY: 'Inventário: Entrada de Lote',
  INVENTORY_ITEM_CREATE: 'Inventário: Criação de Item',
  ASSET_CREATE: 'Ativo Criado',
  ASSET_INVOICE_ATTACH: 'NF Anexada ao Ativo',
  QR_SCAN: 'Leitura de QR Code',
  KB_ARTICLE_DRAFT_CREATE: 'Base de Conhecimento: Rascunho',
  KB_ARTICLE_PUBLISH: 'Base de Conhecimento: Publicação',
  KB_ARTICLE_EDIT: 'Base de Conhecimento: Edição',
  SECTOR_CREATE: 'Gestão: Criação de Setor',
  USER_CREATE: 'Gestão: Criação de Usuário',
  USER_UPDATE: 'Gestão: Edição de Usuário',
  USER_PASSWORD_RESET: 'Gestão: Reset de Senha',
  USER_PERMISSION_CHANGE: 'Alteração de Permissão',
};

const ACTION_BADGE_CLASS: Record<AuditAction, string> = {
  VAULT_LOGIN_SUCCESS: 'bg-indigo-100 text-indigo-700',
  VAULT_LOGIN_FAILURE: 'bg-rose-100 text-rose-700',
  VAULT_SECRET_VIEW: 'bg-blue-100 text-blue-700',
  VAULT_FILE_VIEW: 'bg-blue-100 text-blue-700',
  VAULT_ITEM_CREATE: 'bg-emerald-100 text-emerald-700',
  VAULT_ITEM_EDIT: 'bg-indigo-100 text-indigo-700',
  VAULT_ITEM_DELETE: 'bg-rose-100 text-rose-700',
  LOGIN_SUCCESS: 'bg-green-100 text-green-700',
  LOGIN_FAILURE: 'bg-red-100 text-red-700',
  TWO_FACTOR_RESET: 'bg-yellow-100 text-yellow-700',
  TWO_FACTOR_ADMIN_RESET: 'bg-orange-100 text-orange-700',
  TICKET_OPEN: 'bg-orange-100 text-orange-700',
  TICKET_ASSIGN: 'bg-indigo-100 text-indigo-700',
  TICKET_TRANSFER: 'bg-slate-200 text-slate-700',
  TICKET_RESOLVE: 'bg-emerald-100 text-emerald-700',
  INVENTORY_BATCH_ENTRY: 'bg-amber-100 text-amber-700',
  INVENTORY_ITEM_CREATE: 'bg-teal-100 text-teal-700',
  ASSET_CREATE: 'bg-cyan-100 text-cyan-700',
  ASSET_INVOICE_ATTACH: 'bg-lime-100 text-lime-700',
  QR_SCAN: 'bg-violet-100 text-violet-700',
  KB_ARTICLE_DRAFT_CREATE: 'bg-slate-200 text-slate-700',
  KB_ARTICLE_PUBLISH: 'bg-emerald-100 text-emerald-700',
  KB_ARTICLE_EDIT: 'bg-indigo-100 text-indigo-700',
  SECTOR_CREATE: 'bg-blue-100 text-blue-700',
  USER_CREATE: 'bg-sky-100 text-sky-700',
  USER_UPDATE: 'bg-purple-100 text-purple-700',
  USER_PASSWORD_RESET: 'bg-red-100 text-red-700',
  USER_PERMISSION_CHANGE: 'bg-purple-100 text-purple-700',
};

const ALL_ACTIONS: AuditAction[] = [
  'VAULT_LOGIN_SUCCESS',
  'VAULT_LOGIN_FAILURE',
  'VAULT_SECRET_VIEW',
  'VAULT_FILE_VIEW',
  'VAULT_ITEM_CREATE',
  'VAULT_ITEM_EDIT',
  'VAULT_ITEM_DELETE',
  'LOGIN_SUCCESS',
  'LOGIN_FAILURE',
  'TWO_FACTOR_RESET',
  'TWO_FACTOR_ADMIN_RESET',
  'TICKET_OPEN',
  'TICKET_ASSIGN',
  'TICKET_TRANSFER',
  'TICKET_RESOLVE',
  'INVENTORY_BATCH_ENTRY',
  'INVENTORY_ITEM_CREATE',
  'ASSET_CREATE',
  'ASSET_INVOICE_ATTACH',
  'QR_SCAN',
  'KB_ARTICLE_DRAFT_CREATE',
  'KB_ARTICLE_PUBLISH',
  'KB_ARTICLE_EDIT',
  'SECTOR_CREATE',
  'USER_CREATE',
  'USER_UPDATE',
  'USER_PASSWORD_RESET',
  'USER_PERMISSION_CHANGE',
];

export default function SystemLogs() {
  const [filterUserId, setFilterUserId] = useState('');
  const [filterAction, setFilterAction] = useState<AuditAction | ''>('');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');

  const [users, setUsers] = useState<User[]>([]);
  const [page, setPage] = useState<AuditLogPage | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchLogs = useCallback(
    async (pageNum = 0) => {
      setLoading(true);
      setError(null);
      try {
        const data = await getAuditLogs({
          userId: filterUserId || undefined,
          action: (filterAction as AuditAction) || undefined,
          startDate: startDate ? `${startDate}T00:00:00` : undefined,
          endDate: endDate ? `${endDate}T23:59:59` : undefined,
          page: pageNum,
          size: 20,
        });
        setPage(data);
        setCurrentPage(pageNum);
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 500) {
          setError('Erro interno no servidor ao consultar a auditoria. Tente novamente em instantes.');
        } else {
          setError('Erro ao carregar logs. Verifique sua conexão e tente novamente.');
        }
      } finally {
        setLoading(false);
      }
    },
    [filterUserId, filterAction, startDate, endDate],
  );

  useEffect(() => {
    getUsers().then(setUsers).catch(() => {});
    fetchLogs(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleFilter = (e: React.FormEvent) => {
    e.preventDefault();
    fetchLogs(0);
  };

  const formatDate = (isoString: string) => {
    try {
      return format(new Date(isoString), 'dd/MM/yyyy HH:mm:ss', { locale: ptBR });
    } catch {
      return isoString;
    }
  };

  return (
    <div className="p-4 sm:p-6 w-full max-w-full">
      {/* Cabeçalho */}
      <div className="flex items-center gap-3 mb-6">
        <div className="p-2 bg-brand-secondary rounded-xl">
          <Shield size={24} className="text-brand-primary" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-slate-800">Logs do Sistema</h1>
          <p className="text-sm text-slate-500">Trilha de auditoria — ações sensíveis registradas</p>
        </div>
      </div>

      {/* Filtros */}
      <form
        onSubmit={handleFilter}
        className="bg-white rounded-xl border border-slate-200 p-4 mb-4 flex flex-wrap gap-3 items-end shadow-sm"
      >
        <div className="flex flex-col gap-1 min-w-[180px]">
          <label className="text-xs font-medium text-slate-600">Usuário</label>
          <select
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
            value={filterUserId}
            onChange={(e) => setFilterUserId(e.target.value)}
          >
            <option value="">Todos</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1 min-w-[210px]">
          <label className="text-xs font-medium text-slate-600">Ação</label>
          <select
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
            value={filterAction}
            onChange={(e) => setFilterAction(e.target.value as AuditAction | '')}
          >
            <option value="">Todas</option>
            {ALL_ACTIONS.map((a) => (
              <option key={a} value={a}>
                {ACTION_LABELS[a]}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-slate-600">De</label>
          <input
            type="date"
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-slate-600">Até</label>
          <input
            type="date"
            className="border border-slate-200 rounded-lg px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
            value={endDate}
            onChange={(e) => setEndDate(e.target.value)}
          />
        </div>

        <button
          type="submit"
          className="bg-brand-primary hover:bg-brand-primary-dark text-white text-sm font-medium px-4 py-1.5 rounded-lg transition-colors"
        >
          Filtrar
        </button>
      </form>

      {/* Tabela */}
      <div className="bg-white rounded-xl border border-slate-200 shadow-sm overflow-hidden">
        {loading ? (
          <SkeletonTable />
        ) : error ? (
          <div className="p-8 text-center text-sm text-red-500">{error}</div>
        ) : !page || page.content.length === 0 ? (
          <div className="p-8 text-center text-sm text-slate-400">
            Nenhum log encontrado para os filtros selecionados.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-slate-600 whitespace-nowrap">
                    Data / Hora
                  </th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Usuário</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Ação</th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600 whitespace-nowrap">
                    Endereço IP
                  </th>
                  <th className="text-left px-4 py-3 font-medium text-slate-600">Detalhes</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {page.content.map((log: AuditLog) => (
                  <tr key={log.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3 text-slate-500 whitespace-nowrap font-mono text-xs">
                      {formatDate(log.createdAt)}
                    </td>
                    <td className="px-4 py-3 text-slate-700 max-w-[160px] truncate">
                      {log.userName ?? (
                        <span className="text-slate-400 italic">sistema</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                          ACTION_BADGE_CLASS[log.action] ?? 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {ACTION_LABELS[log.action] ?? log.action}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-slate-500 font-mono text-xs whitespace-nowrap">
                      {log.ipAddress ?? '—'}
                    </td>
                    <td
                      className="px-4 py-3 text-slate-500 text-xs max-w-[260px] truncate"
                      title={log.details ?? undefined}
                    >
                      {log.details ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {/* Paginação */}
        {page && page.totalPages > 1 && (
          <div className="px-4 py-3 border-t border-slate-200 flex items-center justify-between bg-slate-50">
            <span className="text-xs text-slate-500">
              {page.totalElements} registro{page.totalElements !== 1 ? 's' : ''} — página{' '}
              {page.number + 1} de {page.totalPages}
            </span>
            <div className="flex gap-2">
              <button
                onClick={() => fetchLogs(currentPage - 1)}
                disabled={page.first}
                className="px-3 py-1 text-xs font-medium rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Anterior
              </button>
              <button
                onClick={() => fetchLogs(currentPage + 1)}
                disabled={page.last}
                className="px-3 py-1 text-xs font-medium rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                Próxima
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
