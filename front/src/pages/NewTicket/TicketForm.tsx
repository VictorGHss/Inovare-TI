// Formulário de criação de chamado com lógica dinâmica por tipo
import { useState, useEffect, useRef, type FormEvent, type ClipboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import {
  getTicketCategories, getItems, createTicket, uploadTicketAttachment, searchArticles,
  type TicketCategory, type Item, type CreateTicketDto, type ArticleSearchResult,
} from '../../services/api';
import TicketTypeToggle, { type TicketType } from './TicketTypeToggle';
import KbSuggestions from './KbSuggestions';
import FileAttachment from './FileAttachment';
import RequestItemFields from './RequestItemFields';
import PriorityCategoryFields from './PriorityCategoryFields';

interface Props {
  type: TicketType;
  onTypeChange: (v: TicketType) => void;
}

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

const inputCls =
  'w-full rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary transition';

export default function TicketForm({ type, onTypeChange }: Props) {
  const navigate = useNavigate();
  const [categories, setCategories] = useState<TicketCategory[]>([]);
  const [items, setItems] = useState<Item[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [files, setFiles] = useState<File[]>([]);
  const [suggestedArticles, setSuggestedArticles] = useState<ArticleSearchResult[]>([]);
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Nome do item selecionado — usado para gerar o título automático de solicitações
  const [selectedItemName, setSelectedItemName] = useState('');

  const [form, setForm] = useState<CreateTicketDto>({
    title: '',
    description: '',
    anydeskCode: '',
    priority: 'NORMAL',
    // categoryId é UUID (string) — inicializado vazio enquanto as categorias carregam
    categoryId: '',
    requestedItemId: undefined,
    requestedQuantity: 1,
  });

  useEffect(() => {
    Promise.all([getTicketCategories(), getItems()])
      .then(([cats, itens]) => {
        setCategories(cats);
        setItems(itens);
        // Pré-seleciona a primeira categoria; id é UUID (string)
        if (cats.length > 0) setForm((f) => ({ ...f, categoryId: cats[0].id }));
      })
      .catch(() => toast.error('Erro ao carregar opções do formulário.'));
  }, []);

  // Debounce para busca de artigos quando o título muda (Ticket Deflection)
  useEffect(() => {
    if (type !== 'INCIDENT') {
      setSuggestedArticles([]);
      return;
    }

    // Limpa o timer anterior
    if (debounceTimer.current) {
      clearTimeout(debounceTimer.current);
    }

    const query = form.title.trim();

    // Se o título tem menos de 3 caracteres, não busca
    if (query.length < 3) {
      setSuggestedArticles([]);
      return;
    }

    // Cria novo timer para busca com debounce de 500ms
    debounceTimer.current = setTimeout(async () => {
      try {
        const results = await searchArticles(query);
        setSuggestedArticles(results);
      } catch (error) {
        console.error('Erro ao buscar artigos:', error);
        setSuggestedArticles([]);
      }
    }, 500);

    // Cleanup: limpa o timer ao desmontar ou quando o efeito é chamado novamente
    return () => {
      if (debounceTimer.current) {
        clearTimeout(debounceTimer.current);
      }
    };
  }, [form.title, type]);

  function set<K extends keyof CreateTicketDto>(key: K, value: CreateTicketDto[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function handleTypeChange(v: TicketType) {
    onTypeChange(v);
    setFiles([]);
    // Reset type-specific fields when toggling
    setForm((f) => ({
      ...f,
      title: '',
      description: '',
      anydeskCode: '',
      requestedItemId: undefined,
      requestedQuantity: 1,
      priority: 'NORMAL',
    }));
    setSelectedItemName('');
  }

  function handlePaste(e: ClipboardEvent<HTMLTextAreaElement>) {
    const clipboardData = e.clipboardData;
    if (!clipboardData) return;

    const items = clipboardData.items;
    let imageFound = false;

    // Iterate through clipboard items looking for images
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      
      // Check if the item is an image
      if (item.type.indexOf('image') !== -1) {
        imageFound = true;
        const blob = item.getAsFile();
        
        if (blob) {
          // Create a File object with a generated filename
          const timestamp = new Date().getTime();
          const extension = item.type.split('/')[1] || 'png';
          const filename = `clipboard-image-${timestamp}.${extension}`;
          const file = new File([blob], filename, { type: item.type });

          if (file.size > MAX_FILE_SIZE_BYTES) {
            toast.error('O arquivo excede o limite máximo de 5MB.');
            continue;
          }
          
          // Add to existing files without removing previous ones
          setFiles((prevFiles) => [...prevFiles, file]);
          toast.success(`Imagem colada adicionada aos anexos: ${filename}`);
        }
      }
    }

    // Prevent default paste behavior if an image was found
    // This prevents pasting binary data as text in the textarea
    if (imageFound) {
      e.preventDefault();
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    const defaultCategoryId = categories[0]?.id;
    const effectiveCategoryId = form.categoryId || defaultCategoryId;
    if (!effectiveCategoryId) {
      toast.error('Nenhuma categoria disponível para abrir o chamado.');
      return;
    }

    let finalTitle = form.title.trim();
    if (type === 'REQUEST') {
      const itemName = selectedItemName || 'Item não especificado';
      const qty = form.requestedQuantity ?? 1;
      finalTitle = `Solicitação: ${itemName}, ${qty} unidade${qty !== 1 ? 's' : ''}`;
    }
    if (!finalTitle) return;

    const payload: CreateTicketDto = {
      title: finalTitle,
      description: form.description,
      anydeskCode: type === 'INCIDENT' ? (form.anydeskCode || undefined) : undefined,
      priority: type === 'INCIDENT' ? form.priority : 'NORMAL',
      categoryId: effectiveCategoryId,
      requestedItemId: type === 'REQUEST' ? form.requestedItemId : undefined,
      requestedQuantity: type === 'REQUEST' ? (form.requestedQuantity ?? 1) : undefined,
    };

    setSubmitting(true);
    try {
      // Step 1: Create the ticket
      const ticket = await createTicket(payload);
      
      // Step 2: Upload attachments if any
      if (type === 'INCIDENT' && files.length > 0) {
        if (files.some((file) => file.size > MAX_FILE_SIZE_BYTES)) {
          toast.error('O arquivo excede o limite máximo de 5MB.');
          return;
        }

        toast.info(`Enviando ${files.length} anexo${files.length > 1 ? 's' : ''}...`);
        await Promise.all(files.map(file => uploadTicketAttachment(ticket.id, file)));
      }
      
      // Step 3: Success - only navigate after everything is done
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
      <TicketTypeToggle value={type} onChange={handleTypeChange} />

      {type === 'REQUEST' && (
        <RequestItemFields
          items={items}
          requestedItemId={form.requestedItemId}
          requestedQuantity={form.requestedQuantity ?? 1}
          inputCls={inputCls}
          onItemChange={(id, name) => {
            set('requestedItemId', id);
            setSelectedItemName(name ?? '');
          }}
          onQuantityChange={(q) => set('requestedQuantity', q)}
        />
      )}

      {type === 'INCIDENT' && (
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium text-slate-700">Título <span className="text-red-500">*</span></label>
          <input className={inputCls} placeholder="Descreva brevemente" value={form.title}
            onChange={(e) => set('title', e.target.value)} required />
        </div>
      )}

      {suggestedArticles.length > 0 && <KbSuggestions articles={suggestedArticles} />}

      {type === 'INCIDENT' && (
      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Código AnyDesk (Acesso Remoto) - Opcional</label>
        <input className={inputCls} placeholder="Ex: 123 456 789" value={form.anydeskCode || ''}
          onChange={(e) => set('anydeskCode', e.target.value)} />
        <p className="text-xs text-slate-400">Use este código para permitir acesso remoto ao seu computador durante o suporte.</p>
      </div>
      )}

      <div className="flex flex-col gap-1.5">
        <label className="text-sm font-medium text-slate-700">Descrição</label>
        <textarea className={`${inputCls} resize-none`} rows={4}
          placeholder={type === 'REQUEST' ? 'Justifique a necessidade do item...' : 'Detalhe o problema, passos para reproduzir...'}
          value={form.description} 
          onChange={(e) => set('description', e.target.value)}
          onPaste={handlePaste} />
      </div>

      {type === 'INCIDENT' && <FileAttachment files={files} setFiles={setFiles} />}

      {type === 'INCIDENT' && (
      <PriorityCategoryFields
        priority={form.priority} categoryId={form.categoryId} categories={categories}
        inputCls={inputCls}
        onPriorityChange={(v) => set('priority', v)}
        onCategoryChange={(id) => set('categoryId', id)}
      />
      )}

      <div className="flex items-center justify-end gap-3 pt-2">
        <button type="button" onClick={() => navigate('/dashboard')}
          className="text-sm text-slate-500 hover:text-slate-700 px-4 py-2.5 rounded-xl transition-colors">
          Cancelar
        </button>
        <button type="submit" disabled={submitting}
          className="bg-primary hover:bg-primary-hover disabled:opacity-60 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors">
          {submitting ? (type === 'INCIDENT' && files.length > 0 ? 'Enviando arquivos...' : 'Enviando...') : 'Abrir Chamado'}
        </button>
      </div>
    </form>
  );
}
