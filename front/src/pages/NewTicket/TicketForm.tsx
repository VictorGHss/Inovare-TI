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
import FileAttachment from './FileAttachment';
import RequestItemFields from './RequestItemFields';
import PriorityCategoryFields from './PriorityCategoryFields';

interface Props {
  ticketType: TicketType;
  onTypeChange: (v: TicketType) => void;
}

const inputCls =
  'w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

export default function TicketForm({ ticketType, onTypeChange }: Props) {
  const navigate = useNavigate();
  const [showKb, setShowKb] = useState(false);
  const [categories, setCategories] = useState<TicketCategory[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [quantity, setQuantity] = useState(1);
  const [files, setFiles] = useState<File[]>([]);

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
    onTypeChange(v);
    setForm((f) => ({ ...f, ticketType: v, title: '', itemId: undefined }));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.categoryId) return;

    // Para SOLICITAÇÃO, o título é gerado automaticamente
    let finalTitle = form.title.trim();
    if (ticketType === 'REQUEST') {
      const selectedItem = items.find((i) => i.id === form.itemId);
      const itemName = selectedItem?.name ?? 'Item não especificado';
      finalTitle = `Solicitação: ${itemName}, ${quantity} unidade${quantity !== 1 ? 's' : ''}`;
    }
    if (!finalTitle) return;

    setSubmitting(true);
    try {
      await createTicket({
        ...form,
        title: finalTitle,
        quantity: ticketType === 'REQUEST' ? quantity : undefined,
      });
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
      <TicketTypeToggle value={ticketType} onChange={handleTypeChange} />

      {ticketType === 'INCIDENT' && (
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-slate-700">Título <span className="text-red-500">*</span></label>
          <input className={inputCls} placeholder="Descreva brevemente" value={form.title}
            onChange={(e) => set('title', e.target.value)} required />
        </div>
      )}

      {showKb && <KbSuggestions />}

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Descrição</label>
        <textarea className={`${inputCls} resize-none`} rows={4}
          placeholder={ticketType === 'REQUEST' ? 'Justifique a necessidade do item...' : 'Detalhe o problema, passos para reproduzir...'}
          value={form.description} onChange={(e) => set('description', e.target.value)} />
      </div>

      <FileAttachment files={files} setFiles={setFiles} />

      <PriorityCategoryFields
        priority={form.priority} categoryId={form.categoryId} categories={categories}
        inputCls={inputCls}
        onPriorityChange={(v) => set('priority', v)}
        onCategoryChange={(id) => set('categoryId', id)}
      />

      {ticketType === 'REQUEST' && (
        <RequestItemFields
          items={items} itemId={form.itemId} quantity={quantity} inputCls={inputCls}
          onItemChange={(id) => set('itemId', id)}
          onQuantityChange={setQuantity}
        />
      )}

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
