import { useEffect, useState } from 'react';
import { Plus, Check, X, Pencil, Trash2, Calendar, Send } from 'lucide-react';
import { toast } from 'react-toastify';

import {
  getReportSchedules,
  createReportSchedule,
  deleteReportSchedule,
  triggerReportScheduleTest,
  updateReportSchedule,
  getUsers,
  type ReportSchedule,
  type User,
} from '../../services/api';

const inputClassName =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition-all';

export default function ReportSchedulesSection() {
  const [reportSchedules, setReportSchedules] = useState<ReportSchedule[]>([]);
  const [schedulesLoading, setSchedulesLoading] = useState(false);
  const [usersList, setUsersList] = useState<User[]>([]);
  const [newSchedulePayload, setNewSchedulePayload] = useState<Partial<ReportSchedule>>({
    reportType: 'exits',
    targetUserId: null,
    sendEmail: true,
    sendDiscord: false,
    scheduleDay: 12,
    isActive: true,
  });

  const [editingScheduleId, setEditingScheduleId] = useState<string | null>(null);
  const [editingPayload, setEditingPayload] = useState<Partial<ReportSchedule> | null>(null);

  useEffect(() => {
    let mounted = true;
    async function load() {
      setSchedulesLoading(true);
      try {
        const [schedules, users] = await Promise.all([getReportSchedules(), getUsers()]);
        if (!mounted) return;
        setReportSchedules(Array.isArray(schedules) ? schedules : []);
        setUsersList(Array.isArray(users) ? users : []);
      } catch (e) {
        console.warn('Failed to load schedules', e);
      } finally {
        if (mounted) setSchedulesLoading(false);
      }
    }
    load();
    return () => {
      mounted = false;
    };
  }, []);

  async function handleCreateSchedule() {
    if (!newSchedulePayload.reportType) { toast.error('Selecione o tipo de relatório.'); return; }
    setSchedulesLoading(true);
    try {
      const created = await createReportSchedule(newSchedulePayload as Partial<ReportSchedule>);
      setReportSchedules((prev) => (prev ?? []).concat(created));
      toast.success('Agendamento criado com sucesso.');
      setNewSchedulePayload({ reportType: 'exits', targetUserId: null, sendEmail: true, sendDiscord: false, scheduleDay: 12, isActive: true });
    } catch (err) {
      console.warn('Failed to create schedule', err);
      toast.error('Erro ao criar agendamento.');
    } finally {
      setSchedulesLoading(false);
    }
  }

  function startEditSchedule(s: ReportSchedule) {
    setEditingScheduleId(s.id);
    setEditingPayload({ reportType: s.reportType, targetUserId: s.targetUserId, sendEmail: s.sendEmail, sendDiscord: s.sendDiscord, scheduleDay: s.scheduleDay, isActive: s.isActive });
  }

  async function handleSaveEditedSchedule() {
    if (!editingScheduleId || !editingPayload) return;
    setSchedulesLoading(true);
    try {
      const original = reportSchedules.find((r) => r.id === editingScheduleId) ?? null;
      const payload: Partial<ReportSchedule> = {
        reportType: editingPayload.reportType ?? original?.reportType ?? '',
        targetUserId: editingPayload.targetUserId ?? original?.targetUserId ?? null,
        sendEmail: editingPayload.sendEmail ?? original?.sendEmail ?? true,
        sendDiscord: editingPayload.sendDiscord ?? original?.sendDiscord ?? false,
        scheduleDay: editingPayload.scheduleDay ?? original?.scheduleDay ?? 12,
        isActive: editingPayload.isActive ?? original?.isActive ?? true,
      };
      const updated = await updateReportSchedule(editingScheduleId, payload);
      setReportSchedules((prev) => (prev ?? []).map((s) => (s.id === updated.id ? updated : s)));
      toast.success(`Agendamento atualizado. Status: ${updated.isActive ? 'Ativo' : 'Inativo'}.`);
      setEditingScheduleId(null);
      setEditingPayload(null);
    } catch (err) {
      console.warn('Failed to update schedule', err);
      toast.error('Erro ao atualizar agendamento.');
    } finally {
      setSchedulesLoading(false);
    }
  }

  function cancelEdit() {
    setEditingScheduleId(null);
    setEditingPayload(null);
  }

  async function handleDeleteSchedule(id: string) {
    setSchedulesLoading(true);
    try {
      await deleteReportSchedule(id);
      setReportSchedules((prev) => (prev ?? []).filter((s) => s.id !== id));
      toast.success('Agendamento removido com sucesso.');
    } catch (err) {
      console.warn('Failed to delete schedule', err);
      toast.error('Erro ao remover agendamento.');
    } finally {
      setSchedulesLoading(false);
    }
  }

  async function handleTriggerTest(schedule: ReportSchedule) {
    toast.info('Enviando relatório de teste...');
    try {
      await triggerReportScheduleTest(schedule.id);
      toast.success('Relatório de teste enviado com sucesso.');
    } catch (err) {
      console.warn('Failed to trigger test report', err);
      toast.error('Erro ao enviar relatório de teste.');
    }
  }

  async function handleToggleScheduleActive(schedule: ReportSchedule) {
    setSchedulesLoading(true);
    try {
      const payload: Partial<ReportSchedule> = {
        reportType: schedule.reportType,
        targetUserId: schedule.targetUserId,
        sendEmail: schedule.sendEmail,
        sendDiscord: schedule.sendDiscord,
        scheduleDay: schedule.scheduleDay,
        isActive: !schedule.isActive,
      };
      const updated = await updateReportSchedule(schedule.id, payload);
      setReportSchedules((prev) => (prev ?? []).map((s) => (s.id === updated.id ? updated : s)));
      toast.success(updated.isActive ? 'Agendamento ativado.' : 'Agendamento desativado.');
    } catch (err) {
      console.warn('Failed to toggle schedule active', err);
      toast.error('Erro ao atualizar agendamento.');
    } finally {
      setSchedulesLoading(false);
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
      <div className="px-6 py-4 border-b border-slate-100 flex items-center gap-3">
        <div className="w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center shrink-0">
          <Calendar size={16} className="text-[#feb56c]" />
        </div>
        <div>
          <h2 className="text-sm font-semibold text-slate-900">Agendamentos de Relatórios</h2>
          <p className="text-xs text-slate-500 mt-0.5">Configure envios automáticos de relatórios mensais.</p>
        </div>
      </div>

      <div className="divide-y divide-slate-100">
        {Array.isArray(reportSchedules) && reportSchedules.length === 0 ? (
          <div className="px-6 py-8 text-center">
            <p className="text-sm text-slate-400">Nenhum agendamento encontrado.</p>
          </div>
        ) : (
          Array.isArray(reportSchedules) && reportSchedules.map((s) => {
            const usr = Array.isArray(usersList) ? usersList.find((u) => u.id === s.targetUserId) : undefined;

            if (editingScheduleId === s.id) {
              return (
                <div key={s.id} className="px-6 py-4 bg-slate-50">
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1.5">Tipo de Relatório</label>
                      <select value={editingPayload?.reportType ?? 'exits'} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), reportType: e.target.value }))} className={inputClassName}>
                        <option value="exits">Saídas de Estoque</option>
                        <option value="entries">Entradas de Estoque</option>
                        <option value="tickets">Histórico de Chamados</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1.5">Destinatário</label>
                      <select value={editingPayload?.targetUserId ?? ''} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), targetUserId: e.target.value || null }))} className={inputClassName}>
                        <option value="">Selecione usuário (opcional)</option>
                        {Array.isArray(usersList) && usersList.map((u) => (
                          <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-slate-600 mb-1.5">Dia do Mês</label>
                      <input type="number" min={1} max={31} value={editingPayload?.scheduleDay ?? s.scheduleDay} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), scheduleDay: Number(e.target.value) }))} className={inputClassName} />
                    </div>
                    <div className="md:col-span-3">
                      <p className="text-xs text-slate-400">Dias 29, 30 e 31 serão processados no último dia útil de meses mais curtos.</p>
                    </div>
                    <div className="flex items-center gap-4">
                      <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                        <input type="checkbox" checked={!!editingPayload?.sendEmail} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), sendEmail: e.target.checked }))} className="rounded" />
                        E-mail
                      </label>
                      <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                        <input type="checkbox" checked={!!editingPayload?.sendDiscord} onChange={(e) => setEditingPayload((prev) => ({ ...(prev ?? {}), sendDiscord: e.target.checked }))} className="rounded" />
                        Discord
                      </label>
                    </div>
                    <div className="md:col-span-2 flex gap-2 justify-end">
                      <button onClick={handleSaveEditedSchedule} disabled={schedulesLoading} className="inline-flex items-center gap-1.5 rounded-xl bg-brand-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand-primary-dark">
                        <Check size={13} /> Salvar
                      </button>
                      <button onClick={cancelEdit} className="inline-flex items-center gap-1.5 border border-slate-200 text-slate-600 text-sm font-medium px-4 py-2 rounded-lg hover:bg-slate-100 transition-colors">
                        <X size={13} /> Cancelar
                      </button>
                    </div>
                  </div>
                </div>
              );
            }

            return (
              <div key={s.id} className="px-6 py-4 flex flex-col sm:flex-row sm:items-center gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium text-slate-800 capitalize">{s.reportType}</span>
                    <span className="text-slate-300">·</span>
                    <span className="text-sm text-slate-500">Dia {s.scheduleDay}</span>
                    <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full ${s.isActive ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${s.isActive ? 'bg-emerald-500' : 'bg-slate-400'}`} />
                      {s.isActive ? 'Ativo' : 'Inativo'}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {[s.sendEmail && 'E-mail', s.sendDiscord && 'Discord'].filter(Boolean).join(' + ')}
                    {usr ? ` — ${usr.name}` : s.targetUserId ? ` — ${s.targetUserId}` : ''}
                  </p>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  <button
                    onClick={() => handleToggleScheduleActive(s)}
                    title={s.isActive ? 'Desativar' : 'Ativar'}
                    className={`px-3 py-1.5 text-xs font-medium rounded-xl transition-colors ${s.isActive ? 'bg-brand-primary text-white hover:bg-brand-primary-dark' : 'bg-white border border-slate-200 text-brand-primary'}`}
                  >
                    {s.isActive ? 'Desativar' : 'Ativar'}
                  </button>
                  <button onClick={() => startEditSchedule(s)} className="p-2 rounded-lg hover:bg-slate-100 transition-colors">
                    <Pencil size={14} className="text-slate-500" />
                  </button>
                  <button onClick={() => handleTriggerTest(s)} className="p-2 rounded-lg hover:bg-slate-100 transition-colors" title="Testar Agora">
                    <Send size={14} className="text-slate-500" />
                  </button>
                  <button onClick={() => handleDeleteSchedule(s.id)} disabled={schedulesLoading} className="p-2 rounded-lg hover:bg-red-50 transition-colors">
                    <Trash2 size={14} className="text-red-500" />
                  </button>
                </div>
              </div>
            );
          })
        )}
      </div>

      <div className="px-6 py-5 bg-slate-50 border-t border-slate-100">
        <p className="text-xs font-semibold text-slate-600 uppercase tracking-wide mb-3">Novo Agendamento</p>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-end">
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Tipo de Relatório</label>
            <select value={newSchedulePayload.reportType} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, reportType: e.target.value }))} className={inputClassName}>
              <option value="exits">Saídas de Estoque</option>
              <option value="entries">Entradas de Estoque</option>
              <option value="tickets">Histórico de Chamados</option>
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Destinatário</label>
            <select value={newSchedulePayload.targetUserId ?? ''} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, targetUserId: e.target.value || null }))} className={inputClassName}>
              <option value="">Selecione usuário (opcional)</option>
              {Array.isArray(usersList) && usersList.map((u) => (
                <option key={u.id} value={u.id}>{u.name} ({u.email})</option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-xs font-medium text-slate-600 mb-1.5">Dia do Mês</label>
            <input type="number" min={1} max={31} value={newSchedulePayload.scheduleDay ?? 12} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, scheduleDay: Number(e.target.value) }))} className={inputClassName} />
          </div>
          <div className="md:col-span-3">
            <p className="text-xs text-slate-400">Dias 29, 30 e 31 serão processados no último dia útil de meses mais curtos.</p>
          </div>
          <div className="flex items-center gap-4">
            <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
              <input type="checkbox" checked={newSchedulePayload.sendEmail ?? true} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, sendEmail: e.target.checked }))} className="rounded" />
              E-mail
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
              <input type="checkbox" checked={newSchedulePayload.sendDiscord ?? false} onChange={(e) => setNewSchedulePayload((prev) => ({ ...prev, sendDiscord: e.target.checked }))} className="rounded" />
              Discord
            </label>
          </div>
          <div className="md:col-span-2 flex justify-end">
            <button
              onClick={handleCreateSchedule}
              disabled={schedulesLoading}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white transition-colors hover:bg-brand-primary-dark disabled:opacity-40"
            >
              <Plus size={14} />
              Criar Agendamento
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
