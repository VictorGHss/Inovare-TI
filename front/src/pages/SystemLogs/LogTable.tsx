import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';

import SkeletonTable from '../../components/SkeletonTable';
import type { AuditLogPage } from '../../types/models';
import {
  ACTION_BADGE_CLASS,
  getAuditSeverity,
  SEVERITY_BADGE_CLASS,
  toFriendlyAuditAction,
} from './logConstants';

interface LogTableProps {
  page: AuditLogPage | null;
  loading: boolean;
  error: string | null;
  onPreviousPage: () => void;
  onNextPage: () => void;
}

function formatDate(isoString: string) {
  try {
    return format(new Date(isoString), 'dd/MM/yyyy HH:mm:ss', { locale: ptBR });
  } catch {
    return isoString;
  }
}

export default function LogTable({
  page,
  loading,
  error,
  onPreviousPage,
  onNextPage,
}: LogTableProps) {
  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
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
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                <th className="whitespace-nowrap px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                  Data / Hora
                </th>
                <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                  Usuario
                </th>
                <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                  Acao
                </th>
                <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                  Severidade
                </th>
                <th className="whitespace-nowrap px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                  Endereco IP
                </th>
                <th className="px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest text-slate-400">
                  Detalhes
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {page.content.map((log) => {
                const severity = getAuditSeverity(log.action);
                return (
                  <tr key={log.id} className="transition-colors hover:bg-orange-50/40">
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-500">
                      {formatDate(log.createdAt)}
                    </td>
                    <td className="max-w-[160px] truncate px-4 py-3 text-slate-700">
                      {log.userName ?? <span className="italic text-slate-400">sistema</span>}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                          ACTION_BADGE_CLASS[log.action] ?? 'bg-slate-100 text-slate-600'
                        }`}
                      >
                        {toFriendlyAuditAction(log.action)}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${
                          SEVERITY_BADGE_CLASS[severity]
                        }`}
                      >
                        {severity}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-500">
                      {log.ipAddress ?? '—'}
                    </td>
                    <td
                      className="max-w-[260px] truncate px-4 py-3 text-xs text-slate-500"
                      title={log.details ?? undefined}
                    >
                      {log.details ?? '—'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {page && page.totalPages > 1 && (
        <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-4 py-3">
          <span className="text-xs text-slate-500">
            {page.totalElements} registro{page.totalElements !== 1 ? 's' : ''} — pagina {page.number + 1} de {page.totalPages}
          </span>
          <div className="flex gap-2">
            <button
              onClick={onPreviousPage}
              disabled={page.first}
              className="rounded-lg border border-slate-200 bg-white px-3 py-1 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Anterior
            </button>
            <button
              onClick={onNextPage}
              disabled={page.last}
              className="rounded-lg border border-slate-200 bg-white px-3 py-1 text-xs font-medium text-slate-600 transition-colors hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Proxima
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
