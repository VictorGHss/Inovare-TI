// Página de criação de novo item de inventário
import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { toast } from 'react-toastify';
import { getItemCategories, createItem, type ItemCategory } from '../../services/api';

const inputCls =
  'w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

export default function NewItem() {
  const navigate = useNavigate();
  const [categories, setCategories] = useState<ItemCategory[]>([]);
  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [specifications, setSpecifications] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    // Carrega as categorias de item disponíveis
    getItemCategories()
      .then((cats) => {
        setCategories(cats);
        if (cats.length > 0) setCategoryId(cats[0].id);
      })
      .catch(() => toast.error('Erro ao carregar categorias de item.'));
  }, []);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim() || !categoryId) {
      toast.error('Preencha todos os campos obrigatórios.');
      return;
    }

    // Parse das especificações como JSON (se fornecidas)
    let parsedSpecs: Record<string, unknown> | undefined;
    if (specifications.trim()) {
      try {
        parsedSpecs = JSON.parse(specifications);
      } catch {
        toast.error('Especificações inválidas. Use formato JSON válido.');
        return;
      }
    }

    setSubmitting(true);
    try {
      await createItem({
        name: name.trim(),
        itemCategoryId: categoryId,
        specifications: parsedSpecs,
      });
      toast.success('Item criado com sucesso!');
      navigate('/inventory');
    } catch {
      toast.error('Erro ao criar item. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="max-w-3xl mx-auto px-4 sm:px-6 py-8">
      {/* Navegação de retorno */}
      <div className="flex items-center gap-3 mb-6">
        <button
          onClick={() => navigate('/inventory')}
          className="p-1.5 rounded-lg hover:bg-slate-200 text-slate-500 hover:text-slate-700 transition-colors"
          aria-label="Voltar"
        >
          <ArrowLeft size={18} />
        </button>
        <div>
          <h1 className="text-base font-bold text-slate-800">Novo Item</h1>
          <p className="text-xs text-slate-400">
            Cadastre um novo item no inventário
          </p>
        </div>
      </div>

      {/* Formulário */}
      <form
        onSubmit={handleSubmit}
        className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex flex-col gap-5"
      >
        {/* Nome do item */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-slate-700">
            Nome do Item <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            className={inputCls}
            placeholder="Ex: Mouse Logitech MX Master 3"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>

        {/* Categoria */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-slate-700">
            Categoria <span className="text-red-500">*</span>
          </label>
          <select
            className={inputCls}
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            required
          >
            {categories.length === 0 ? (
              <option value="">Carregando...</option>
            ) : (
              categories.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.name}
                </option>
              ))
            )}
          </select>
        </div>

        {/* Especificações (JSON opcional) */}
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-slate-700">
            Especificações (JSON)
          </label>
          <textarea
            className={`${inputCls} resize-none font-mono text-xs`}
            rows={6}
            placeholder='{"marca": "Logitech", "modelo": "MX Master 3", "cor": "Preto"}'
            value={specifications}
            onChange={(e) => setSpecifications(e.target.value)}
          />
          <p className="text-xs text-slate-400">
            Formate as especificações como um objeto JSON válido. Deixe em branco se não houver.
          </p>
        </div>

        {/* Botões de ação */}
        <div className="flex items-center justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => navigate('/inventory')}
            className="text-sm text-slate-500 hover:text-slate-700 px-4 py-2.5 rounded-xl transition-colors"
          >
            Cancelar
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="bg-primary hover:bg-primary-hover disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
          >
            {submitting ? 'Criando...' : 'Criar Item'}
          </button>
        </div>
      </form>
    </main>
  );
}
