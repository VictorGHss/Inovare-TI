import { useEffect, useState } from 'react';
import { Loader2, Plus, Trash2, Tag, Archive } from 'lucide-react';
import { toast } from 'react-toastify';

import {
  getItemCategories,
  createItemCategory,
  deleteItemCategory,
  getAssetCategories,
  createAssetCategory,
  deleteAssetCategory,
} from '../../services/inventoryService';
import type { ItemCategory, AssetCategory } from '../../types/models';

export default function CategoriesSection() {
  const [activeTab, setActiveTab] = useState<'items' | 'assets'>('items');
  const [itemCategories, setItemCategories] = useState<ItemCategory[]>([]);
  const [assetCategories, setAssetCategories] = useState<AssetCategory[]>([]);
  const [loading, setLoading] = useState(true);
  
  // Form states
  const [newName, setNewName] = useState('');
  const [isConsumable, setIsConsumable] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const loadData = async () => {
    try {
      setLoading(true);
      const [items, assets] = await Promise.all([
        getItemCategories(),
        getAssetCategories(),
      ]);
      setItemCategories(Array.isArray(items) ? items : []);
      setAssetCategories(Array.isArray(assets) ? assets : []);
    } catch {
      toast.error('Falha ao carregar as categorias do sistema.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;

    setSubmitting(true);
    try {
      if (activeTab === 'items') {
        const created = await createItemCategory({
          name: newName.trim(),
          isConsumable,
        });
        setItemCategories((prev) => [...prev, created]);
        toast.success('Categoria de item criada com sucesso.');
      } else {
        const created = await createAssetCategory({
          name: newName.trim(),
        });
        setAssetCategories((prev) => [...prev, created]);
        toast.success('Categoria de ativo criada com sucesso.');
      }
      setNewName('');
    } catch (err) {
      const error = err as { response?: { data?: { detail?: string } } };
      const msg = error.response?.data?.detail || 'Falha ao criar categoria.';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Confirma a exclusão desta categoria?')) return;

    try {
      if (activeTab === 'items') {
        await deleteItemCategory(id);
        setItemCategories((prev) => prev.filter((c) => c.id !== id));
        toast.success('Categoria de item excluída.');
      } else {
        await deleteAssetCategory(id);
        setAssetCategories((prev) => prev.filter((c) => c.id !== id));
        toast.success('Categoria de ativo excluída.');
      }
    } catch (err) {
      const error = err as { response?: { data?: { detail?: string } } };
      const msg = error.response?.data?.detail || 'Erro ao excluir categoria. Certifique-se de que não há itens vinculados a ela.';
      toast.error(msg);
    }
  }

  return (
    <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
      <header className="px-6 py-5 border-b border-slate-100 bg-slate-50/50">
        <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
          Gerenciamento de Categorias
        </h2>
        <p className="text-xs text-slate-500 mt-0.5">
          Cadastre e remova categorias estruturais para itens de inventário e equipamentos do sistema.
        </p>
      </header>

      {/* Tabs */}
      <div className="flex border-b border-slate-100 bg-slate-50/30 px-6 py-2 gap-2">
        <button
          onClick={() => {
            setActiveTab('items');
            setNewName('');
          }}
          className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-lg transition-all ${
            activeTab === 'items'
              ? 'bg-brand-primary/10 text-brand-primary'
              : 'text-slate-600 hover:bg-slate-100'
          }`}
        >
          <Tag size={16} />
          Categorias de Itens (Inventário)
        </button>
        <button
          onClick={() => {
            setActiveTab('assets');
            setNewName('');
          }}
          className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-lg transition-all ${
            activeTab === 'assets'
              ? 'bg-brand-primary/10 text-brand-primary'
              : 'text-slate-600 hover:bg-slate-100'
          }`}
        >
          <Archive size={16} />
          Categorias de Ativos (Equipamentos)
        </button>
      </div>

      <div className="p-6 grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Form para Adição */}
        <div className="lg:col-span-1 border-r border-slate-100 pr-0 lg:pr-8">
          <h3 className="text-sm font-bold text-slate-800 mb-4">Adicionar Nova Categoria</h3>
          <form onSubmit={(e) => void handleCreate(e)} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-500 mb-1">Nome da Categoria</label>
              <input
                type="text"
                placeholder="Ex: Consultório, Reagentes, TI..."
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-700 transition-all focus:border-brand-primary/40 focus:outline-none focus:ring-2 focus:ring-brand-primary/25"
                required
              />
            </div>

            {activeTab === 'items' && (
              <div className="flex items-center gap-2 mt-2">
                <input
                  type="checkbox"
                  id="isConsumable"
                  checked={isConsumable}
                  onChange={(e) => setIsConsumable(e.target.checked)}
                  className="rounded border-slate-300 text-brand-primary focus:ring-brand-primary"
                />
                <label htmlFor="isConsumable" className="text-xs font-medium text-slate-600 cursor-pointer">
                  Esta categoria contém itens consumíveis
                </label>
              </div>
            )}

            <button
              type="submit"
              disabled={submitting || !newName.trim()}
              className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-brand-primary px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors hover:bg-brand-primary-dark disabled:opacity-60"
            >
              {submitting ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
              {submitting ? 'Salvando...' : 'Adicionar Categoria'}
            </button>
          </form>
        </div>

        {/* Listagem */}
        <div className="lg:col-span-2">
          <h3 className="text-sm font-bold text-slate-800 mb-4">Categorias Cadastradas</h3>
          {loading ? (
            <div className="flex items-center gap-2 justify-center py-10 text-slate-400">
              <Loader2 size={18} className="animate-spin text-brand-primary" />
              Carregando categorias...
            </div>
          ) : activeTab === 'items' ? (
            itemCategories.length === 0 ? (
              <p className="text-xs text-slate-400 py-6 text-center">Nenhuma categoria de item cadastrada.</p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3 max-h-[380px] overflow-y-auto pr-2">
                {itemCategories.map((cat) => (
                  <div
                    key={cat.id}
                    className="flex items-center justify-between p-3 rounded-xl border border-slate-100 bg-slate-50/50 hover:bg-slate-50 transition-colors"
                  >
                    <div>
                      <p className="text-sm font-bold text-slate-700">{cat.name}</p>
                      <p className="text-[10px] uppercase font-bold tracking-wider text-slate-400 mt-0.5">
                        {cat.isConsumable ? 'Consumível' : 'Patrimônio'}
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => void handleDelete(cat.id)}
                      className="p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-all"
                      title="Excluir Categoria"
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>
            )
          ) : assetCategories.length === 0 ? (
            <p className="text-xs text-slate-400 py-6 text-center">Nenhuma categoria de ativo cadastrada.</p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3 max-h-[380px] overflow-y-auto pr-2">
              {assetCategories.map((cat) => (
                <div
                  key={cat.id}
                  className="flex items-center justify-between p-3 rounded-xl border border-slate-100 bg-slate-50/50 hover:bg-slate-50 transition-colors"
                >
                  <p className="text-sm font-bold text-slate-700">{cat.name}</p>
                  <button
                    type="button"
                    onClick={() => void handleDelete(cat.id)}
                    className="p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-all"
                    title="Excluir Categoria"
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
