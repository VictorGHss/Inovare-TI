// Formulário de criação de chamado com lógica dinâmica por tipo
import { useState, useEffect, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import {
  getTicketCategories, getItems, createTicket,
  type TicketCategory, type Item, type CreateTicketDto,
} from '../../services/api';
import TicketTypeToggle, { type TicketType } from './TicketTypeToggle';
import KbSuggestions from './KbSuggestions';

const inputCls =
  'w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

const priorities = [
  { value: 'LOW', label: 'Baixa' },
  { value: 'NORMAL', label: 'Normal' },
  { value: 'HIGH', label: 'Alta' },
  { value: 'URGENT', label: 'Urgente' },
];

export default function TicketForm() {
  const navigate = useNavigate();
  const [ticketType, setTicketType] = useState<TicketType>('INCIDENT');
  const [showKb, setShowKb] = useState(false);
  const [categories, setCategories] = useState<TicketCategory[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [quantity, setQuantity] = useState(1);

  const [form, setForm] = useState<CreateTicketDto>({
    title: '', description: '', priority: 'NORMAL',
    categoryId: 0, ticketType: 'INCIDENT',
  });

  useEffect(() => {
    Promise.all([getTicketCategories(), getItems()])
      .then(([cats, itens]) => {
        setCategories(cats);
        setItems(itens);
        if (cats.length > 0) setForm((f) => ({ ...f, categoryId: cats[0].id }));
      })
      .catch(() => toast.error('Erro ao carregar opções do formulário.'));
  }, []);

  // Exibe sugestões da KB quando o usuário digita mais de 5 caracteres (apenas para INCIDENT)
  useEffect(() => {
    const hasContent = (form.title + form.description).trim().length > 5;
    setShowKb(ticketType === 'INCIDENT' && hasContent);
  }, [form.title, form.description, ticketType]);

  function set<K extends keyof CreateTicketDto>(key: K, value: CreateTicketDto[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function handleTypeChange(v: TicketType) {
    setTicketType(v);
    setForm((f) => ({ ...f, ticketType: v, itemId: undefined }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.title.trim() || !form.categoryId) return;
    setSubmitting(true);
    try {
      await createTicket({ ...form, quantity: ticketType === 'REQUEST' ? quantity : undefined });
      toast.success('Chamado criado com sucesso!');
      navigate('/dashboard');
    } catch {
      toast.error('Erro ao criar chamado. Tente novamente.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="bg-white rounded-xl border border-slate-200 shadow-sm p-6 flex flex-col gap-5">
      {/* Seletor de tipo de chamado */}
      <TicketTypeToggle value={ticketType} onChange={handleTypeChange} />

      {/* Título */}
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Título <span className="text-red-500">*</span></label>
        <input className={inputCls} placeholder="Descreva brevemente" value={form.title}
          onChange={(e) => set('title', e.target.value)} required />
      </div>

      {/* Sugestões da base de conhecimento (apenas para INCIDENT) */}
      {showKb && <KbSuggestions />}

      {/* Descrição */}
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Descrição</label>
        <textarea className={`${inputCls} resize-none`} rows={4}
          placeholder="Detalhe o problema, passos para reproduzir..."
          value={form.description} onChange={(e) => set('description', e.target.value)} />
      </div>

      {/* Prioridade e Categoria */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-slate-700">Prioridade</label>
          <select className={inputCls} value={form.priority}
            onChange={(e) => set('priority', e.target.value as CreateTicketDto['priority'])}>
            {priorities.map((p) => <option key={p.value} value={p.value}>{p.label}</option>)}
          </select>
        </div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-slate-700">Categoria <span className="text-red-500">*</span></label>
          <select className={inputCls} value={form.categoryId}
            onChange={(e) => set('categoryId', Number(e.target.value))} required>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
      </div>

      {/* Campos exclusivos para SOLICITAÇÃO */}
      {ticketType === 'REQUEST' && (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">Item Solicitado</label>
            <select className={inputCls} value={form.itemId ?? ''}
              onChange={(e) => set('itemId', e.target.value ? Number(e.target.value) : undefined)}>
              <option value="">Selecione um item</option>
              {items.map((i) => <option key={i.id} value={i.id}>{i.name}</option>)}
            </select>
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-slate-700">Quantidade</label>
            <input type="number" min={1} className={inputCls} value={quantity}
              onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))} />
          </div>
        </div>
      )}

      {/* Ações */}
      <div className="flex items-center justify-end gap-3 pt-2">
        <button type="button" onClick={() => navigate('/dashboard')}
          className="text-sm text-slate-500 hover:text-slate-700 px-4 py-2.5 rounded-xl transition-colors">
          Cancelar
        </button>
        <button type="submit" disabled={submitting}
          className="bg-primary hover:bg-primary-hover disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors">
          {submitting ? 'Enviando...' : 'Abrir Chamado'}
        </button>
      </div>
    </form>
  );
}
