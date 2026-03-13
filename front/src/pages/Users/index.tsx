// Página de listagem e cadastro de usuários
import { useEffect, useState } from 'react';
import { PlusCircle, X, Upload, Pencil, KeyRound, ShieldOff, Bell, BellOff, CircleHelp } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  getUsers,
  createUser,
  getSectors,
  resetUserPassword,
  adminReset2FA,
  type User,
  type Sector,
  type CreateUserDto,
} from '../../services/api';
import { useAuth } from '../../contexts/AuthContext';
import BulkImportModal from './BulkImportModal';
import EditUserModal from './EditUserModal';

export default function Users() {
  const { user: authenticatedUser, invalidateTwoFactorVerification } = useAuth();

  const [users, setUsers] = useState<User[]>([]);
  const [sectors, setSectors] = useState<Sector[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [editingUser, setEditingUser] = useState<User | null>(null);

  const [resetTargetUser, setResetTargetUser] = useState<User | null>(null);
  const [resettingPassword, setResettingPassword] = useState(false);

  const [reset2FATargetUser, setReset2FATargetUser] = useState<User | null>(null);
  const [resetting2FA, setResetting2FA] = useState(false);

  const [formData, setFormData] = useState<CreateUserDto>({
    name: '',
    email: '',
    password: '',
    role: 'USER',
    sectorId: '',
    receives_it_notifications: true,
  });

  useEffect(() => {
    void Promise.all([loadUsers(), loadSectors()]);
  }, []);

  async function loadUsers() {
    setLoading(true);
    try {
      const data = await getUsers();
      setUsers(data);
    } catch {
      toast.error('Erro ao carregar usuários.');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }

  async function loadSectors() {
    try {
      const data = await getSectors();
      setSectors(data);
    } catch {
      toast.error('Erro ao carregar setores.');
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    if (!formData.name.trim() || !formData.email.trim() || !formData.password.trim()) {
      toast.error('Preencha todos os campos obrigatórios.');
      return;
    }

    if (!formData.sectorId) {
      toast.error('Selecione o setor do usuário.');
      return;
    }

    setSubmitting(true);
    try {
      await createUser(formData);
      toast.success('Usuário cadastrado com sucesso!');
      setShowModal(false);
      resetForm();
      await loadUsers();
    } catch {
      toast.error('Erro ao cadastrar usuário. Verifique os dados.');
    } finally {
      setSubmitting(false);
    }
  }

  function resetForm() {
    setFormData({
      name: '',
      email: '',
      password: '',
      role: 'USER',
      sectorId: '',
      receives_it_notifications: true,
    });
  }

  async function handleConfirmResetPassword() {
    if (!resetTargetUser) return;

    setResettingPassword(true);
    try {
      await resetUserPassword(resetTargetUser.id);
      toast.success(`Senha de ${resetTargetUser.name} redefinida para "Mudar@123".`);
      setResetTargetUser(null);
    } catch {
      toast.error('Erro ao redefinir senha. Tente novamente.');
    } finally {
      setResettingPassword(false);
    }
  }

  async function handleConfirmReset2FA() {
    if (!reset2FATargetUser) return;

    setResetting2FA(true);
    try {
      await adminReset2FA(reset2FATargetUser.id);
      toast.success(`2FA de ${reset2FATargetUser.name} redefinido com sucesso.`);

      if (authenticatedUser?.id === reset2FATargetUser.id) {
        invalidateTwoFactorVerification();
        toast.info('Seu 2FA atual foi invalidado. Configure novamente para acessar o cofre.');
      }

      setReset2FATargetUser(null);
    } catch {
      toast.error('Erro ao redefinir o 2FA. Tente novamente.');
    } finally {
      setResetting2FA(false);
    }
  }

  const roleLabels = {
    ADMIN: 'Administrador',
    TECHNICIAN: 'Técnico',
    USER: 'Usuário',
  } as const;

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Equipe</h1>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowImportModal(true)}
            className="flex items-center gap-2 bg-brand-primary hover:bg-orange-500 text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
          >
            <Upload size={17} />
            Importar Planilha
          </button>
          <button
            onClick={() => setShowModal(true)}
            className="flex items-center gap-2 bg-primary hover:bg-primary-hover text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
          >
            <PlusCircle size={17} />
            Novo Usuário
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-slate-200 shadow-sm">
        {loading ? (
          <div className="p-12 text-center">
            <div className="animate-pulse space-y-3">
              <div className="h-4 bg-slate-200 rounded w-3/4 mx-auto" />
              <div className="h-4 bg-slate-200 rounded w-1/2 mx-auto" />
            </div>
          </div>
        ) : users.length === 0 ? (
          <p className="text-center text-slate-400 py-12 text-sm">Nenhum usuário cadastrado.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-slate-500 uppercase text-xs tracking-wider">
                <tr>
                  <th className="px-4 py-3 text-left">Nome</th>
                  <th className="px-4 py-3 text-left">E-mail</th>
                  <th className="px-4 py-3 text-left">Setor</th>
                  <th className="px-4 py-3 text-left">Nível de Acesso</th>
                  <th className="px-4 py-3 text-center">Alertas Discord</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map((currentUser) => (
                  <tr
                    key={currentUser.id}
                    onClick={() => setSelectedUser(currentUser)}
                    className="hover:bg-slate-50 transition-colors cursor-pointer"
                  >
                    <td className="px-4 py-3 font-medium text-slate-800">{currentUser.name}</td>
                    <td className="px-4 py-3 text-slate-600">{currentUser.email}</td>
                    <td className="px-4 py-3 text-slate-600">{currentUser.sectorName}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          currentUser.role === 'ADMIN'
                            ? 'bg-red-100 text-red-700'
                            : currentUser.role === 'TECHNICIAN'
                              ? 'bg-brand-secondary text-brand-primary'
                              : 'bg-slate-100 text-slate-700'
                        }`}
                      >
                        {roleLabels[currentUser.role]}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span
                        className="inline-flex"
                        title={
                          currentUser.receives_it_notifications
                            ? 'Recebe alertas de chamados e SLA no Discord'
                            : 'Não recebe alertas de chamados e SLA no Discord'
                        }
                      >
                        {currentUser.receives_it_notifications ? (
                          <Bell size={16} className="text-[#ffa751]" />
                        ) : (
                          <BellOff size={16} className="text-slate-400" />
                        )}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold text-slate-800">Novo Usuário</h2>
              <button
                onClick={() => {
                  setShowModal(false);
                  resetForm();
                }}
                className="p-1 rounded-lg hover:bg-slate-200 transition-colors"
              >
                <X size={18} className="text-slate-500" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">Nome Completo</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  disabled={submitting}
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">E-mail</label>
                <input
                  type="email"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  disabled={submitting}
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">Senha</label>
                <input
                  type="password"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  disabled={submitting}
                  placeholder="Mínimo 8 caracteres"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">Setor</label>
                <select
                  value={formData.sectorId}
                  onChange={(e) => setFormData({ ...formData, sectorId: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  disabled={submitting}
                >
                  <option value="">Selecione...</option>
                  {sectors.map((sector) => (
                    <option key={sector.id} value={sector.id}>
                      {sector.name}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1.5">Nível de Acesso</label>
                <select
                  value={formData.role}
                  onChange={(e) =>
                    setFormData({ ...formData, role: e.target.value as CreateUserDto['role'] })
                  }
                  className="w-full px-3 py-2 border border-slate-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary"
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

              <button
                type="submit"
                disabled={submitting}
                className="w-full bg-primary hover:bg-primary-hover text-white text-sm font-semibold py-2.5 rounded-lg transition-colors disabled:opacity-50"
              >
                {submitting ? 'Salvando...' : 'Cadastrar Usuário'}
              </button>
            </form>
          </div>
        </div>
      )}

      {selectedUser && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 p-6">
            <div className="flex items-center justify-between mb-5">
              <h2 className="text-lg font-bold text-slate-800">Gestão de Usuário</h2>
              <button
                onClick={() => setSelectedUser(null)}
                className="p-1 rounded-lg hover:bg-slate-200 transition-colors"
              >
                <X size={18} className="text-slate-500" />
              </button>
            </div>

            <div className="space-y-2 text-sm mb-6">
              <p><span className="font-semibold text-slate-700">Nome:</span> <span className="text-slate-600">{selectedUser.name}</span></p>
              <p><span className="font-semibold text-slate-700">E-mail:</span> <span className="text-slate-600">{selectedUser.email}</span></p>
              <p><span className="font-semibold text-slate-700">Setor:</span> <span className="text-slate-600">{selectedUser.sectorName}</span></p>
              <p><span className="font-semibold text-slate-700">Perfil:</span> <span className="text-slate-600">{roleLabels[selectedUser.role]}</span></p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              <button
                onClick={() => {
                  setEditingUser(selectedUser);
                }}
                className="inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg bg-blue-50 text-blue-700 hover:bg-blue-100 text-sm font-semibold transition-colors"
              >
                <Pencil size={15} />
                Editar Dados
              </button>

              <button
                onClick={() => setResetTargetUser(selectedUser)}
                className="inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg bg-amber-50 text-amber-700 hover:bg-amber-100 text-sm font-semibold transition-colors"
              >
                <KeyRound size={15} />
                Redefinir Senha
              </button>

              <button
                onClick={() => setReset2FATargetUser(selectedUser)}
                className="inline-flex items-center justify-center gap-2 px-3 py-2.5 rounded-lg bg-red-50 text-red-700 hover:bg-red-100 text-sm font-semibold transition-colors"
              >
                <ShieldOff size={15} />
                Resetar 2FA
              </button>
            </div>
          </div>
        </div>
      )}

      <BulkImportModal
        isOpen={showImportModal}
        onClose={() => setShowImportModal(false)}
        onSuccess={loadUsers}
      />

      <EditUserModal
        user={editingUser}
        sectors={sectors}
        onClose={() => setEditingUser(null)}
        onSuccess={loadUsers}
      />

      {resetTargetUser && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold text-slate-800">Redefinir Senha</h2>
              <button
                onClick={() => setResetTargetUser(null)}
                className="p-1 rounded-lg hover:bg-slate-200 transition-colors"
                disabled={resettingPassword}
              >
                <X size={18} className="text-slate-500" />
              </button>
            </div>
            <p className="text-sm text-slate-600 mb-6">
              Tem certeza que deseja redefinir a senha de{' '}
              <strong className="text-slate-800">{resetTargetUser.name}</strong> para{' '}
              <strong className="text-slate-800">"Mudar@123"</strong>?<br />
              <span className="text-slate-500 mt-1 inline-block">
                O usuário terá de criar uma nova senha no próximo login.
              </span>
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setResetTargetUser(null)}
                disabled={resettingPassword}
                className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-60"
              >
                Cancelar
              </button>
              <button
                onClick={() => void handleConfirmResetPassword()}
                disabled={resettingPassword}
                className="flex items-center gap-2 bg-amber-500 hover:bg-amber-600 disabled:opacity-60 disabled:cursor-not-allowed text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors"
              >
                <KeyRound size={15} />
                {resettingPassword ? 'Redefinindo...' : 'Confirmar Redefinição'}
              </button>
            </div>
          </div>
        </div>
      )}

      {reset2FATargetUser && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-bold text-slate-800">Resetar 2FA</h2>
              <button
                onClick={() => setReset2FATargetUser(null)}
                className="p-1 rounded-lg hover:bg-slate-200 transition-colors"
                disabled={resetting2FA}
              >
                <X size={18} className="text-slate-500" />
              </button>
            </div>
            <p className="text-sm text-slate-600 mb-6">
              Tem certeza que deseja resetar o 2FA de{' '}
              <strong className="text-slate-800">{reset2FATargetUser.name}</strong>?<br />
              <span className="text-red-500 mt-1 inline-block text-xs">
                ⚠️ O autenticador atual será removido. O usuário precisará configurar um novo na página de Perfil.
              </span>
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setReset2FATargetUser(null)}
                disabled={resetting2FA}
                className="px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-60"
              >
                Cancelar
              </button>
              <button
                onClick={() => void handleConfirmReset2FA()}
                disabled={resetting2FA}
                className="flex items-center gap-2 bg-red-500 hover:bg-red-600 disabled:opacity-60 disabled:cursor-not-allowed text-white text-sm font-semibold px-4 py-2 rounded-lg transition-colors"
              >
                <ShieldOff size={15} />
                {resetting2FA ? 'Resetando...' : 'Confirmar Reset 2FA'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
