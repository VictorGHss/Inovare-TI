import { useState, useEffect, type FormEvent } from 'react';
import { CircleHelp, X } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  updateUser,
  type User,
  type Sector,
  type UpdateUserDto,
} from '../../services/api';

interface EditUserModalProps {
  user: User | null;
  sectors: Sector[];
  onClose: () => void;
  onSuccess: () => void;
}

const inputClassName =
  'w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-60 disabled:cursor-not-allowed';

const labelClassName = 'block text-xs font-medium text-slate-600 mb-1.5';

export default function EditUserModal({ user, sectors, onClose, onSuccess }: EditUserModalProps) {
  const [formData, setFormData] = useState<UpdateUserDto>({
    name: '',
    email: '',
    role: 'USER',
    sectorId: '',
    receives_it_notifications: true,
  });
  const [submitting, setSubmitting] = useState(false);

  // Populate form when a user is selected
  useEffect(() => {
    if (user) {
      setFormData({
        name: user.name,
        email: user.email,
        role: user.role,
        sectorId: user.sectorId ?? '',
        receives_it_notifications: user.receives_it_notifications,
      });
    }
  }, [user]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    if (!user) return;

    if (!formData.name.trim() || !formData.email.trim()) {
      toast.error('Preencha todos os campos obrigatórios.');
      return;
    }

    if (!formData.sectorId) {
      toast.error('Selecione o setor do usuário.');
      return;
    }

    setSubmitting(true);
    try {
      await updateUser(user.id, formData);
      toast.success('Usuário atualizado com sucesso!');
      onSuccess();
      onClose();
    } catch {
      toast.error('Erro ao atualizar usuário. Verifique os dados.');
    } finally {
      setSubmitting(false);
    }
  }

  if (!user) return null;

  return (
    <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
        {/* Header */}
        <div className="flex items-center justify-between mb-5">
          <h2 className="text-lg font-bold text-slate-800">Editar Usuário</h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-slate-200 transition-colors"
            disabled={submitting}
          >
            <X size={18} className="text-slate-500" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Nome */}
          <div>
            <label className={labelClassName}>Nome Completo *</label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              className={inputClassName}
              disabled={submitting}
              placeholder="Nome completo"
            />
          </div>

          {/* E-mail */}
          <div>
            <label className={labelClassName}>E-mail *</label>
            <input
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              className={inputClassName}
              disabled={submitting}
              placeholder="email@empresa.com"
            />
          </div>

          {/* Setor */}
          <div>
            <label className={labelClassName}>Setor *</label>
            <select
              value={formData.sectorId}
              onChange={(e) => setFormData({ ...formData, sectorId: e.target.value })}
              className={inputClassName}
              disabled={submitting}
            >
              <option value="">Selecione o setor...</option>
              {sectors.map((sector) => (
                <option key={sector.id} value={sector.id}>
                  {sector.name}
                </option>
              ))}
            </select>
          </div>

          {/* Nível de Acesso */}
          <div>
            <label className={labelClassName}>Nível de Acesso *</label>
            <select
              value={formData.role}
              onChange={(e) => setFormData({ ...formData, role: e.target.value as UpdateUserDto['role'] })}
              className={inputClassName}
              disabled={submitting}
            >
              <option value="USER">Usuário</option>
              <option value="TECHNICIAN">Técnico</option>
              <option value="ADMIN">Administrador</option>
            </select>
          </div>

          <div className="flex items-center justify-between rounded-lg border border-slate-200 px-3 py-2.5">
            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-700">Receber notificações de chamados (Discord)</span>
              <span
                title="Essa opção controla o envio de alertas de chamados e SLA no Discord."
                aria-label="Informação sobre notificações no Discord"
                className="inline-flex text-slate-400"
              >
                <CircleHelp size={15} />
              </span>
            </div>

            <button
              type="button"
              role="switch"
              aria-checked={formData.receives_it_notifications}
              onClick={() =>
                setFormData({
                  ...formData,
                  receives_it_notifications: !formData.receives_it_notifications,
                })
              }
              className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2 ${
                formData.receives_it_notifications
                  ? 'focus:ring-[#ffa751]'
                  : 'bg-slate-300 focus:ring-slate-400'
              }`}
              style={{
                backgroundColor: formData.receives_it_notifications ? '#ffa751' : undefined,
              }}
              disabled={submitting}
            >
              <span
                className={`inline-block h-5 w-5 transform rounded-full bg-white transition-transform ${
                  formData.receives_it_notifications ? 'translate-x-5' : 'translate-x-1'
                }`}
              />
            </button>
          </div>

          {/* Actions */}
          <div className="flex justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-60"
            >
              Cancelar
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex items-center gap-2 bg-brand-primary hover:bg-orange-500 disabled:opacity-60 disabled:cursor-not-allowed text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors"
            >
              {submitting ? 'Salvando...' : 'Salvar Alterações'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
