import { useEffect, useMemo, useState } from 'react';
import { Loader2, Save, Trash2, Plus, RefreshCw, AlertCircle } from 'lucide-react';
import { toast } from 'react-toastify';

import { getApiErrorMessage } from '../../lib/apiError';
import {
  getDoctorMappings,
  createDoctorMapping,
  updateDoctorMapping,
  deleteDoctorMapping,
  syncDoctorsBaseFromContaAzul,
} from '../../services/financeService';
import { getUsers } from '../../services/userService';
import type { DoctorMapping, User } from '../../types/models';

const inlineInputClass =
  'w-full rounded-lg border border-slate-200 bg-slate-50 px-2 py-1.5 text-sm text-slate-700 transition-all focus:border-brand-primary/40 focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-primary/25';

interface ExtendedDoctorMapping extends DoctorMapping {
  isNew?: boolean;
}

export default function FinancialDoctorMappingPanel() {
  const [mappings, setMappings] = useState<ExtendedDoctorMapping[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [syncingData, setSyncingData] = useState(false);
  const [savingId, setSavingId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const loadData = async () => {
    try {
      setLoading(true);
      const [dbMappings, dbUsers] = await Promise.all([
        getDoctorMappings(),
        getUsers(),
      ]);
      setMappings(dbMappings);
      setUsers(Array.isArray(dbUsers) ? dbUsers : []);
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao carregar mapeamentos de médicos ou usuários.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  async function handleSyncData() {
    try {
      setSyncingData(true);
      const result = await syncDoctorsBaseFromContaAzul();
      toast.success(`Base de médicos sincronizada. Novos: ${result.novos}, Atualizados: ${result.atualizados}`);
      await loadData();
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao sincronizar médicos do Conta Azul.'));
    } finally {
      setSyncingData(false);
    }
  }

  function handleAddRow() {
    const newRow: ExtendedDoctorMapping = {
      id: `temp-${Date.now()}`,
      userId: null,
      userContaAzulId: null,
      doctorName: '',
      contaAzulCustomerUuid: '',
      doctorEmail: '',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      isNew: true,
    };
    setMappings((prev) => [newRow, ...prev]);
  }

  function updateField(index: number, field: keyof ExtendedDoctorMapping, value: string | null) {
    setMappings((current) =>
      current.map((m, i) => {
        if (i !== index) return m;

        // If updating userId, try to resolve the linked user's Conta Azul ID
        if (field === 'userId') {
          const linkedUser = users.find((u) => u.id === value);
          return {
            ...m,
            userId: value,
            userContaAzulId: linkedUser?.contaAzulId ?? null,
          };
        }

        return { ...m, [field]: value };
      })
    );
  }

  async function handleSaveRow(index: number) {
    const row = mappings[index];
    if (!row.contaAzulCustomerUuid?.trim()) {
      toast.error('O UUID do cliente da Conta Azul é obrigatório.');
      return;
    }

    try {
      setSavingId(row.id);
      const payload = {
        userId: row.userId ?? undefined,
        doctorName: row.doctorName ?? undefined,
        contaAzulCustomerUuid: row.contaAzulCustomerUuid.trim(),
        doctorEmail: row.doctorEmail ?? undefined,
      };

      if (row.isNew) {
        const saved = await createDoctorMapping(payload);
        setMappings((current) =>
          current.map((m, i) => (i === index ? { ...saved, isNew: false } : m))
        );
        toast.success('Mapeamento criado com sucesso.');
      } else {
        const saved = await updateDoctorMapping(row.id, payload);
        setMappings((current) =>
          current.map((m, i) => (i === index ? saved : m))
        );
        toast.success('Mapeamento atualizado com sucesso.');
      }
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao salvar mapeamento.'));
    } finally {
      setSavingId(null);
    }
  }

  async function handleDeleteRow(row: ExtendedDoctorMapping, index: number) {
    if (row.isNew) {
      setMappings((current) => current.filter((_, i) => i !== index));
      toast.success('Rascunho removido.');
      return;
    }

    if (!confirm(`Confirma excluir o mapeamento do médico "${row.doctorName || 'Sem nome'}"?`)) {
      return;
    }

    try {
      setSavingId(row.id);
      await deleteDoctorMapping(row.id);
      setMappings((current) => current.filter((m) => m.id !== row.id));
      toast.success('Mapeamento removido com sucesso.');
    } catch (error) {
      toast.error(getApiErrorMessage(error, 'Falha ao excluir mapeamento.'));
    } finally {
      setSavingId(null);
    }
  }

  const filteredMappings = useMemo(() => {
    if (!searchQuery.trim()) return mappings;
    const query = searchQuery.toLowerCase();
    return mappings.filter(
      (m) =>
        m.doctorName?.toLowerCase().includes(query) ||
        m.doctorEmail?.toLowerCase().includes(query) ||
        m.contaAzulCustomerUuid?.toLowerCase().includes(query)
    );
  }, [mappings, searchQuery]);

  return (
    <section className="w-full rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden mt-6">
      <header className="border-b border-slate-100 px-6 py-5 bg-slate-50/50">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
              Mapeamento de Médicos (Conta Azul)
            </h2>
            <p className="mt-0.5 text-xs text-slate-500">
              Gerencie o vínculo entre médicos faturados no Conta Azul e os respectivos usuários e e-mails do sistema.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={handleAddRow}
              disabled={loading || syncingData}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 shadow-sm transition-colors hover:bg-slate-50 disabled:opacity-60"
            >
              <Plus size={16} />
              Novo Mapeamento
            </button>

            <button
              type="button"
              onClick={() => {
                void handleSyncData();
              }}
              disabled={syncingData || loading}
              className="inline-flex items-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:cursor-not-allowed disabled:opacity-60"
            >
              {syncingData ? <Loader2 size={16} className="animate-spin" /> : <RefreshCw size={16} />}
              {syncingData ? 'Sincronizando...' : 'Sincronizar Conta Azul'}
            </button>
          </div>
        </div>

        {/* Campo de Busca */}
        <div className="mt-4 max-w-md">
          <input
            type="text"
            placeholder="Pesquisar por nome, e-mail ou UUID do cliente..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 transition-all focus:border-brand-primary/40 focus:outline-none focus:ring-2 focus:ring-brand-primary/25"
          />
        </div>
      </header>

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50">
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                UUID Cliente Conta Azul
              </th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                Nome do Médico (Exibição)
              </th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                E-mail de Fallback
              </th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500">
                Usuário do Sistema
              </th>
              <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-slate-500 w-[180px]">
                Ações
              </th>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-100">
            {loading ? (
              <tr>
                <td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-400">
                  <span className="flex items-center justify-center gap-2">
                    <Loader2 size={16} className="animate-spin text-brand-primary" />
                    Carregando mapeamentos...
                  </span>
                </td>
              </tr>
            ) : filteredMappings.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-10 text-center text-sm text-slate-400">
                  Nenhum mapeamento localizado.
                </td>
              </tr>
            ) : (
              filteredMappings.map((row, idx) => (
                <tr key={row.id} className="hover:bg-slate-50/50 transition-colors">
                  {/* UUID Conta Azul */}
                  <td className="px-4 py-3 align-middle font-mono text-xs">
                    {row.isNew ? (
                      <input
                        type="text"
                        placeholder="UUID do Cliente"
                        value={row.contaAzulCustomerUuid || ''}
                        onChange={(e) => updateField(idx, 'contaAzulCustomerUuid', e.target.value)}
                        className={inlineInputClass}
                      />
                    ) : (
                      row.contaAzulCustomerUuid
                    )}
                  </td>

                  {/* Nome do Médico */}
                  <td className="px-4 py-3 align-middle">
                    <input
                      type="text"
                      placeholder="Nome do Médico"
                      value={row.doctorName || ''}
                      onChange={(e) => updateField(idx, 'doctorName', e.target.value)}
                      className={inlineInputClass}
                    />
                  </td>

                  {/* E-mail */}
                  <td className="px-4 py-3 align-middle">
                    <input
                      type="email"
                      placeholder="E-mail de Fallback"
                      value={row.doctorEmail || ''}
                      onChange={(e) => updateField(idx, 'doctorEmail', e.target.value)}
                      className={inlineInputClass}
                    />
                  </td>

                  {/* Usuário Vinculado */}
                  <td className="px-4 py-3 align-middle">
                    <select
                      value={row.userId || ''}
                      onChange={(e) => updateField(idx, 'userId', e.target.value || null)}
                      className={`${inlineInputClass} border border-slate-200 bg-white`}
                    >
                      <option value="">Nenhum usuário vinculado (Usar Fallback)</option>
                      {users.map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.name} ({u.email})
                        </option>
                      ))}
                    </select>
                  </td>

                  {/* Ações */}
                  <td className="px-4 py-3 align-middle">
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => void handleSaveRow(idx)}
                        disabled={savingId === row.id}
                        className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700 hover:bg-emerald-100 transition-colors disabled:opacity-50"
                      >
                        {savingId === row.id ? (
                          <Loader2 size={14} className="animate-spin" />
                        ) : (
                          <Save size={14} />
                        )}
                        Salvar
                      </button>

                      <button
                        type="button"
                        onClick={() => void handleDeleteRow(row, idx)}
                        disabled={savingId === row.id}
                        className="inline-flex items-center gap-1.5 rounded-lg bg-red-50 px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-100 transition-colors disabled:opacity-50"
                      >
                        <Trash2 size={14} />
                        Excluir
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <footer className="bg-slate-50 px-6 py-4 border-t border-slate-100 flex items-start gap-3">
        <AlertCircle size={16} className="text-slate-400 mt-0.5 flex-shrink-0" />
        <p className="text-xs text-slate-500 leading-normal">
          <strong>Funcionamento da Integração Conta Azul:</strong> Quando uma venda é baixada no Conta Azul, o sistema busca o médico associado pelo <code>UUID do Cliente</code> nesta tabela. O recibo consolidado em formato PDF é enviado preferencialmente ao e-mail do <strong>Usuário do Sistema</strong> vinculado. Se nenhum usuário estiver vinculado ou se o usuário não possuir e-mail válido, o e-mail de <strong>Fallback</strong> será utilizado.
        </p>
      </footer>
    </section>
  );
}
