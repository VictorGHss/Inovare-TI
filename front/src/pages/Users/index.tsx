// Página de listagem e cadastro de usuários
import { useEffect, useState } from 'react';
import { PlusCircle, X, Upload } from 'lucide-react';
import { toast } from 'react-toastify';
import {
  getUsers,
  createUser,
  getSectors,
  type User,
  type Sector,
  type CreateUserDto,
} from '../../services/api';
import BulkImportModal from './BulkImportModal';

export default function Users() {
  const [users, setUsers] = useState<User[]>([]);
  const [sectors, setSectors] = useState<Sector[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [formData, setFormData] = useState<CreateUserDto>({
    name: '',
    email: '',
    password: '',
    role: 'USER',
    sectorId: '',
  });

  useEffect(() => {
    Promise.all([loadUsers(), loadSectors()]);
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
      loadUsers();
    } catch {
      toast.error('Erro ao cadastrar usuário. Verifique os dados.');
    } finally {
      setSubmitting(false);
    }
  }

  function resetForm() {
    setFormData({ name: '', email: '', password: '', role: 'USER', sectorId: '' });
  }

  const roleLabels = {
    ADMIN: 'Administrador',
    TECHNICIAN: 'Técnico',
    USER: 'Usuário',
  };

  return (
    <main className="w-full max-w-full px-4 sm:px-6 lg:px-8 py-8">
      {/* Cabeçalho */}
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-slate-800">Equipe</h1>
        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowImportModal(true)}
            className="flex items-center gap-2 bg-emerald-500 hover:bg-emerald-600 text-white text-sm font-semibold px-4 py-2.5 rounded-xl transition-colors"
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

      {/* Tabela de usuários */}
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
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map((user) => (
                  <tr key={user.id} className="hover:bg-slate-50 transition-colors">
                    <td className="px-4 py-3 font-medium text-slate-800">{user.name}</td>
                    <td className="px-4 py-3 text-slate-600">{user.email}</td>
                    <td className="px-4 py-3 text-slate-600">{user.sectorName}</td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex px-2.5 py-0.5 rounded-full text-xs font-medium ${
                          user.role === 'ADMIN'
                            ? 'bg-red-100 text-red-700'
                            : user.role === 'TECHNICIAN'
                            ? 'bg-brand-secondary text-brand-primary'
                            : 'bg-slate-100 text-slate-700'
                        }`}
                      >
                        {roleLabels[user.role]}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal de cadastro */}
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
                <label className="block text-xs font-medium text-slate-600 mb-1.5">
                  Nome Completo
                </label>
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
                <label className="block text-xs font-medium text-slate-600 mb-1.5">
                  Nível de Acesso
                </label>
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

      {/* Modal de importação CSV */}
      <BulkImportModal
        isOpen={showImportModal}
        onClose={() => setShowImportModal(false)}
        onSuccess={loadUsers}
      />
    </main>
  );
}
