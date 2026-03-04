// Página de criação de novo item de inventário
import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, X } from 'lucide-react';
import { toast } from 'react-toastify';
import { getItemCategories, createItem, addBatch, type ItemCategory } from '../../services/api';

interface SpecEntry {
  key: string;
  value: string;
}

const inputCls =
  'w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

export default function NewItem() {
  const navigate = useNavigate();
  const [categories, setCategories] = useState<ItemCategory[]>([]);
  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [specs, setSpecs] = useState<SpecEntry[]>([{ key: '', value: '' }]);
  const [submitting, setSubmitting] = useState(false);
  
  // Estados para registro de primeira entrada
  const [registerFirstBatch, setRegisterFirstBatch] = useState(false);
  const [batchQuantity, setBatchQuantity] = useState(1);
  const [batchUnitPrice, setBatchUnitPrice] = useState('');
  const [batchBrand, setBatchBrand] = useState('');
  const [batchSupplier, setBatchSupplier] = useState('');
  const [batchPurchaseReason, setBatchPurchaseReason] = useState('');

  useEffect(() => {
    // Carrega as categorias de item disponíveis
    getItemCategories()
      .then((cats) => {
        setCategories(cats);
        if (cats.length > 0) setCategoryId(cats[0].id);
      })
      .catch(() => toast.error('Erro ao carregar categorias de item.'));
  }, []);
Valida campos de lote se o checkbox estiver marcado
    if (registerFirstBatch) {
      if (batchQuantity < 1 || !batchUnitPrice || parseFloat(batchUnitPrice) <= 0) {
        toast.error('Preencha quantidade e preço unitário do lote corretamente.');
        return;
      }
    }

    // Converte o array de specs em um objeto Record<string, string>
    // Filtra entradas vazias e cria o objeto final
    const parsedSpecs: Record<string, string> = {};
    specs.forEach((spec) => {
      if (spec.key.trim() && spec.value.trim()) {
        parsedSpecs[spec.key.trim()] = spec.value.trim();
      }
    });

    const finalSpecs = Object.keys(parsedSpecs).length > 0 ? parsedSpecs : undefined;

    setSubmitting(true);
    try {
      // 1. Cria o item
      const newItem = await createItem({
        name: name.trim(),
        itemCategoryId: categoryId,
        specifications: finalSpecs,
      });
      
      // 2. Se checkbox marcado, registra o primeiro lote
      if (registerFirstBatch) {
        await addBatch(newItem.id, {
          quantity: batchQuantity,
          unitPrice: parseFloat(batchUnitPrice),
          brand: batchBrand.trim() || undefined,
          supplier: batchSupplier.trim() || undefined,
          purchaseReason: batchPurchaseReason.trim() || undefined,
        });
        toast.success('Item e primeiro lote criados com sucesso!');
      } else {
        toast.success('Item criado com sucesso!');
      }
      
      navigate('/inventory');
    } catch (error) {
      console.error('Erro ao criar item:', error);
      await createItem({
        name: name.trim(),
        itemCategoryId: categoryId,
        specifications: finalSpecs,
      });
      toast.success('Item criado com sucesso!');
      navigate('/inventory');
    } catch {
      toast.error('Erro ao criar item. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  }

  function addSpecRow() {
    setSpecs([...specs, { key: '', value: '' }]);
  }

  function removeSpecRow(index: number) {
    if (specs.length > 1) {
      setSpecs(specs.filter((_, i) => i !== index));
    }
  }

  function updateSpecKey(index: number, key: string) {
    const updated = [...specs];
    updated[index].key = key;
    setSpecs(updated);
  }

  function updateSpecValue(index: number, value: string) {
    const updated = [...specs];
    updated[index].value = value;
    setSpecs(updated);
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

        {/* Especificações - Construtor de chave-valor */}
        <div className="flex flex-col gap-3">
          <label className="text-sm font-medium text-slate-700">
            Especificações
          </label>
          {specs.map((spec, index) => (
            <div key={index} className="flex items-center gap-2">
              <input
                type="text"
                className={inputCls}
                placeholder="Chave (ex: marca)"
                value={spec.key}
                onChange={(e) => updateSpecKey(index, e.target.value)}
              />
              <input
                type="text"
                className={inputCls}
                placeholder="Valor (ex: Logitech)"
                value={spec.value}
                onChange={(e) => updateSpecValue(index, e.target.value)}
              />
              <button
                type="button"
                onClick={() => removeSpecRow(index)}
                disabled={specs.length === 1}
                className="p-2.5 rounded-lg text-red-500 hover:bg-red-50 disabled:opacity-30 disabled:cursor-not-allowed transition-colors shrink-0"
                aria-label="Remover especificação"
              >
                <X size={16} />
              </button>
            </div>
          ))}
          <button
            type="button"
            onClick={addSpecRow}
            className="flex items-center gap-2 text-sm text-blue-600 hover:text-blue-700 font-medium self-start transition-colors"
          >
            <Plus size={16} />
            Adicionar Especificação
          </button>
        </div>

        {/* Divisor */}
        <div className="border-t border-slate-200 my-2" />

        {/* Toggle para registrar primeira entrada */}
        <div className="flex items-center gap-3">
          <input
            type="checkbox"
            id="registerFirstBatch"
            checked={registerFirstBatch}
            onChange={(e) => setRegisterFirstBatch(e.target.checked)}
            className="w-4 h-4 text-primary bg-white border-slate-300 rounded focus:ring-2 focus:ring-primary cursor-pointer"
          />
          <label
            htmlFor="registerFirstBatch"
            className="text-sm font-medium text-slate-700 cursor-pointer select-none"
          >
            Registrar primeira entrada de estoque agora?
          </label>
        </div>

        {/* Campos de lote (condicionais) */}
        {registerFirstBatch && (
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 flex flex-col gap-4">
            <h3 className="text-sm font-semibold text-blue-900">
              Dados da Primeira Entrada
            </h3>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Quantidade */}
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-slate-700">
                  Quantidade <span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  min={1}
                  className={inputCls}
                  value={batchQuantity}
                  onChange={(e) => setBatchQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                  required={registerFirstBatch}
                />
              </div>

              {/* Preço Unitário */}
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-slate-700">
                  Preço Unitário (R$) <span className="text-red-500">*</span>
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  className={inputCls}
                  value={batchUnitPrice}
                  onChange={(e) => setBatchUnitPrice(e.target.value)}
                  placeholder="0.00"
                  required={registerFirstBatch}
                />
              </div>

              {/* Marca */}
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-slate-700">
                  Marca
                </label>
                <input
                  type="text"
                  className={inputCls}
                  value={batchBrand}
                  onChange={(e) => setBatchBrand(e.target.value)}
                  placeholder="Ex: Logitech, HP, Dell"
                  maxLength={100}
                />
              </div>

              {/* Fornecedor */}
              <div className="flex flex-col gap-1.5">
                <label className="text-sm font-medium text-slate-700">
                  Fornecedor
                </label>
                <input
                  type="text"
                  className={inputCls}
                  value={batchSupplier}
                  onChange={(e) => setBatchSupplier(e.target.value)}
                  placeholder="Ex: Kabum, Amazon, Kalunga"
                  maxLength={150}
                />
              </div>

              {/* Motivo da Compra */}
              <div className="flex flex-col gap-1.5 md:col-span-2">
                <label className="text-sm font-medium text-slate-700">
                  Motivo da Compra
                </label>
                <input
                  type="text"
                  className={inputCls}
                  value={batchPurchaseReason}
                  onChange={(e) => setBatchPurchaseReason(e.target.value)}
                  placeholder="Ex: Reposição mensal, Expansão de TI"
                  maxLength={200}
                />
              </div>
            </div>
          </div>
        )}

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
