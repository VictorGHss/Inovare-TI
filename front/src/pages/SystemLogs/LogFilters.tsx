import type { FormEvent } from 'react';

import type { AuditAction, AuditSeverity, User } from '../../types/models';
import { ALL_ACTIONS, toFriendlyAuditAction } from './logConstants';

interface LogFiltersProps {
  users: User[];
  filterUserId: string;
  filterAction: AuditAction | '';
  filterSeverity: AuditSeverity | '';
  search: string;
  startDate: string;
  endDate: string;
  loading: boolean;
  onFilterUserIdChange: (value: string) => void;
  onFilterActionChange: (value: AuditAction | '') => void;
  onFilterSeverityChange: (value: AuditSeverity | '') => void;
  onSearchChange: (value: string) => void;
  onStartDateChange: (value: string) => void;
  onEndDateChange: (value: string) => void;
  onSubmit: () => void;
}

export default function LogFilters({
  users,
  filterUserId,
  filterAction,
  filterSeverity,
  search,
  startDate,
  endDate,
  loading,
  onFilterUserIdChange,
  onFilterActionChange,
  onFilterSeverityChange,
  onSearchChange,
  onStartDateChange,
  onEndDateChange,
  onSubmit,
}: LogFiltersProps) {
  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    onSubmit();
  };

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-4 flex flex-wrap items-end gap-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
    >
      <div className="min-w-[180px] flex flex-col gap-1">
        <label className="text-xs font-medium text-slate-600">Usuario</label>
        <select
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
          value={filterUserId}
          onChange={(event) => onFilterUserIdChange(event.target.value)}
        >
          <option value="">Todos</option>
          {users.map((user) => (
            <option key={user.id} value={user.id}>
              {user.name}
            </option>
          ))}
        </select>
      </div>

      <div className="min-w-[220px] flex flex-col gap-1">
        <label className="text-xs font-medium text-slate-600">Acao</label>
        <select
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
          value={filterAction}
          onChange={(event) => onFilterActionChange(event.target.value as AuditAction | '')}
        >
          <option value="">Todas</option>
          {ALL_ACTIONS.map((action) => (
            <option key={action} value={action}>
              {toFriendlyAuditAction(action)}
            </option>
          ))}
        </select>
      </div>

      <div className="min-w-[160px] flex flex-col gap-1">
        <label className="text-xs font-medium text-slate-600">Severidade</label>
        <select
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
          value={filterSeverity}
          onChange={(event) => onFilterSeverityChange(event.target.value as AuditSeverity | '')}
        >
          <option value="">Todas</option>
          <option value="INFO">Info</option>
          <option value="WARN">Warn</option>
          <option value="ERROR">Error</option>
        </select>
      </div>

      <div className="min-w-[260px] flex-1 flex flex-col gap-1">
        <label className="text-xs font-medium text-slate-600">Busca</label>
        <input
          type="text"
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
          placeholder="Buscar por usuario, acao, IP ou detalhes"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-xs font-medium text-slate-600">De</label>
        <input
          type="date"
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
          value={startDate}
          onChange={(event) => onStartDateChange(event.target.value)}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-xs font-medium text-slate-600">Ate</label>
        <input
          type="date"
          className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-brand-primary/40"
          value={endDate}
          onChange={(event) => onEndDateChange(event.target.value)}
        />
      </div>

      <button
        type="submit"
        disabled={loading}
        className="rounded-lg bg-brand-primary px-4 py-1.5 text-sm font-medium text-white transition-colors hover:bg-brand-primary-dark disabled:opacity-60"
      >
        {loading ? 'Filtrando...' : 'Filtrar'}
      </button>
    </form>
  );
}
